## Context

- **Código real transcrito (2026-09-29).** `services/orchestrator` é hexagonal e já usa Java 21 nativo (records nos DTOs, enums). O domínio gira em torno de `Tarefa` (`id`, `nome`, `descricao`, `action`, `regraCron`, `regraIntervalo`, `status`, `parametros`, `logs`, mais datas e execuções), persistida em `tTrefaAgnda`/`tParmTrefa`/`tLogTrefa`. Uma `action` é resolvida por `TaskActionExecutor` a partir do nome cadastrado e delegada a uma implementação de `TaskActionPort`; hoje existem `ConsoleTaskActionAdapter` (`console`) e `HttpTaskActionAdapter` (`http`).
- **Rotas reais.** `TarefaController`/`TarefaCrudService` fazem o CRUD (`/api/v1/tarefas`); `SchedulerController`/`SchedulerService` fazem iniciar/parar/agendar/executar/cancelar/resetar (`/api/v1/agendador/*`). O agendamento físico está atrás da porta `SchedulerPort` (`schedule`, `stop`, `stopAll`, `getTaskStatus`), hoje implementada por `SpringSchedulerAdapter` (`ThreadPoolTaskScheduler`, em memória).
- **Disparo automático só no `TarefaService`.** O agendamento na subida (`ApplicationReadyEvent`) e a reconciliação a cada 60 s só existem nele, desligados por padrão (`scheduler.enabled:false`), e ele duplica CRUD e execução do fluxo real. `SchedulerService.startAll()` só roda quando alguém chama `POST /api/v1/agendador/iniciar`.
- **Nada funciona com 2 instâncias.** Agendamento, status e a trava de execução (`synchronized` num `ConcurrentHashMap`) vivem na memória de cada JVM: com 2 instâncias no Azure (regra do projeto), a mesma tarefa dispara nas duas, o status depende de qual instância responde, e um disparo com as duas fora (deploy) se perde.
- **Sem segurança.** Nenhuma rota exige papel; o pom não tem `spring-boot-starter-oauth2-resource-server`.
- **SSRF e vazamento de token.** `HttpTaskActionAdapter` monta a chamada (URL, método, headers) inteira a partir dos parâmetros da tarefa. `RestClientSchedulerWebhookClient` repassa o `Authorization` de quem chamou a API do orquestrador para as URLs de webhook configuradas.
- **Mapeamento trocado.** Em `TarefaJpaMapper`, `domain.descricao` vem de `cAcaoOperSist` e `domain.action` vem de `rTrefa`, ao contrário do que os nomes das colunas sugerem.
- **Limite de cron.** `tTrefaAgnda.cRegraAgnda` é `VARCHAR(15)`, e `TarefaRequestDto` valida `@Size(max = 15)`.
- **Conector B3** (change `conector-b3-webhook-ingest`): `POST /api/b3/taxa-swap/download?date=AAAA-MM-DD` responde 200 com `dataBase` e `idCarga` (a busca de dias anteriores pode devolver um arquivo antigo), 502/503 em falha; `POST /api/b3/taxa-swap/reprocessamento?dataBase=` reprocessa.
- **Engine** (change `engine-modelos-curva`): `POST /api/v1/construcoes/{dataBase}` (papel `Curvas.Orquestrador`), idempotente; `GET /api/v1/calendarios/Brazil?mercado=Settlement&anoInicial=&anoFinal=` (papel `Curvas.Leitura`).

## Goals / Non-Goals

**Goals:** disparo agendado automático, correto com 2+ instâncias (um disparo roda uma vez, sobrevive à queda de uma instância e ao deploy), status igual em qualquer instância, rotas com papel, sem SSRF nem vazamento de token; as duas tarefas das curvas cadastradas nesse motor, com alertas visíveis no dashboard do front.

**Non-Goals:** lógica de curva, a tela do dashboard (é do front/BFF), downloads de ANBIMA e Bloomberg, alteração de tabela existente, alerta por e-mail ou Teams.

## Decisions

### D1. Tarefas de curva são `action`, não comportamento fixo do orquestrador
`B3DownloadTaskActionAdapter` e `ConstrucaoDataTaskActionAdapter` implementam `TaskActionPort`, ao lado de `console` e `http`, e são resolvidas como `b3-taxa-swap-download` e `construcao-curvas-data`. O motor fica genérico; toda regra de curva mora só nessas duas classes. **Alternativa rejeitada:** tarefas fixas dentro do orquestrador, que duplicariam o CRUD que já existe.

### D2. Agendamento pelo Quartz em cluster (JDBC JobStore)
O `SchedulerPort` real já isola o agendador; troca-se só a implementação: sai `SpringSchedulerAdapter` (memória), entra `QuartzSchedulerAdapter` (`spring-boot-starter-quartz`, versão do Spring Boot 4.1.1, Quartz 2.5.2), com:

