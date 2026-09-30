## Why

O orquestrador (`services/orchestrator`) já foi transcrito do código real: é um motor genérico de tarefas agendadas (`Tarefa`, cadastro por CRUD, agendamento por cron ou intervalo, execução por uma `action` extensível), ainda sem as tarefas das curvas e com falhas que impedem produção. O agendamento, o status e a trava de execução vivem na memória de cada instância, então com as 2 instâncias do Azure a mesma tarefa dispara duas vezes e um disparo com as duas fora (deploy) se perde. Além disso, o disparo automático só existe num fluxo paralelo desligado por padrão (`TarefaService`), nenhuma rota exige papel, a action `http` aceita chamar qualquer endereço com qualquer cabeçalho (SSRF) e o webhook repassa o token de quem chamou (vazamento de credencial).

## What Changes

- **Motor de tarefas (capability `agendamento-tarefas`):**
  - **Agendamento pelo Quartz em cluster** (JDBC JobStore no SQL Server), atrás da porta `SchedulerPort` que já existe: um disparo roda numa só instância, a mesma tarefa nunca roda em paralelo, instância que cai no meio tem a execução retomada por outra, disparo perdido com tudo fora roda uma vez ao voltar, e mudança de cadastro vale na hora em todas as instâncias;
  - **11 tabelas `QRTZ_*`** pelo DDL oficial do Quartz 2.5.2, em script próprio (`scripts/quartz-orquestrador.sql`), separado do alter da change `banco-curvas-ajustes`; nenhuma tabela existente muda;
  - **Um só fluxo:** apaga o `TarefaService` e a reconciliação a cada 60 s; status sempre lido do banco; mudança de situação atômica e condicionada ao estado esperado;
  - **Cron do Quartz** (validação e próxima execução pelo `org.quartz.CronExpression`), respeitando o limite de 15 caracteres da coluna;
  - **Segurança:** `Curvas.Leitura` nas consultas e `Curvas.Operador` nas mutações; action `http` só chama destino cadastrado; webhook assinado com segredo próprio;
  - **Alertas consultáveis:** `GET /api/v1/alertas`, para o dashboard do front;
  - **Correção** do mapeamento trocado de `action`/`descricao` no `TarefaJpaMapper`, só no Java.
- **Tarefas das curvas (capability `orquestracao-curvas`):** duas tarefas cadastradas no motor, cada uma com uma `action` nova (`TaskActionPort`):
  - `b3-taxa-swap-download`: um disparo no início da janela; cada tentativa sem o arquivo do dia agenda a próxima pelo próprio Quartz, até o horário limite, e aí grava `CARGA_NAO_RECEBIDA`;
  - `construcao-curvas-data`: disparos nos horários do dia; na última execução do dia, grava `CURVAS_PENDENTES` se ainda houver curva com erro ou sem insumo.
- **Alertas no dashboard do front:** `CARGA_NAO_RECEBIDA` e `CURVAS_PENDENTES` ficam no log da própria tarefa e saem pela rota de alertas; sem e-mail nem Teams.
- **Data-base e dia útil:** hoje em `America/Sao_Paulo`; dia não útil no `Brazil`/`Settlement` do engine não chama nada.
- **Execução manual:** a rota real `/api/v1/agendador/tarefas/{id}/executar`, com `dataBase` opcional (reprocessamento B3 para data passada).
- **Identidade de serviço:** token do Entra ID por client credentials, com `Curvas.Orquestrador` e `Curvas.Leitura` no engine.

## Capabilities

### New Capabilities
- `agendamento-tarefas`: CRUD e ciclo de vida de `Tarefa`, agendamento em cluster pelo Quartz, execução única e recuperação, extensibilidade por `action`, segurança, alertas consultáveis.
- `orquestracao-curvas`: já existia nesta change (não arquivada); reescrita como duas `action` do motor `agendamento-tarefas`.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas na develop. -->

## Impact

- **services/orchestrator:** `spring-boot-starter-quartz` e `spring-boot-starter-oauth2-resource-server` no pom; `QuartzSchedulerAdapter` e `ExecutarTarefaJob` no lugar de `SpringSchedulerAdapter`; remoção de `TarefaService`, `TarefaUseCase` e dos mapas em memória; validação de cron pelo Quartz; papéis nas rotas; `destino`/`caminho` na action `http`; assinatura do webhook; rota de alertas; correção do `TarefaJpaMapper`; `B3DownloadTaskActionAdapter` e `ConstrucaoDataTaskActionAdapter`.
- **Banco:** terceiro script, `scripts/quartz-orquestrador.sql` (11 tabelas `QRTZ_*`, com volta), a pedir ao DBA.
- **Entra ID:** identidade de serviço do orquestrador com `Curvas.Orquestrador` e `Curvas.Leitura` no engine; `Curvas.Operador`/`Curvas.Leitura` para quem chama a API do orquestrador.
- **Front/BFF:** o dashboard passa a consumir `GET /api/v1/alertas` (change do front, futura).
- **Dependências:** `b3/taxa-swap/download` e `b3/taxa-swap/reprocessamento` do conector (change `conector-b3-webhook-ingest`); `POST /api/v1/construcoes/{dataBase}` e a exportação de calendário do engine (change `engine-modelos-curva`).
- **Fora de escopo:** download ANBIMA e Bloomberg, a tela do dashboard, lógica de curva, alteração de tabela existente.
