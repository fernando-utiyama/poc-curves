## Context

Ver proposal.md para a motivação. Estado atual do código, para quem for implementar:

- `AgendamentoBootstrap` (`adapter/in/bootstrap/`) roda uma vez, no `ApplicationRunner`, e chama `schedulerRegistry.registrar(agendamento)` para cada agendamento ativo lido do banco — nunca mais depois disso.
- `AgendamentoSchedulerRegistry` (`adapter/out/scheduling/`) mantém um `ConcurrentHashMap<UUID, ScheduledFuture<?>>` em memória. `registrar`/`cancelar`/`reagendar` mexem só nesse mapa local.
- `AgendamentoService` (`application/usecase/`) chama `schedulerRegistry` diretamente dentro de `cadastrar`/`editar`/`ativar`/`desativar`, na mesma requisição HTTP que gravou no banco.
- `DisparoAgendadoExecutor.executar(UUID agendamentoId)` (`application/usecase/`) é o método que o `CronTrigger` de cada réplica dispara. Faz: buscar agendamento → checar dia de pregão → checar execução ativa (`buscarExecucaoAtivaParaConjuntoDados`) → inserir `ExecucaoCurva` → loop de tentativa com `Thread.sleep` chamando `AquisicaoExecutionService.acionarFeederEEncadear`.
- `db/migration/V14__execucao_curva_indice_ativa_por_alvo.sql` já garante, por índice único filtrado, que só pode existir uma `ExecucaoCurva` ativa (`PENDENTE`/`EXECUTANDO`/`CONSTRUINDO`) por `(conjunto_dados, data_referencia, momento_curva)` ou por `(definicao_curva_id, data_referencia, momento_curva)`. Essa proteção continua existindo depois desta mudança — o lock distribuído evita a corrida ANTES dela chegar ao banco, o índice único continua como última linha de defesa.
- `SchedulingConfig` (`config/`) declara o único bean hoje relacionado a agendamento: o `TaskScheduler` (`ThreadPoolTaskScheduler`, pool de 4).

## Goals / Non-Goals

**Goals:**
- Com N réplicas do curve-orchestrator, cada agendamento dispara exatamente uma vez por horário devido.
- Criar/editar/ativar/desativar um agendamento converge em todas as réplicas em poucos minutos, sem reinício.
- Mudança adaptada à infraestrutura já existente (SQL Server, sem adicionar Redis/Zookeeper só para isto).

**Non-Goals:**
- Não implementa eleição de líder nem torna uma réplica "primária" para todo o serviço — o lock é por-disparo (`agendamentoId` + data de referência), não um lock global do processo.
- Não muda o modelo de dados de `execucao_curva`/`agendamento` além da nova tabela `shedlock`.
- Não resolve replicação/alta disponibilidade de outros serviços Java além de confirmar (já verificado nesta sessão) que nenhum outro tem estado de agendamento em memória — `curve-processor`/`curve-engine` dependem só de consumer group do Kafka, que já é seguro multi-réplica.
- Não decide a orquestração real de N réplicas em produção (Kubernetes `replicas: N` é o alvo de produção da POC) — só garante que o compose local consiga simular 2 réplicas para validar o comportamento.

## Decisions

**D1. ShedLock com provider JDBC (SQL Server) em vez de Redis ou Zookeeper.**
O projeto já tem SQL Server como dependência de infraestrutura obrigatória; ShedLock com `shedlock-provider-jdbc-template` não adiciona nenhum serviço novo, só uma tabela. Alternativa descartada: lock via Redis (o projeto já tem Redis, mas só para cache de interpolação do curve-engine — usá-lo para lock introduziria acoplamento entre concerns não relacionados, e Redis não é obrigatório em todos os perfis do compose, `compose.lite.yaml` não o inclui).

**D2. Lock programático (`LockingTaskExecutor.executeWithLock`) em vez de `@SchedulerLock` anotado.**
O código não usa `@Scheduled` (usa `TaskScheduler.schedule(Runnable, Trigger)` programático via `CronTrigger`), então a forma de integração do ShedLock é a API programática, não a anotação — `DisparoAgendadoExecutor.executar` passa a envolver o corpo atual num `executeWithLock`, com nome de lock derivado de `agendamentoId` + data de referência (não só `agendamentoId` sozinho, porque o mesmo agendamento dispara em datas diferentes, e travas de dias diferentes não devem se bloquear entre si).

