# curve-orchestrator

Agenda, dispara, faz backfill e acompanha execuções de curva; materializa pendências de dead-letter.

## Layout (arquitetura hexagonal)

```
com.poccurves.orchestrator
├── domain/              entidades e regras puras (Agendamento, ExecucaoCurva, PendenciaDlq, IntegracaoIndisponivelException,
│                         CalendarioPregao (calendário de pregão B3/ANBIMA — cópia própria do módulo, não uma lib
│                         compartilhada; ver nota abaixo), + records promovidos de repositório: DefinicaoConsumidora,
│                         ProgressoBackfill, LinhaExecucaoResumo, PaginaExecucoes, AgendamentoComUltimaExecucao)
├── application/          casos de uso (AgendamentoService, DisparoAgendadoExecutor, BackfillService/Dispatcher,
│                         CargaManualService, DisparoManualService, AquisicaoExecutionService, ExecucoesService,
│                         MaterializarPendenciaDlqUseCase, ReconciliacaoService) + portas (7 *Port)
├── adapter/in/web         5 controllers REST
├── adapter/in/messaging   DlqMaterializacaoListener (tradutor fino)
├── adapter/in/bootstrap   AgendamentoBootstrap, ReconciliacaoInicializacaoRunner
├── adapter/out/persistence  6 repositórios JDBC
├── adapter/out/messaging    BuildRequestPublisher
├── adapter/out/http          FunctionMarketdataClient, CurveProcessorCargaManualClient
├── adapter/out/scheduling    AgendamentoSchedulerRegistry
├── adapter/out/metrics        ExecucaoMetricsBinder
├── config/                wiring Spring (UseCaseConfig) e configs de infraestrutura
└── dto/                   contratos HTTP (OrchestratorDtos) — application pode depender deste pacote
```

Maior módulo do monorepo. `Agendamento.criar` usa `org.springframework.scheduling.support.CronExpression.parse(...)`
só para validar sintaxe de expressão cron — exceção deliberada, sem wiring de infraestrutura por trás (mesmo espírito
da exceção de `@Transactional` nos outros módulos). Falhas de transporte HTTP (feeder, curve-processor) são traduzidas
para `IntegracaoIndisponivelException` nos adaptadores, para que a lógica de retentativa em `application` não dependa
de `org.springframework.web.client.RestClientException`.

Não existe mais um módulo `libs/curve-kernel` compartilhado. `CalendarioPregao` é uma cópia própria deste módulo
(decisão deliberada: curve-orchestrator só precisa de dias de pregão, não de matemática de curva) — o restante do
antigo kernel (interpoladores, `CurvaJuros`/`Vertice`, bootstrapper) migrou para dentro de `curve-engine/domain`,
único consumidor real dessa parte.

## ArchitectureTest

```bash
mvn -pl services/curve-orchestrator -am test -Dtest=ArchitectureTest
```
