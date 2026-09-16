## 1. Coordenação de disparo via constraint existente (zero tabelas novas)

- [x] 1.1 `DisparoAgendadoExecutor.executar` envolve `execucaoCurvaRepository.inserir(execucao)` num try/catch para `org.springframework.dao.DuplicateKeyException` — loga em nível INFO nomeando alvo/data/momento e retorna sem propagar. Confirmado que `org.springframework.dao..` não está na lista de pacotes banidos do `ArchitectureTest` para `application/` (rodei o teste depois da mudança, não presumi). Teste unitário cobre o cenário (constraint simulada não propaga exceção, `vincularExecucao`/`acionarFeederEEncadear` não são chamados).
- [ ] 1.2 **Parcial, não fechada**: a cobertura hoje é unitária (mock de `DuplicateKeyException`), não um teste de integração com duas chamadas concorrentes de verdade contra o mesmo banco. Confirma o tratamento da exceção, mas não a condição de corrida real (duas threads/processos inserindo ao mesmo tempo) — fica para a tarefa 4 (verificação com réplicas reais), que é o ambiente onde a corrida de verdade pode ser observada.

## 2. Reconciliação periódica de execuções presas (reaproveita ReconciliacaoService)

- [x] 2.1 `ExecucaoCurvaRepositoryPort.buscarExecucoesEmAndamento` passa a exigir `Instant iniciadoAntesDe`; a query em `ExecucaoCurvaRepository` ganha `AND iniciado_em < ?`. Sem coluna nova.
- [x] 2.2 `ReconciliacaoService.reconciliarExecucoesPresas(Duration limiteAntiguidade)` calcula `Instant.now().minus(limiteAntiguidade)` e usa o filtro. Teste novo cobre o cálculo do instante-limite; os testes existentes (marca pendente/executando como falha) continuam cobrindo o comportamento de reconciliação em si.
- [x] 2.3 Novo `ReconciliacaoExecucoesPresasScheduler` (`@Scheduled`, `adapter/in/scheduling`) chama o mesmo serviço periodicamente; `ReconciliacaoInicializacaoRunner` mantido intacto no boot, agora também parametrizado pelo mesmo limite.
- [x] 2.4 Default definido como constante simples e conservadora (`agendamento.execucao-presa.limite-minutos`, default 60 min) — não uma junção por agendamento, como já simplificado no design.md. Documentado em `application.yml` com comentário explicando o porquê.

## 3. Reconciliação periódica do catálogo de agendamentos

- [x] 3.1 `AgendamentoSchedulerRegistry.reconciliar(List<Agendamento>)` implementado — passou a guardar `expressaoHorario`/`fusoHorario` junto com o `ScheduledFuture` (record `RegistroAtivo`) para poder comparar sem estado externo. Teste novo cobre os três casos (novo, removido, alterado) numa única chamada.
- [x] 3.2 Novo `AgendamentoReconciliacaoScheduler` (`@Scheduled`), intervalo configurável via `agendamento.reconciliacao.intervalo-segundos` (default 30s).
- [x] 3.3 `AgendamentoBootstrap` chama `ReconciliacaoAgendamentosService.reconciliar()` em vez de `registrar` um a um — mesmo código do boot e da reconciliação periódica, sem duplicação.
- [x] 3.4 `AgendamentoService.cadastrar/editar/ativar/desativar` não dependem mais de `AgendamentoSchedulerPort` — só escrevem no banco. `UseCaseConfig` ganhou o bean `ReconciliacaoAgendamentosService` para a convergência.

77/77 testes de `curve-orchestrator` verdes (reator inteiro também verde), `ArchitectureTest` confirmando que nenhuma classe nova violou a fronteira hexagonal.

## 4. Verificação com múltiplas réplicas reais

- [ ] 4.1 Configurar o compose local para subir 2 réplicas de `curve-orchestrator` contra o mesmo SQL Server (`deploy.replicas` do Podman Compose, ou dois serviços nomeados apontando para a mesma imagem — o que o Podman Compose suportar de verdade; confirmar qual funciona antes de assumir).
- [ ] 4.2 Rodar um agendamento real de ponta a ponta com as 2 réplicas de pé e confirmar, por `execucao_curva`, que só uma execução foi criada e só uma chamada ao feeder ocorreu — **fecha a lacuna deixada pela tarefa 1.2** (verificação real de corrida, não só unitária).
- [ ] 4.3 Criar, editar e desativar um agendamento via a API de uma réplica e confirmar (logs ou consulta) que a outra réplica converge dentro do intervalo de reconciliação configurado, sem reiniciar.
- [ ] 4.4 Simular uma réplica caindo no meio de uma execução (matar o processo com uma `ExecucaoCurva` em `EXECUTANDO`) e confirmar que a outra réplica reconcilia depois do limite de tempo configurado, sem esperar reinício.

## 5. Documentação

- [ ] 5.1 Atualizar o capability `curve-schedule-registry` (`openspec/changes/curve-orchestrator/specs/`) para refletir o novo comportamento de "Durabilidade dos agendamentos" sob múltiplas réplicas (ou deixar nota apontando para este capability novo, se aquele já estiver arquivado).
- [ ] 5.2 Documentar em `services/curve-orchestrator/README.md` os dois intervalos configuráveis novos (reconciliação de agendamentos, limite de execução presa) e por que não há tabela nova.