**D3. Reconciliação periódica substitui registro único no boot + mutação por HTTP.**
`AgendamentoSchedulerRegistry` ganha um método de reconciliação (chamado por um `@Scheduled(fixedDelay = ...)` interno, ou reaproveitando o mesmo `TaskScheduler` com uma tarefa periódica) que: lê `listarAtivos()` do banco, compara com o que está registrado localmente (`ConcurrentHashMap`), registra o que é novo, cancela o que sumiu ou foi desativado, e reagenda o que mudou de `expressaoHorario`/`fusoHorario` (comparando os campos relevantes, não recriando sempre). `AgendamentoBootstrap` chama essa mesma reconciliação na inicialização (comportamento de boot preservado), e `AgendamentoService` para de chamar `schedulerRegistry` — só escreve no banco. Intervalo de reconciliação: curto o bastante para não atrasar a percepção de um agendamento novo/editado de forma perceptível (ex.: 30-60s), mas longo o bastante para não sobrecarregar o banco com poll constante de N réplicas — número exato a decidir na implementação, configurável.

**D4. `lockAtMostFor` generoso, `lockAtLeastFor` zero.**
`lockAtMostFor` precisa cobrir o pior caso real do loop de tentativa em `DisparoAgendadoExecutor` (janela de tentativa do agendamento, que já é configurável por agendamento e pode chegar a vários minutos) — usar o próprio `janelaTentativaMinutos` do agendamento (mais uma margem) como `lockAtMostFor`, em vez de uma constante fixa, evita que o lock expire enquanto a réplica que o detém ainda está legitimamente dentro da janela de tentativa. `lockAtLeastFor` não precisa ser maior que zero — não há razão para segurar o lock além do tempo de processamento real.

## Risks / Trade-offs

- **[Risco] Escolher `lockAtMostFor` errado (curto demais) faz o lock expirar enquanto a réplica ainda está legitimamente dentro da janela de tentativa, permitindo que outra réplica tome o mesmo disparo no meio do caminho.** → Mitigação: D4 — derivar `lockAtMostFor` de `janelaTentativaMinutos` do próprio agendamento, não uma constante global; o índice único de `execucao_curva` (V14) continua como segunda linha de defesa mesmo nesse cenário residual.
- **[Risco] Intervalo de reconciliação longo demais atrasa a percepção de agendamentos novos/editados; curto demais sobrecarrega o banco com N réplicas fazendo poll.** → Mitigação: configurável via propriedade, começar com um valor conservador (ex.: 30s) e ajustar com medição real, não suposição.
- **[Trade-off] Reconciliação por polling é mais simples de implementar e depurar que um mecanismo de notificação (ex.: publicar evento de mudança de agendamento), mas introduz uma janela de inconsistência não-zero entre réplicas.** Aceito porque o domínio tolera isso — um agendamento editado alguns segundos atrasado em uma réplica não é um problema de correção de dado, só de latência de propagação de configuração.
- **[Risco] Testar de verdade "2 réplicas competindo pelo lock" exige rodar 2 instâncias do curve-orchestrator ao mesmo tempo contra o mesmo banco** — não é replicável só com testes unitários/`@SpringBootTest` de uma instância. → Mitigação: tarefa de implementação inclui subir 2 réplicas via compose local e observar os logs/tabela `shedlock`/tabela `execucao_curva` durante um disparo real.

## Migration Plan

- Nova migração Flyway (`V21__shedlock.sql` ou próximo número livre no momento da implementação) criando a tabela `shedlock` com o DDL padrão da biblioteca para SQL Server.
- Dependências novas em `services/curve-orchestrator/pom.xml`: `shedlock-spring` e `shedlock-provider-jdbc-template`, versão 6.6.0, gerenciadas via `dependencyManagement` no pom raiz (mesmo padrão já usado para H2).
- Nenhuma mudança de contrato de API pública (REST) do curve-orchestrator — a mudança é inteiramente interna à forma como o disparo é coordenado entre réplicas.
- Rollback: reverter para o `TaskScheduler` puro e remover a tabela `shedlock` é possível a qualquer momento antes do merge; depois de mesclada, reverter exige aceitar de volta o risco de disparo duplicado documentado na proposta — não recomendado sem motivo forte.
