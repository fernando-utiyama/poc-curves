# curve-orchestrator

Agenda, dispara, faz backfill e acompanha execuções de curva; materializa pendências de dead-letter.

## Layout (arquitetura hexagonal)

```
com.poccurves.orchestrator
├── application/
│   ├── model/            entidades e regras puras (Agendamento, ExecucaoCurva, PendenciaDlq, CalendarioPregao
│   │                     (calendário de pregão B3/ANBIMA — cópia própria do módulo, não uma lib compartilhada;
│   │                     ver nota abaixo), + records promovidos de repositório: DefinicaoConsumidora,
│   │                     ProgressoBackfill, LinhaExecucaoResumo, PaginaExecucoes, AgendamentoComUltimaExecucao)
│   ├── exception/        IntegracaoIndisponivelException
│   ├── port/              10 *Port
│   ├── service/            orquestração interna (AquisicaoExecutionService, BackfillDispatcher)
│   └── usecase/            ponto de entrada por controller/listener/scheduler (AgendamentoService,
│                          BackfillService, CargaManualService, DisparoAgendadoExecutor, DisparoManualService,
│                          ExecucoesService, MaterializarPendenciaDlqUseCase, ReconciliacaoService)
├── adapter/in/web         6 controllers REST
├── adapter/in/messaging   DlqMaterializacaoListener (tradutor fino)
├── adapter/in/bootstrap   AgendamentoBootstrap, ReconciliacaoInicializacaoRunner
├── adapter/out/persistence  6 repositórios JDBC
├── adapter/out/http          CurveEngineClient, FunctionMarketdataClient, CurveProcessorCargaManualClient
├── adapter/out/scheduling    AgendamentoSchedulerRegistry
├── adapter/out/metrics        ExecucaoMetricsBinder
├── config/                wiring Spring (UseCaseConfig) e configs de infraestrutura
└── dto/                   contratos HTTP (OrchestratorDtos) — application pode depender deste pacote
```

Layout espelha o padrão hex real usado em outro projeto (`adapter`/`application`/`util`, com `application` subdividido
em `model`/`service`/`usecase`/`port`/`exception`), revisado nesta sessão a pedido do usuário. `ArchitectureTest`
continua banindo import de framework em qualquer subpacote de `application` (o padrão `..application..` já cobre
todos os subpacotes novos).

Maior módulo do monorepo. `Agendamento.criar` usa `org.springframework.scheduling.support.CronExpression.parse(...)`
só para validar sintaxe de expressão cron — exceção deliberada, sem wiring de infraestrutura por trás (mesmo espírito
da exceção de `@Transactional` nos outros módulos). Falhas de transporte HTTP (function-marketdata, curve-processor)
são traduzidas para `IntegracaoIndisponivelException` nos adaptadores, para que a lógica de retentativa em
`application` não dependa de `org.springframework.web.client.RestClientException`.

Não existe mais um módulo `libs/curve-kernel` compartilhado. `CalendarioPregao` é uma cópia própria deste módulo
(decisão deliberada: curve-orchestrator só precisa de dias de pregão, não de matemática de curva) — o restante do
antigo kernel (interpoladores, `CurvaJuros`/`Vertice`, bootstrapper) migrou para dentro de `curve-engine/application/model`,
único consumidor real dessa parte.

## ArchitectureTest

```bash
mvn -pl services/curve-orchestrator -am test -Dtest=ArchitectureTest
```
