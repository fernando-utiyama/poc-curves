## Why

O orquestrador (`services/orchestrator`) já foi transcrito do código real: é um motor genérico de tarefas agendadas (`Tarefa`, cadastro por CRUD, agendamento por cron ou intervalo, execução por uma `action` extensível), ainda sem as tarefas das curvas e com falhas que impedem produção. O agendamento, o status e a trava de execução vivem na memória de cada instância, então com as 2 instâncias do Azure a mesma tarefa dispara duas vezes e um disparo com as duas fora (deploy) se perde. Além disso, o disparo automático só existe num fluxo paralelo desligado por padrão (`TarefaService`), a action `http` aceita chamar qualquer endereço com qualquer cabeçalho (SSRF) e o webhook de notificação, que ninguém usa, repassa o `Authorization` de quem chamou.

## What Changes

- **Motor de tarefas (capability `agendamento-tarefas`), sem tabela nem coluna nova:**
  - **Horário do Brasil em tudo, global da JVM:** fuso da JVM fixado em `America/Sao_Paulo` no `main` (subida recusada com outro), como nos outros serviços; o código passa a usar só o padrão da JVM, sem as constantes de fuso e o Jackson em UTC que o código real tem hoje;
  - **Todas as instâncias agendam, uma executa:** cada disparo tem uma ocorrência (o instante programado, igual em todas as instâncias; intervalos alinhados à meia-noite de Brasília), reivindicada numa transação curta com trava de linha em `tTrefaAgnda` e log de início em `tLogTrefa`; a segunda instância, mesmo atrasada alguns segundos, encontra a ocorrência já reivindicada e desiste;
  - **Reconciliação** a cada 30 s: aplica mudanças de cadastro feitas na outra instância, dispara uma vez o que se perdeu com tudo fora (janela de 60 min) e libera tarefa presa de instância que caiu;
  - **Um só fluxo:** apaga `TarefaService`, `scheduler.enabled` e os mapas em memória; disparo automático ligado por padrão; status sempre lido do banco; mudança de situação condicionada ao estado esperado;
  - **Segurança:** action `http` só chama destino cadastrado; o webhook de notificação (desligado por padrão e sem destinatário) é apagado;
  - **Alertas consultáveis:** `GET /api/v1/alertas`, para o dashboard do front;
  - **Correção** do mapeamento trocado de `action`/`descricao` no `TarefaJpaMapper` (não há cadastro em nenhum ambiente).
- **Tarefas das curvas (capability `orquestracao-curvas`):** três tarefas cadastradas no motor, todas com uma só `action` nova (`TaskActionPort`), `download-carga-dia`, cada uma disparando a function da sua fonte com a data-base:
  - **B3** (`TaxaSwap`) e **ANBIMA** (`ms`): a data-base; a Bloomberg (SOFR) recebe também os **tickers** (parâmetro da tarefa, editável sem redeploy);
  - a cada 10 minutos (intervalo alinhado) dentro da janela da fonte, até receber o arquivo do dia; no limite, faz a última tentativa e, sem o arquivo, grava `CARGA_NAO_RECEBIDA` com a fonte;
  - **o orquestrador não chama o engine:** depois do download, o fluxo da B3 segue sozinho (function grava no Blob e avisa, processor grava o bruto e chama o webhook do engine, que constrói as curvas da carga e as derivadas em cadeia).
- **Alerta no dashboard do front:** `CARGA_NAO_RECEBIDA` fica no log da própria tarefa e sai pela rota de alertas; sem e-mail nem Teams.
- **Calendários iguais aos do engine, por tarefa:** o orquestrador passa a ter os mesmos calendários nativos do engine (`Brazil`/`Settlement`, `UnitedStates`/`FederalReserve`), conferidos contra a planilha oficial de feriados nacionais da ANBIMA, e sincroniza uma vez por dia os feriados decretados do engine (única chamada dele ao engine); cada tarefa diz em quais calendários precisa ser dia útil (a Bloomberg, nos dois).
- **Execução manual:** a rota real `/api/v1/agendador/tarefas/{id}/executar`, com `dataBase` opcional (reprocessamento da fonte para data passada, pela function); 409 se a tarefa já estiver executando.
- **Alternativa guardada:** a versão com o Quartz em cluster (11 tabelas `QRTZ_*`) fica em `alternativa-quartz/`, para adoção futura.

## Capabilities

### New Capabilities
- `agendamento-tarefas`: CRUD e ciclo de vida de `Tarefa`, agendamento automático em todas as instâncias com execução única por ocorrência, recuperação de disparo perdido e de tarefa presa, extensibilidade por `action`, segurança, alertas consultáveis.
- `orquestracao-curvas`: já existia nesta change (não arquivada); reescrita como a `action` de download do motor `agendamento-tarefas`, para as três fontes.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas na develop. -->

## Impact

- **services/orchestrator:** `SpringSchedulerAdapter` agendando por ocorrência; reivindicação e transições atômicas no `TaskJpaPersistenceAdapter`; `ReconciliacaoAgendamentos`; remoção de `TarefaService`, `TarefaUseCase` e dos mapas em memória; `destino`/`caminho` na action `http`; remoção do webhook de notificação; rota de alertas; correção do `TarefaJpaMapper`; `DownloadCargaTaskActionAdapter`.
- **Banco:** nenhum script; usa só `tTrefaAgnda`, `tParmTrefa` e `tLogTrefa`, que já existem.
- **Front/BFF:** o dashboard passa a consumir `GET /api/v1/alertas` (change do front, futura).
- **Dependências:** `b3/taxa-swap/download` e `b3/taxa-swap/reprocessamento` da function B3 (change `conector-b3-webhook-ingest`); rotas das functions ANBIMA (`ms`) e Bloomberg (nós da SOFR, com os tickers), no mesmo contrato (changes próprias); a exportação de calendário do engine (change `engine-construcao-curvas`).
- **Fora de escopo:** a construção da data pelo orquestrador (o engine é disparado pelo processor), as rotas ANBIMA e Bloomberg nas functions (changes próprias), os cadastros em si (feitos pela tela; sugestão em `cadastros-sugeridos.txt`), a tela do dashboard, lógica de curva, alteração de schema.