- `spring.quartz.job-store-type=jdbc`, `org.quartz.jobStore.isClustered=true`, `org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.MSSQLDelegate`, `org.quartz.scheduler.instanceId=AUTO`, `org.quartz.jobStore.clusterCheckinInterval=20000`, `spring.quartz.jdbc.initialize-schema=never`;
- 11 tabelas `QRTZ_*` pelo DDL oficial do Quartz 2.5.2 para SQL Server, em script próprio (`scripts/quartz-orquestrador.sql`), separado do alter da change `banco-curvas-ajustes`, que já foi pedido;
- cada `Tarefa` vira um `JobDetail` durável (`tarefa-{id}`, grupo `tarefas`, `requestsRecovery=true`) de `ExecutarTarefaJob` (`@DisallowConcurrentExecution`), criado já no cadastro, com um gatilho só quando há regra: `CronTrigger` no fuso `America/Sao_Paulo` para `regraCron`; `SimpleTrigger` para `regraIntervalo` (instante: uma vez; duração: repetido);
- `ExecutarTarefaJob` só lê o id da tarefa e chama `SchedulerUseCase` (porta de entrada); nenhuma regra dentro do job.

O que o Quartz resolve, sem código nosso: **um disparo roda numa só instância** (o gatilho é adquirido com trava em `QRTZ_LOCKS`); **a mesma tarefa nunca roda em paralelo** (`@DisallowConcurrentExecution` vale para o cluster); **instância cai no meio** (outra percebe pelo checkin e roda de novo o job, `requestsRecovery`); **disparo perdido com todas fora** (política de misfire `FIRE_AND_PROCEED` no cron e `FIRE_NOW` no intervalo: roda uma vez ao voltar); **mudança de cadastro vale na hora em todas as instâncias** (o gatilho está no banco). Cadastro e gatilho gravam na mesma transação (`LocalDataSourceJobStore` usa o `DataSource` e as transações do Spring).

`/api/v1/agendador/parar` e `/iniciar` viram `pauseAll()`/`resumeAll()` (estado no banco, valem para o cluster), e não mais `standby` local.

**Alternativas rejeitadas:** trava feita à mão (`UPDATE` condicional + reconciliação a cada 60 s + destravar pelo log), que atrasava mudanças entre instâncias e perdia disparo com tudo fora; `db-scheduler` (uma tabela só, mas menos conhecido); `sp_getapplock`.

### D3. Sintaxe e limite do cron
O cron cadastrado passa a ser o do Quartz (segundos primeiro; `?` obrigatório no dia do mês ou no dia da semana; dia da semana 1 = domingo). A validação do cadastro e o cálculo da próxima execução usam `org.quartz.CronExpression`, e não mais `org.springframework.scheduling.support.CronExpression`. A coluna continua `VARCHAR(15)` (nenhuma alteração de tabela existente): expressões como `0 0 18 * * ?` (12) e `0 0 19-21 * * ?` (15) cabem; janelas "a cada N minutos" não cabem e não são necessárias (D9).

### D4. Um só fluxo; transição de situação atômica
`TarefaService`, `TarefaUseCase` e a reconciliação a cada 60 s são apagados: o Quartz guarda os gatilhos no banco, então a subida não precisa reagendar nada. Na subida, uma checagem única cria o `JobDetail` e o gatilho de toda tarefa cadastrada que ainda não tenha (migração das tarefas existentes antes do Quartz), sem efeito nas demais. Toda mudança de situação em `tTrefaAgnda` é um `UPDATE ... WHERE cIdtfdTrefa = ? AND cSit = <situação esperada>`: zero linhas afetadas significa que outra instância mudou antes, e a operação falha com conflito em vez de sobrescrever (ex.: cancelar numa instância enquanto a outra conclui). Um job rodando de novo por recuperação (`JobExecutionContext.isRecovering()`) aceita a tarefa em `EXECUTANDO` e registra "execução retomada após queda de instância".

### D5. Status sempre do banco
Removem-se os mapas em memória de `SchedulerService` e do adaptador de agendamento. O status vem de `tTrefaAgnda` + `tLogTrefa` (caminho que `RecuperarStatusService` já usa), e a próxima execução vem do gatilho do Quartz (`getNextFireTime`), também no banco: qualquer instância responde igual.

### D6. Papel exigido em toda rota
Resource Server com token do Entra ID (mesmo padrão do engine): `Curvas.Leitura` nas consultas (`GET`), `Curvas.Operador` em toda mutação.

