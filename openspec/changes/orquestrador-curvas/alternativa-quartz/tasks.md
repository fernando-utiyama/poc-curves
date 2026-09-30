Guia de implementação: ajustes no `services/orchestrator` real já transcrito. Cada item diz a classe a mexer. Testes com mocks (sem banco nem Quartz real); o que exige cluster real vai para homologação (3.2).

## 1. Motor de tarefas (`agendamento-tarefas`)

- [ ] 1.1 Pedir ao DBA o `scripts/quartz-orquestrador.sql` (DDL oficial do Quartz 2.5.2, sem alteração; volta comentada no fim); verificar num banco de teste que cria as 11 tabelas `QRTZ_*` e que a volta remove só elas
- [ ] 1.2 Acrescentar ao pom `spring-boot-starter-quartz` (versão do Spring Boot) e, no `application.yml`, `spring.quartz.job-store-type: jdbc`, `spring.quartz.jdbc.initialize-schema: never` e as propriedades de cluster da D2 (`isClustered`, `MSSQLDelegate`, `instanceId: AUTO`, `clusterCheckinInterval`); verificar que o contexto sobe com o Quartz apontando para o `DataSource` da aplicação
- [ ] 1.3 Criar `adapter/in/scheduler/ExecutarTarefaJob` (`@DisallowConcurrentExecution`, lê o id da tarefa e a `dataBase` opcional do `JobDataMap`, chama `SchedulerUseCase`; em `isRecovering()` registra a retomada) e `adapter/out/scheduler/QuartzSchedulerAdapter implements SchedulerPort` (job durável `tarefa-{id}` com `requestsRecovery`, `CronTrigger` no fuso `America/Sao_Paulo` com `FIRE_AND_PROCEED`, `SimpleTrigger` para instante/duração com `FIRE_NOW`, gatilho único de nova tentativa, `pauseAll`/`resumeAll`, `triggerJob` para a execução manual); apagar `SpringSchedulerAdapter` e `SchedulerConfiguration`; verificar com `Scheduler` simulado os jobs, gatilhos, fuso e misfire criados para cron, instante e duração
- [ ] 1.4 Apagar `TarefaService`, `TarefaUseCase` e os mapas em memória de `SchedulerService`; criar a checagem única de subida (job e gatilho para toda tarefa cadastrada que não tenha; cron inválido só no log); `TarefaCrudService` chama o `SchedulerPort` ao criar, alterar, desabilitar e remover, na mesma transação; verificar com portas simuladas cada chamada e que a checagem não duplica job existente
- [ ] 1.5 Tornar atômicas as mudanças de situação (`UPDATE ... WHERE cIdtfdTrefa = ? AND cSit = ?` no `TaskJpaPersistenceAdapter`, com zero linhas = conflito); verificar o conflito com o repositório simulado devolvendo zero linhas
- [ ] 1.6 Trocar a validação de cron (`TarefaCrudService`, `TarefaRequestDto`) e o cálculo da próxima execução (`TarefaJpaResponseMapper`, `SchedulerService`) para `org.quartz.CronExpression` / `getNextFireTime` do gatilho; verificar os cenários de cron da spec (formato Spring recusado, 18 caracteres recusado, `0 0 19-21 * * ?` aceito)
- [ ] 1.7 Status das duas rotas de status vindo de `RecuperarStatusUseCase` (banco + `tLogTrefa`) e próxima execução do gatilho; verificar que a resposta não depende de estado em memória
- [ ] 1.8 Acrescentar ao pom `spring-boot-starter-oauth2-resource-server`, configurar o Resource Server (padrão do engine) e exigir `Curvas.Leitura` nos `GET` e `Curvas.Operador` nas mutações de `SchedulerAPI`/`TarefaAPI`; verificar 401/403 em cada rota com `MockMvc`
- [ ] 1.9 `HttpTaskActionAdapter`: trocar `url` por `destino` (`orquestrador.http.destinos`) e `caminho`, ignorar `header.Authorization` de parâmetro; verificar destino não cadastrado sem chamada e que o `Authorization` de parâmetro não sai
- [ ] 1.10 `RestClientSchedulerWebhookClient`: trocar `propagateAuthorizationHeader` por `X-Webhook-Signature` (HMAC-SHA256 com `orquestrador.webhook.segredo`); verificar a assinatura e que o token do chamador não sai
- [ ] 1.11 Criar `GET /api/v1/alertas?dataInicial=&dataFinal=` (`Curvas.Leitura`), lendo de `tLogTrefa` os registros código `500` com JSON `alerta`; verificar o cenário da spec
- [ ] 1.12 Corrigir `TarefaJpaMapper` (`action` ↔ `cAcaoOperSist`, `descricao` ↔ `rTrefa`) depois de confirmar com o time (D10); verificar o cenário "Campos lidos como gravados"

## 2. Tarefas de curva (`orquestracao-curvas`)

- [ ] 2.1 Criar a checagem de dia útil (calendário do engine, cache até o fim do dia, recuo para fim de semana com o engine fora); verificar feriado, fim de semana, engine fora e servidor em UTC
- [ ] 2.2 Criar `B3DownloadTaskActionAdapter` (`b3-taxa-swap-download`): sucesso do dia já registrado, conferência da `dataBase`, nova tentativa pelo `SchedulerPort` até o `limiteHorario`, `CARGA_NAO_RECEBIDA` uma vez por dia, parada em 4xx, reprocessamento na execução manual com data passada; verificar os cenários da spec
- [ ] 2.3 Criar `ConstrucaoDataTaskActionAdapter` (`construcao-curvas-data`): repetição em 5xx/tempo esgotado (1, 2, 4 min), resumo por situação, última execução do dia pela expressão cron, `CURVAS_PENDENTES` só nela; verificar os cenários da spec e o 403 sem repetição
- [ ] 2.4 Cliente de saída com token de client credentials e `X-Correlation-Id` para os destinos `conector` e `engine`; verificar com servidor simulado que o token e o cabeçalho vão em toda chamada e que nenhum log contém o token
- [ ] 2.5 Contadores Micrometer por tipo de alerta; verificar que cada alerta incrementa o seu contador

## 3. Fechamento

- [ ] 3.1 Rodar `openspec validate orquestrador-curvas --strict` e a suíte do orquestrador; verificar que tudo passa
- [ ] 3.2 Homologação (infra real, não é teste automatizado): duas instâncias no mesmo banco — cada disparo roda uma vez; derrubar uma no meio de uma execução e ver a retomada na outra; parar as duas no horário de um disparo e ver a execução única ao voltar; cadastrar as duas tarefas de curva e acompanhar um dia completo com os alertas no dashboard
