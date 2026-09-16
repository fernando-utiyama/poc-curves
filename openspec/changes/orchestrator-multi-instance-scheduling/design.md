## Context

Ver proposal.md para a motivação e a restrição explícita de complexidade de banco. Estado atual do código, para quem for implementar:

- `AgendamentoBootstrap` (`adapter/in/bootstrap/`) roda uma vez, no `ApplicationRunner`, e chama `schedulerRegistry.registrar(agendamento)` para cada agendamento ativo lido do banco — nunca mais depois disso.
- `AgendamentoSchedulerRegistry` (`adapter/out/scheduling/`) mantém um `ConcurrentHashMap<UUID, ScheduledFuture<?>>` em memória. `registrar`/`cancelar`/`reagendar` mexem só nesse mapa local.
- `AgendamentoService` (`application/usecase/`) chama `schedulerRegistry` diretamente dentro de `cadastrar`/`editar`/`ativar`/`desativar`, na mesma requisição HTTP que gravou no banco.
- `DisparoAgendadoExecutor.executar(UUID agendamentoId)` (`application/usecase/`) é o método que o `CronTrigger` de cada réplica dispara. Faz: buscar agendamento → checar dia de pregão → checar execução ativa (`buscarExecucaoAtivaParaConjuntoDados`, um `SELECT`) → **inserir `ExecucaoCurva` sem tratamento de exceção** (linha 100, `execucaoCurvaRepository.inserir(execucao)`) → loop de tentativa com `Thread.sleep` chamando `AquisicaoExecutionService.acionarFeederEEncadear`.
- `db/migration/V14__execucao_curva_indice_ativa_por_alvo.sql` já garante, por índice único filtrado, que só pode existir uma `ExecucaoCurva` ativa (`PENDENTE`/`EXECUTANDO`/`CONSTRUINDO`) por `(conjunto_dados, data_referencia, momento_curva)` ou por `(definicao_curva_id, data_referencia, momento_curva)`. **Essa constraint já é, na prática, o lock distribuído de que este design precisa** — falta só tratar a violação dela como caminho esperado.
- `ReconciliacaoService.reconciliarExecucoesPresas()` (`application/usecase/`) já existe: busca `buscarExecucoesEmAndamento()` e marca tudo como falho com motivo `SERVICO_REINICIADO`. Hoje só é chamado por `ReconciliacaoInicializacaoRunner`, um `ApplicationRunner` — roda uma vez, só no boot de cada processo.
- `execucao_curva` (`db/migration/V3`) tem `iniciado_em DATETIME2` — não tem coluna de "última atualização". É o campo disponível para julgar há quanto tempo uma execução está parada.
- `SchedulingConfig` (`config/`) declara o único bean hoje relacionado a agendamento: o `TaskScheduler` (`ThreadPoolTaskScheduler`, pool de 4).

## Goals / Non-Goals

**Goals:**
- Com N réplicas do curve-orchestrator, cada agendamento dispara exatamente uma vez por horário devido.
- Criar/editar/ativar/desativar um agendamento converge em todas as réplicas em poucos minutos, sem reinício.
- **Zero tabelas novas, zero dependências novas** — reaproveitar só o que já existe (`execucao_curva`, `agendamento`, `ReconciliacaoService`).

**Non-Goals:**
- Não implementa eleição de líder nem lock distribuído genérico — a coordenação é inteiramente um efeito colateral da constraint única já existente em `execucao_curva`.
- Não muda o modelo de dados de `execucao_curva`/`agendamento` (nenhuma migração nova).
- Não resolve replicação/alta disponibilidade de outros serviços Java além de confirmar (já verificado nesta sessão) que nenhum outro tem estado de agendamento em memória — `curve-processor`/`curve-engine` dependem só de consumer group do Kafka, que já é seguro multi-réplica.
- Não decide a orquestração real de N réplicas em produção (Kubernetes `replicas: N` é o alvo de produção da POC) — só garante que o compose local consiga simular 2 réplicas para validar o comportamento.

## Decisions

**D1. Reaproveitar o índice único de `execucao_curva` como mecanismo de coordenação, em vez de um lock distribuído dedicado (ShedLock, Redis, Zookeeper).**
A constraint já existe (V14) e já rejeita a segunda tentativa de `INSERT` para o mesmo alvo/data/momento — o comportamento que falta é só tratar essa rejeição como sinal de "outra réplica já assumiu este disparo", não como erro. `DisparoAgendadoExecutor.executar` envolve o `execucaoCurvaRepository.inserir(execucao)` num `try/catch` que captura a exceção de violação de constraint (no Spring, `DataIntegrityViolationException` — ou, se o repositório usa JDBC puro, a checagem do código de erro do driver do SQL Server para violação de índice único, 2601/2627), loga em nível INFO ("disparo já assumido por outra réplica") e retorna sem propagar. Alternativa descartada: ShedLock — funcionaria, mas adiciona uma tabela e uma dependência para resolver um problema que a constraint já resolve; descartado pela restrição explícita de menor complexidade de banco.