### D7. Action `http` só chama destino cadastrado (corrige SSRF)
O parâmetro `url` deixa de existir: a tarefa tem `destino` (chave de `orquestrador.http.destinos`, cada uma com base-URL e credencial de configuração ou cofre) e `caminho` (só o path). Nenhum parâmetro de tarefa define `Authorization`; a chamada de saída sempre usa o token de client credentials do destino.

### D8. Webhook com segredo próprio (corrige vazamento de token)
Troca `propagateAuthorizationHeader` por assinatura HMAC-SHA256 do corpo com `orquestrador.webhook.segredo`, no cabeçalho `X-Webhook-Signature`.

### D9. Download B3: um disparo por dia, novas tentativas pelo próprio Quartz
A tarefa `b3-taxa-swap-download` tem um cron só com o início da janela (ex.: `0 0 18 * * ?`) e os parâmetros `intervaloMinutos` e `limiteHorario`. Cada execução é uma tentativa; se o arquivo do dia ainda não saiu e a próxima tentativa cabe antes do `limiteHorario`, a action pede ao `SchedulerPort` um gatilho único de nova tentativa (`tarefa-{id}-tentativa`, `SimpleTrigger` em agora + `intervaloMinutos`), que o Quartz persiste e dispara em uma só instância como qualquer outro. Chegado o limite sem sucesso, grava o alerta (D11). **Alternativa rejeitada:** laço com espera dentro da execução, que prenderia uma thread e a tarefa em `EXECUTANDO` por horas.

### D10. Correção do mapeamento `action`/`descricao` no `TarefaJpaMapper`
`domain.action` passa a vir de `cAcaoOperSist` e `domain.descricao` de `rTrefa`. Só o mapeamento Java muda. **Confirmar antes com o time** (Open Questions).

### D11. Alertas no dashboard do front
`CARGA_NAO_RECEBIDA` e `CURVAS_PENDENTES` são linhas de `tLogTrefa` da própria tarefa, com código `500` e texto JSON (`{"alerta": ..., "dataBase": ..., "detalhe": ...}`), sem tabela nova. O orquestrador expõe `GET /api/v1/alertas?dataInicial=&dataFinal=` (`Curvas.Leitura`), que lista esses registros já interpretados, para o dashboard do front (via BFF) mostrar. Métricas (Micrometer) continuam para monitoração; e-mail e Teams estão fora.

### D12. Data-base, dia útil e identidade de serviço
Data-base é o dia de hoje em `America/Sao_Paulo`; dia não útil no calendário `Brazil`/`Settlement` do engine (exportado e guardado em memória até o fim do dia) não chama nada; com o engine fora, só sábado e domingo são pulados. Por isso o cron pode ser de todos os dias (`?`/`*` no dia da semana), o que ajuda no limite de 15 caracteres. Chamadas ao conector e ao engine levam token do Entra ID por client credentials (`Curvas.Orquestrador` e `Curvas.Leitura` no engine).

## Risks / Trade-offs

- **11 tabelas novas e uma dependência nova.** Aceito em troca de não manter agendador distribuído próprio; o script é o DDL oficial, sem alteração, com volta.
- **Carga no banco.** Cada instância faz checkin a cada 20 s e adquire gatilhos com trava; para dezenas de tarefas é desprezível.
- **Cron diferente do que o time usa hoje.** Tarefas já cadastradas com sintaxe Spring (sem `?`) falham na migração da D4; a checagem de subida lista as inválidas no log em vez de parar a aplicação, e elas precisam ser corrigidas pelo `PATCH`.
- **Relógio das instâncias.** O Quartz em cluster exige relógios sincronizados (NTP), o que o Azure já garante.
- **Corrigir o mapper pode expor uma leitura diferente da atual** (D10).
- **Papel exigido quebra chamador sem token.** Validar em homologação antes.

## Migration Plan

1. Pedir ao DBA o terceiro script, `scripts/quartz-orquestrador.sql` (ida; volta comentada no fim).
2. Subir o orquestrador com Quartz, papéis e as correções de segurança (D2–D8); a checagem da D4 cria jobs e gatilhos das tarefas existentes.
3. Confirmar D10 com o time e aplicar.
4. Cadastrar as duas tarefas de curva via API.
5. Rollback: desabilitar as tarefas pelo cadastro. Voltar ao agendador em memória não é recomendado com 2+ instâncias; as tabelas `QRTZ_*` podem ficar (não afetam nada) ou sair pela volta do script.

## Open Questions

- A inversão `action`/`descricao` no `TarefaJpaMapper` é bug, ou algum consumidor já lê pelos nomes atuais (D10)?
- Horários de operação: início e `limiteHorario` do download B3, `intervaloMinutos` entre tentativas, e os horários da construção da data (o último do dia é o que dispara `CURVAS_PENDENTES`).
- Existem tarefas já cadastradas em algum ambiente com cron no formato do Spring (sem `?`), que precisariam ser corrigidas na migração?
