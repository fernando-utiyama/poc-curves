## Purpose

Define o motor genérico de tarefas do orquestrador (`services/orchestrator`): cadastro (CRUD), ciclo de vida, agendamento por cron ou intervalo em cluster, execução extensível por `action`, segurança e observabilidade. Este motor não conhece curva, B3, ANBIMA nem engine: essas regras vivem só nas `action` cadastradas nele (spec `orquestracao-curvas`).

## ADDED Requirements

### Requirement: Cadastro de tarefa
Uma `Tarefa` SHALL ter `nome`, `descricao` e `action` obrigatórios, e no máximo uma regra de agendamento: `regraCron` (expressão cron do Quartz, até 15 caracteres, validada por `org.quartz.CronExpression`) ou `regraIntervalo` (instante ou duração ISO-8601). `action` SHALL ser um nome atendido por algum `TaskActionPort` (`console`, `http`, `b3-taxa-swap-download`, `construcao-curvas-data`); um nome não atendido MUST falhar com 400. Criar uma tarefa SHALL colocá-la em `PRONTA` e devolver 201 com a localização do recurso. `PATCH` SHALL atualizar só os campos enviados, e MUST recusar alterar uma tarefa `REMOVIDA` ou `EXECUTANDO`. Os campos `action` e `descricao` SHALL ser gravados e lidos cada um na sua coluna, sem inversão.

#### Scenario: Action não atendida
- **WHEN** uma tarefa é criada com `action` = `"ftp-download"`
- **THEN** a criação falha com 400, sem gravar nada

#### Scenario: Cron no formato do Spring
- **WHEN** `regraCron` = `"0 0 19 * * *"` (sem `?`, inválido no Quartz)
- **THEN** a criação falha com 400, indicando a expressão inválida

#### Scenario: Cron maior que o permitido
- **WHEN** `regraCron` = `"0 0/10 18-21 * * ?"` (18 caracteres)
- **THEN** a criação falha por tamanho, antes de qualquer gravação

#### Scenario: Campos lidos como gravados
- **WHEN** uma tarefa é criada com `action` = `"http"` e `descricao` = `"Chama o conector"`
- **THEN** a consulta devolve exatamente `action` = `"http"` e `descricao` = `"Chama o conector"`

### Requirement: Ciclo de vida
Uma `Tarefa` SHALL ter um dos estados `PRONTA`, `AGENDADA`, `EXECUTANDO`, `FINALIZADA`, `ERRO`, `CANCELADA`, `DESABILITADA` ou `REMOVIDA`, com as transições:

| De | Para |
|---|---|
| `PRONTA` | `AGENDADA`, `DESABILITADA` |
| `AGENDADA` | `PRONTA`, `EXECUTANDO` |
| `EXECUTANDO` | `AGENDADA`, `FINALIZADA`, `ERRO`, `CANCELADA` |
| `FINALIZADA`, `ERRO`, `CANCELADA` | `PRONTA` |
| `DESABILITADA` | `PRONTA`, `REMOVIDA` |
| `REMOVIDA` | nenhuma |

Toda mudança de estado SHALL ser atômica e condicionada ao estado esperado: se outra instância mudou o estado antes, a operação MUST falhar com conflito, sem sobrescrever. Uma transição fora da tabela MUST falhar sem alterar o estado. Só uma tarefa `DESABILITADA` SHALL poder ser removida. Desabilitar ou remover uma tarefa SHALL remover o gatilho dela do agendador na mesma transação.

#### Scenario: Remover sem desabilitar
- **WHEN** um operador tenta remover uma tarefa `PRONTA`
- **THEN** a operação falha com a transição inválida, e a tarefa continua `PRONTA`

#### Scenario: Duas instâncias mudando o mesmo estado
- **WHEN** a instância A cancela uma tarefa `EXECUTANDO` no mesmo instante em que a instância B a conclui
- **THEN** só uma das mudanças é gravada, e a outra recebe conflito

### Requirement: Agendamento em cluster
O agendamento SHALL usar o Quartz com JDBC JobStore em cluster (`isClustered=true`) no SQL Server, com as tabelas `QRTZ_*` criadas pelo script da change (`initialize-schema=never`). Cada tarefa SHALL ter um job durável criado no cadastro; `regraCron` SHALL gerar um gatilho cron no fuso `America/Sao_Paulo`; `regraIntervalo` em instante SHALL gerar um gatilho de execução única, e em duração, um gatilho repetido. Criar, alterar, desabilitar e remover uma tarefa SHALL gravar o cadastro e o gatilho na mesma transação, e a mudança SHALL valer imediatamente em todas as instâncias. O disparo SHALL ser automático, sem chamada a `/iniciar`: gatilhos ficam no banco e sobrevivem a reinícios. `/api/v1/agendador/parar` e `/iniciar` SHALL pausar e retomar todos os gatilhos do cluster, e não só da instância que recebeu a chamada.

#### Scenario: Reinício sem chamada manual
- **WHEN** as instâncias reiniciam num deploy
- **THEN** as tarefas continuam disparando nos horários, sem ninguém chamar `/api/v1/agendador/iniciar`

#### Scenario: Cadastro alterado numa instância
- **WHEN** um `PATCH` muda o `regraCron` de uma tarefa pela instância A
- **THEN** o próximo disparo já segue o novo horário, qualquer que seja a instância que dispare

#### Scenario: Parar o agendador
- **WHEN** um operador chama `/api/v1/agendador/parar` na instância A
- **THEN** nenhuma instância dispara tarefa até alguém chamar `/iniciar`