**D2. Sem lock explícito, não há `lockAtMostFor` para dimensionar — a constraint junto com o estado da execução já delimita a janela.**
Enquanto a `ExecucaoCurva` da réplica vencedora estiver em estado ativo (`PENDENTE`/`EXECUTANDO`/`CONSTRUINDO`), a constraint impede qualquer segunda tentativa para aquele alvo/data/momento — não existe um "lock" com prazo próprio para expirar; o que existe é o próprio ciclo de vida da execução. Recuperação de uma execução travada (réplica caiu no meio do processamento) é tratada pela D3, não por expiração de lock.

**D3. `ReconciliacaoService.reconciliarExecucoesPresas()` passa a rodar periodicamente, não só no boot — com critério de "presa de verdade" por tempo decorrido.**
Hoje o método marca como falha TUDO que está em estado não-terminal, presumindo (correto só no boot) que o processo anterior morreu. Rodando periodicamente enquanto outras réplicas continuam vivas, essa suposição deixa de valer — uma execução `EXECUTANDO` pode ser trabalho legítimo de uma réplica saudável, ainda dentro da janela de tentativa. A query de busca (`buscarExecucoesEmAndamento`) ganha um filtro por tempo: só considera "presa" uma execução cujo `iniciado_em` é mais antigo que um limite configurável (ex.: maior janela de tentativa configurada entre os agendamentos ativos, mais uma margem — um valor simples e conservador, não uma junção por agendamento). Nenhuma coluna nova: `iniciado_em` já existe. `ReconciliacaoInicializacaoRunner` continua chamando o mesmo serviço no boot (comportamento preservado); passa a existir também uma chamada periódica (reaproveitando o `TaskScheduler` já existente ou um `@Scheduled` simples).

**D4. Reconciliação periódica do catálogo de agendamentos substitui registro único no boot + mutação por HTTP.**
`AgendamentoSchedulerRegistry` ganha um método de reconciliação que: lê `listarAtivos()` do banco (consulta já existente), compara com o que está registrado localmente (`ConcurrentHashMap`), registra o que é novo, cancela o que sumiu ou foi desativado, e reagenda o que mudou de `expressaoHorario`/`fusoHorario` (comparando os campos relevantes, não recriando sempre). `AgendamentoBootstrap` chama essa mesma reconciliação na inicialização, e `AgendamentoService` para de chamar `schedulerRegistry` — só escreve no banco. Intervalo configurável, começando conservador (ex.: 30-60s).

## Risks / Trade-offs

- **[Risco] Detectar a violação de constraint certa.** Capturar uma exceção genérica demais poderia mascarar um erro real de banco não relacionado a duplicidade. → Mitigação: checar explicitamente o tipo/código de erro de violação de constraint única (não um `catch (Exception e)` genérico), e só nesse caso tratar como "outra réplica assumiu" — qualquer outro erro continua propagando normalmente.
- **[Risco] Critério de "presa de verdade" baseado só em tempo decorrido (D3) pode ser conservador demais (falso negativo: espera mais que o necessário) ou agressivo demais (falso positivo: derruba execução legítima ainda lenta).** → Mitigação: usar um valor deliberadamente folgado (maior janela de tentativa configurada + margem generosa) — o custo de esperar um pouco mais para reconciliar uma execução genuinamente presa é baixo; o custo de derrubar uma execução legítima é mais alto. Ajustar com medição real depois.
- **[Risco] Intervalo de reconciliação de agendamentos longo demais atrasa a percepção de agendamentos novos/editados; curto demais sobrecarrega o banco com N réplicas fazendo poll.** → Mitigação: configurável via propriedade, começar com um valor conservador e ajustar com medição real.
- **[Trade-off] Reconciliação por polling (tanto de agendamentos quanto de execuções presas) é mais simples de implementar e depurar que um mecanismo de notificação, mas introduz uma janela de inconsistência não-zero entre réplicas.** Aceito porque o domínio tolera isso — nem um agendamento editado com alguns segundos de atraso, nem uma execução presa detectada um pouco depois do ideal, são problemas de correção de dado, só de latência.
- **[Risco] Testar de verdade "2 réplicas competindo pela mesma constraint" exige rodar 2 instâncias do curve-orchestrator ao mesmo tempo contra o mesmo banco** — não é replicável só com testes unitários/`@SpringBootTest` de uma instância. → Mitigação: tarefa de implementação inclui subir 2 réplicas via compose local e observar `execucao_curva` durante um disparo real.

## Migration Plan

- Nenhuma migração Flyway nova.
- Nenhuma dependência nova.
- Nenhuma mudança de contrato de API pública (REST) do curve-orchestrator — a mudança é inteiramente interna à forma como o disparo é coordenado entre réplicas.
- Rollback: reverter o `try/catch` e a periodicidade das duas reconciliações é uma mudança de código isolada, sem qualquer pendência de schema para desfazer.
