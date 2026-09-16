## 1. Coordenação de disparo via constraint existente (zero tabelas novas)

- [ ] 1.1 Em `DisparoAgendadoExecutor.executar`, envolver `execucaoCurvaRepository.inserir(execucao)` num tratamento que captura especificamente a violação da constraint única de `execucao_curva` (V14) — não uma exceção genérica — loga em nível INFO ("disparo já assumido por outra réplica", nomeando alvo/data/momento) e retorna sem propagar. Verificar com teste unitário que uma violação de constraint simulada é tratada sem lançar exceção, e que qualquer outra exceção de banco continua propagando normalmente.
- [ ] 1.2 Verificar com teste de integração (duas chamadas concorrentes a `executar` para o mesmo agendamento/data, contra o mesmo banco de teste) que exatamente uma `ExecucaoCurva` é criada e exatamente uma chamada a `acionarFeederEEncadear` ocorre (cenários "Duas réplicas disparam o mesmo cron ao mesmo tempo" e "Réplica perdedora não gera erro visível" da spec).

## 2. Reconciliação periódica de execuções presas (reaproveita ReconciliacaoService)

- [ ] 2.1 Adicionar a `ExecucaoCurvaRepositoryPort`/`buscarExecucoesEmAndamento` (ou um método novo) um filtro por tempo decorrido desde `iniciado_em`, parametrizado por um limite configurável — sem coluna nova, usando `iniciado_em` que já existe.
- [ ] 2.2 Ajustar `ReconciliacaoService.reconciliarExecucoesPresas()` para usar esse filtro por tempo, em vez de considerar "presa" qualquer execução não-terminal — verificar com teste unitário os dois cenários da spec ("Réplica cai no meio do processamento" e "Execução legítima não é derrubada prematuramente").
- [ ] 2.3 Agendar a chamada periódica de `reconciliarExecucoesPresas()` (reaproveitando o `TaskScheduler` já existente ou um `@Scheduled` simples), mantendo a chamada existente no boot (`ReconciliacaoInicializacaoRunner`) intacta.
- [ ] 2.4 Definir o valor default do limite de "presa de verdade" (maior janela de tentativa configurada entre os agendamentos ativos, mais margem — valor simples, não uma junção por agendamento) e documentá-lo.

## 3. Reconciliação periódica do catálogo de agendamentos

- [ ] 3.1 Implementar o método de reconciliação em `AgendamentoSchedulerRegistry` (ou classe nova dedicada): lê `agendamentoRepository.listarAtivos()`, compara com o `ConcurrentHashMap` local, registra o que é novo, cancela o que sumiu do resultado, reagenda o que mudou `expressaoHorario`/`fusoHorario` (comparando os campos, não recriando sempre) — verificar com teste unitário os três casos (novo, removido, alterado) isoladamente.
- [ ] 3.2 Agendar essa reconciliação para rodar em intervalo curto e configurável (propriedade nova, ex. `agendamento.reconciliacao.intervalo-segundos`, default a definir).
- [ ] 3.3 Fazer `AgendamentoBootstrap` chamar essa mesma reconciliação (em vez de `registrar` um a um) — comportamento de boot preservado, código não duplicado.
- [ ] 3.4 Remover a chamada direta a `schedulerRegistry` de `AgendamentoService.cadastrar/editar/ativar/desativar` — esses métodos passam a só escrever no banco; verificar com teste que a convergência acontece só via reconciliação (cenários "Agendamento criado em uma réplica aparece nas demais", "Edição de horário propaga", "Desativação para o disparo" da spec).

## 4. Verificação com múltiplas réplicas reais

- [ ] 4.1 Configurar o compose local para subir 2 réplicas de `curve-orchestrator` contra o mesmo SQL Server (`deploy.replicas` do Podman Compose, ou dois serviços nomeados apontando para a mesma imagem — o que o Podman Compose suportar de verdade; confirmar qual funciona antes de assumir).
- [ ] 4.2 Rodar um agendamento real de ponta a ponta com as 2 réplicas de pé e confirmar, por `execucao_curva`, que só uma execução foi criada e só uma chamada ao feeder ocorreu.
- [ ] 4.3 Criar, editar e desativar um agendamento via a API de uma réplica e confirmar (logs ou consulta) que a outra réplica converge dentro do intervalo de reconciliação configurado, sem reiniciar.
- [ ] 4.4 Simular uma réplica caindo no meio de uma execução (matar o processo com uma `ExecucaoCurva` em `EXECUTANDO`) e confirmar que a outra réplica reconcilia depois do limite de tempo configurado, sem esperar reinício.

## 5. Documentação

- [ ] 5.1 Atualizar o capability `curve-schedule-registry` (`openspec/changes/curve-orchestrator/specs/`) para refletir o novo comportamento de "Durabilidade dos agendamentos" sob múltiplas réplicas (ou deixar nota apontando para este capability novo, se aquele já estiver arquivado).
- [ ] 5.2 Documentar em `services/curve-orchestrator/README.md` os dois intervalos configuráveis novos (reconciliação de agendamentos, limite de execução presa) e por que não há tabela nova.