### Requirement: Execução única e recuperação
Com 2 ou mais instâncias, cada disparo SHALL ser executado por uma só instância, e a mesma tarefa MUST NOT executar em paralelo em duas instâncias (job `@DisallowConcurrentExecution`). Se a instância cair no meio de uma execução, outra instância SHALL executar a tarefa de novo depois de perceber a queda (`requestsRecovery`), registrando no log que é uma retomada. Um disparo que caia com todas as instâncias fora SHALL ser executado uma vez quando alguma voltar (misfire `FIRE_AND_PROCEED` no cron, `FIRE_NOW` no intervalo), e não uma vez para cada horário perdido. A execução manual (`/executar`) SHALL passar pelo mesmo job, com as mesmas garantias.

#### Scenario: Duas instâncias no mesmo horário
- **WHEN** as duas instâncias estão no ar no horário do gatilho de uma tarefa
- **THEN** a tarefa executa uma vez, numa só instância

#### Scenario: Instância cai no meio
- **WHEN** a instância A cai enquanto executa uma tarefa
- **THEN** a instância B executa a tarefa de novo, e o log registra que é uma retomada

#### Scenario: Deploy no horário do disparo
- **WHEN** as duas instâncias estão fora das 18h55 às 19h10, e a tarefa tinha disparo às 19h
- **THEN** a tarefa executa uma vez quando a primeira instância volta

#### Scenario: Manual durante a execução agendada
- **WHEN** um operador pede `/executar` de uma tarefa que está executando pelo agendamento
- **THEN** a execução manual só começa depois que a agendada termina

### Requirement: Status a partir do banco
O status de uma tarefa (`GET .../tarefas/{id}/status`) e o status global (`GET .../status`) SHALL vir da situação e dos logs gravados, e a próxima execução, do gatilho gravado no Quartz; nunca de estado guardado só na memória da instância que respondeu. A mesma consulta em instâncias diferentes SHALL devolver o mesmo resultado.

#### Scenario: Consultar noutra instância
- **WHEN** a tarefa X executa na instância A, e o status dela é consultado na instância B
- **THEN** a instância B devolve a mesma situação, última execução, próxima execução e última mensagem

### Requirement: Extensibilidade por `action`
Uma nova `action` SHALL poder ser adicionada implementando `TaskActionPort` (`getActionName`, `getSupportedActionNames`, `execute`), sem alterar cadastro, agendamento ou execução única. Uma `action` MUST validar os próprios parâmetros e falhar com erro claro quando um obrigatório faltar ou for inválido. Uma `action` SHALL poder pedir ao `SchedulerPort` um gatilho único de nova tentativa da mesma tarefa num instante futuro, com as mesmas garantias de cluster dos demais gatilhos.

#### Scenario: Parâmetro obrigatório ausente
- **WHEN** uma tarefa com `action` = `"http"` não tem o parâmetro `destino`
- **THEN** a execução falha com erro claro sobre o parâmetro, sem nenhuma chamada

### Requirement: Segurança das rotas
Toda rota de consulta (`GET`) SHALL exigir o papel `Curvas.Leitura`; toda rota de mutação (criar, atualizar, desabilitar, habilitar, remover tarefa; iniciar, parar, agendar, desagendar, executar, cancelar, resetar) SHALL exigir `Curvas.Operador`. Sem token válido ou sem o papel, a resposta MUST ser 401 ou 403, sem executar nada.

#### Scenario: Executar sem o papel
- **WHEN** um token sem `Curvas.Operador` chama `POST /api/v1/agendador/tarefas/{id}/executar`
- **THEN** a resposta é 403, e a tarefa não muda de estado

### Requirement: Chamada de saída restrita a destino cadastrado
A `action` `http` MUST NOT aceitar URL livre. Ela SHALL receber `destino` (chave de `orquestrador.http.destinos`, cada uma com base-URL e credencial de configuração ou cofre) e `caminho` (sem esquema nem host); `destino` não cadastrado MUST falhar sem chamar nada. Nenhum parâmetro de tarefa SHALL definir o cabeçalho `Authorization` da chamada.

#### Scenario: Destino não cadastrado
- **WHEN** a tarefa tem `destino` = `"externo-desconhecido"`
- **THEN** a execução falha antes de qualquer chamada de rede

#### Scenario: Tentativa de sobrescrever o Authorization
- **WHEN** um parâmetro da tarefa é `header.Authorization`
- **THEN** ele é ignorado; a chamada usa só a credencial do `destino`

### Requirement: Webhook com segredo próprio
A notificação de mudança de tarefa para o webhook configurado MUST NOT repassar o `Authorization` de quem chamou a API. O corpo SHALL ser assinado com HMAC-SHA256 usando `orquestrador.webhook.segredo`, no cabeçalho `X-Webhook-Signature`.

#### Scenario: Execução via API autenticada
- **WHEN** um operador autenticado executa uma tarefa, e ela notifica o webhook
- **THEN** o webhook recebe `X-Webhook-Signature`, e não recebe o token do operador

### Requirement: Alertas consultáveis
Um alerta de tarefa SHALL ser gravado em `tLogTrefa` da própria tarefa, com código `500` e texto JSON com os campos `alerta` (tipo), `dataBase` e `detalhe`. `GET /api/v1/alertas?dataInicial=&dataFinal=` (`Curvas.Leitura`) SHALL devolver os alertas do período, cada um com tarefa (id e nome), tipo, data-base, instante e detalhe, ordenados do mais recente, para o dashboard do front.

#### Scenario: Dashboard lista os alertas do dia
- **WHEN** o front consulta os alertas de `2026-09-14`
- **THEN** recebe cada alerta registrado nessa data, com tarefa, tipo e detalhe
