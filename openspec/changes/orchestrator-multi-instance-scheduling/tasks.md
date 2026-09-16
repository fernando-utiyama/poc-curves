## 1. Infraestrutura (ShedLock)

- [ ] 1.1 Adicionar `shedlock-spring` e `shedlock-provider-jdbc-template` (versão 6.6.0) ao `dependencyManagement` do pom raiz e como dependência em `services/curve-orchestrator/pom.xml`, e verificar que o reator compila (`mvn -pl services/curve-orchestrator -am compile`).
- [ ] 1.2 Criar a migração Flyway da tabela `shedlock` (DDL padrão da biblioteca para SQL Server: `name VARCHAR(64) NOT NULL PRIMARY KEY, lock_until datetime2 NOT NULL, locked_at datetime2 NOT NULL, locked_by VARCHAR(255) NOT NULL`), e verificar que aplica limpa em banco local.
- [ ] 1.3 Declarar o bean `LockProvider` (`JdbcTemplateLockProvider`, `usingDbTime()`) em `SchedulingConfig` (ou uma classe de config dedicada), e verificar com teste de contexto Spring que o bean sobe sem erro.

## 2. Lock distribuído no disparo

- [ ] 2.1 Envolver o corpo de `DisparoAgendadoExecutor.executar(UUID agendamentoId)` em `LockingTaskExecutor.executeWithLock(...)`, com nome de lock derivado de `agendamentoId` + `dataReferencia` (ex.: `"disparo-agendado:" + agendamentoId + ":" + dataReferencia`), e `lockAtMostFor` derivado de `agendamento.janelaTentativaMinutos()` (mais margem) em vez de uma constante fixa (design.md D4). Verificar com teste unitário que o método só executa a lógica de negócio quando o lock é obtido.
- [ ] 2.2 Verificar com teste (dois `LockingTaskExecutor` sobre o mesmo `LockProvider`/mesma tabela, simulando duas réplicas) que uma segunda tentativa concorrente do mesmo `agendamentoId`+data retorna sem processar e sem lançar exceção (cenário "Réplica perdedora não gera erro visível" da spec).
- [ ] 2.3 Confirmar que o índice único de `execucao_curva` (V14) continua intacto e não foi removido — ele é a segunda linha de defesa, não substituída pelo lock.

## 3. Reconciliação periódica do catálogo de agendamentos

- [ ] 3.1 Implementar o método de reconciliação em `AgendamentoSchedulerRegistry` (ou classe nova dedicada): lê `agendamentoRepository.listarAtivos()`, compara com o `ConcurrentHashMap` local, registra o que é novo, cancela o que sumiu do resultado, reagenda o que mudou `expressaoHorario`/`fusoHorario` (comparando os campos, não recriando sempre) — verificar com teste unitário os três casos (novo, removido, alterado) isoladamente.
- [ ] 3.2 Agendar essa reconciliação para rodar em intervalo curto e configurável (propriedade nova, ex. `agendamento.reconciliacao.intervalo-segundos`, default a definir — ver design.md D3), reaproveitando o `TaskScheduler` já existente ou um `@Scheduled` dedicado.
- [ ] 3.3 Fazer `AgendamentoBootstrap` chamar essa mesma reconciliação (em vez de `registrar` um a um) — comportamento de boot preservado, código não duplicado.
- [ ] 3.4 Remover a chamada direta a `schedulerRegistry` de `AgendamentoService.cadastrar/editar/ativar/desativar` — esses métodos passam a só escrever no banco; verificar com teste que a convergência acontece só via reconciliação (cenários "Agendamento criado em uma réplica aparece nas demais", "Edição de horário propaga", "Desativação para o disparo" da spec).

## 4. Verificação com múltiplas réplicas reais

- [ ] 4.1 Configurar o compose local para subir 2 réplicas de `curve-orchestrator` contra o mesmo SQL Server (`deploy.replicas` do Podman Compose, ou dois serviços nomeados apontando para a mesma imagem — o que o Podman Compose suportar de verdade; confirmar qual funciona antes de assumir).
- [ ] 4.2 Rodar um agendamento real de ponta a ponta com as 2 réplicas de pé e confirmar, pela tabela `shedlock` e por `execucao_curva`, que só uma execução foi criada e só uma chamada ao feeder ocorreu.
- [ ] 4.3 Criar, editar e desativar um agendamento via a API de uma réplica e confirmar (logs ou consulta) que a outra réplica converge dentro do intervalo de reconciliação configurado, sem reiniciar.

## 5. Documentação

- [ ] 5.1 Atualizar o capability `curve-schedule-registry` (`openspec/changes/curve-orchestrator/specs/`) para refletir o novo comportamento de "Durabilidade dos agendamentos" sob múltiplas réplicas (ou deixar nota apontando para este capability novo, se aquele já estiver arquivado).
- [ ] 5.2 Documentar em `services/curve-orchestrator/README.md` a necessidade da tabela `shedlock` e o intervalo de reconciliação configurável.
