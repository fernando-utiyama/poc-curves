## Why

O orquestrador (`services/orchestrator`) já foi transcrito do código real: é um motor genérico de tarefas agendadas (`Tarefa`, cadastro por CRUD, agendamento por cron ou intervalo, execução por uma `action` extensível), ainda sem as tarefas das curvas e com falhas que impedem produção. O agendamento, o status e a trava de execução vivem na memória de cada instância, então com as 2 instâncias do Azure a mesma tarefa dispara duas vezes e um disparo com as duas fora (deploy) se perde. Além disso, o disparo automático só existe num fluxo paralelo desligado por padrão (`TarefaService`), nenhuma rota exige papel, a action `http` aceita chamar qualquer endereço com qualquer cabeçalho (SSRF) e o webhook repassa o token de quem chamou (vazamento de credencial).

## What Changes

- **Motor de tarefas (capability `agendamento-tarefas`), sem tabela nem coluna nova:**
  - **Horário do Brasil em tudo, global da JVM:** fuso da JVM fixado em `America/Sao_Paulo` no `main` (subida recusada com outro), como nos outros serviços; o código passa a usar só o padrão da JVM, sem as constantes de fuso e o Jackson em UTC que o código real tem hoje;
  - **Todas as instâncias agendam, uma executa:** cada disparo tem uma ocorrência (o instante programado, igual em todas as instâncias; intervalos alinhados à meia-noite de Brasília), reivindicada numa transação curta com trava de linha em `tTrefaAgnda` e log de início em `tLogTrefa`; a segunda instância, mesmo atrasada alguns segundos, encontra a ocorrência já reivindicada e desiste;
  - **Reconciliação** a cada 30 s: aplica mudanças de cadastro feitas na outra instância, dispara uma vez o que se perdeu com tudo fora (janela de 60 min) e libera tarefa presa de instância que caiu;
  - **Um só fluxo:** apaga `TarefaService`, `scheduler.enabled` e os mapas em memória; disparo automático ligado por padrão; status sempre lido do banco; mudança de situação condicionada ao estado esperado;
  - **Segurança:** `Curvas.Leitura` nas consultas e `Curvas.Operador` nas mutações; action `http` só chama destino cadastrado; webhook assinado com segredo próprio;
  - **Alertas consultáveis:** `GET /api/v1/alertas`, para o dashboard do front;
  - **Correção** do mapeamento trocado de `action`/`descricao` no `TarefaJpaMapper` (não há cadastro em nenhum ambiente).
- **Tarefas das curvas (capability `orquestracao-curvas`):** tarefas cadastradas no motor, com duas `action` novas (`TaskActionPort`):
  - `download-carga-dia`: uma `action` para todas as fontes, cadastrada uma vez por fonte (B3 `TaxaSwap`; ANBIMA `ms` e Bloomberg SOFR, quando o conector tiver as rotas): a cada 10 minutos (intervalo alinhado) dentro da janela da fonte, até receber o arquivo do dia; no limite, faz a última tentativa e, sem o arquivo, grava `CARGA_NAO_RECEBIDA` com a fonte;
  - `construcao-curvas-data`: rede de segurança do webhook do processor (que continua sendo o caminho principal, inclusive para as curvas filhas em cadeia), com a mesma prioridade de construir o quanto antes: a cada 10 minutos dentro da janela, constrói o que ficou para trás (engine fora além da janela do processor, filha de componente construída à mão); sem pendência, para no dia; no limite com pendência, grava `CURVAS_PENDENTES`.
- **Alertas no dashboard do front:** `CARGA_NAO_RECEBIDA` e `CURVAS_PENDENTES` ficam no log da própria tarefa e saem pela rota de alertas; sem e-mail nem Teams.
- **Calendários iguais aos do engine, por tarefa:** o orquestrador passa a ter os mesmos calendários nativos do engine (`Brazil`/`Settlement`, `UnitedStates`/`FederalReserve`), conferidos contra a planilha oficial de feriados nacionais da ANBIMA, e sincroniza uma vez por dia os feriados decretados do engine; cada tarefa diz em quais calendários precisa ser dia útil (a Bloomberg, nos dois).
- **Execução manual:** a rota real `/api/v1/agendador/tarefas/{id}/executar`, com `dataBase` opcional (reprocessamento da fonte para data passada); 409 se a tarefa já estiver executando.
- **Identidade de serviço:** token do Entra ID por client credentials, com `Curvas.Orquestrador` e `Curvas.Leitura` no engine.
- **Alternativa guardada:** a versão com o Quartz em cluster (11 tabelas `QRTZ_*`) fica em `alternativa-quartz/`, para adoção futura.

## Capabilities

### New Capabilities
- `agendamento-tarefas`: CRUD e ciclo de vida de `Tarefa`, agendamento automático em todas as instâncias com execução única por ocorrência, recuperação de disparo perdido e de tarefa presa, extensibilidade por `action`, segurança, alertas consultáveis.
- `orquestracao-curvas`: já existia nesta change (não arquivada); reescrita como duas `action` do motor `agendamento-tarefas`.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas na develop. -->

## Impact

- **services/orchestrator:** `SpringSchedulerAdapter` agendando por ocorrência; reivindicação e transições atômicas no `TaskJpaPersistenceAdapter`; `ReconciliacaoAgendamentos`; remoção de `TarefaService`, `TarefaUseCase` e dos mapas em memória; `spring-boot-starter-oauth2-resource-server` e papéis nas rotas; `destino`/`caminho` na action `http`; assinatura do webhook; rota de alertas; correção do `TarefaJpaMapper`; `DownloadCargaTaskActionAdapter` e `ConstrucaoDataTaskActionAdapter`.
- **Banco:** nenhum script; usa só `tTrefaAgnda`, `tParmTrefa` e `tLogTrefa`, que já existem.
- **Entra ID:** identidade de serviço do orquestrador com `Curvas.Orquestrador` e `Curvas.Leitura` no engine; `Curvas.Operador`/`Curvas.Leitura` para quem chama a API do orquestrador.
- **Front/BFF:** o dashboard passa a consumir `GET /api/v1/alertas` (change do front, futura).
- **Dependências:** `b3/taxa-swap/download` e `b3/taxa-swap/reprocessamento` do conector (change `conector-b3-webhook-ingest`); rotas do arquivo ANBIMA `ms` e dos nós da SOFR (Bloomberg) no conector, no mesmo contrato (changes próprias, futuras); `POST /api/v1/construcoes/{dataBase}` e a exportação de calendário do engine (change `engine-modelos-curva`).
- **Fora de escopo:** as rotas ANBIMA e Bloomberg no conector (changes próprias), os cadastros em si (feitos pela tela; sugestão em `cadastros-sugeridos.txt`), a tela do dashboard, lógica de curva, alteração de schema.
