# curve-processor

Normaliza e persiste dado de mercado ingerido; publica curvas importadas e carregadas.

## Layout (arquitetura hexagonal)

```
com.poccurves.processor
├── application/
│   ├── model/            entidades e regras puras — parsers B3 (B3CurvaProntaParser, B3TaxaSwapParser), leitores
│   │                     CSV/XLSX, VersaoCurva/VerticeCurva, LoteIngestao, PontoDadoMercado...
│   ├── validator/        bateria de carga manual (TesteAderenciaCurvaReferencia, TesteTaxasNaoNegativas)
│   ├── exception/        LoteJaExisteException, CurvaNaoMapeadaException, EnvelopeInvalidoException...
│   ├── port/             10 *Port (8 *RepositoryPort, CurvaPublicadaEventPort, NormalizedEventPort)
│   ├── service/          orquestração interna (BateriaValidacaoCarga)
│   ├── usecase/          ponto de entrada por controller/listener (ProcessarEnvelopeIngestaoUseCase,
│   │                     IngestaoService, PublicacaoCurvaService)
│   └── util/             concern técnico transversal, não regra de negócio (MetricasIngestao)
├── adapter/in/web         CargaManualInternoController
├── adapter/in/messaging   IngestaoListener (tradutor fino: valida envelope, desserializa, delega ao caso de uso)
├── adapter/out/persistence  8 repositórios JDBC
├── adapter/out/messaging     CurvaPublicadaEventPublisher, NormalizedEventPublisher
└── config/                wiring Spring (ParserConfig, UseCaseConfig) e configs de infraestrutura
```

Layout espelha o padrão hex real usado em outro projeto (`adapter`/`application`/`util`, com `application`
subdividido em `model`/`service`/`usecase`/`validator`/`port`/`exception`), revisado nesta sessão a pedido do
usuário, substituindo o `domain`/`application` separado que existia antes.

`EventEnvelope.payload()` (módulo `common`) é `tools.jackson.databind.JsonNode` — permitido em `application` como
estrutura de dado genérica, não como uso de infraestrutura de serialização (por isso o `ArchitectureTest` bane
Jackson por CLASSE de serviço — `ObjectMapper`/`JsonMapper`/`TypeReference` — em vez de banir o pacote inteiro).

## ArchitectureTest

```bash
mvn -pl services/curve-processor -am test -Dtest=ArchitectureTest
```
