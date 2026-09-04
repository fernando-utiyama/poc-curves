# curve-processor

Normaliza e persiste dado de mercado ingerido; publica curvas importadas e carregadas.

## Layout (arquitetura hexagonal)

```
com.poccurves.processor
├── domain/              entidades e regras puras (parsers B3, carga/validação, LoteJaExisteException...)
├── application/          casos de uso (ProcessarEnvelopeIngestaoUseCase, IngestaoService, PublicacaoCurvaService,
│                         MetricasIngestao) + portas (8 *RepositoryPort, CurvaPublicadaEventPort, NormalizedEventPort)
├── adapter/in/web         CargaManualInternoController
├── adapter/in/messaging   IngestaoListener (tradutor fino: valida envelope, desserializa, delega ao caso de uso)
├── adapter/out/persistence  8 repositórios JDBC
├── adapter/out/messaging     CurvaPublicadaEventPublisher, NormalizedEventPublisher
└── config/                wiring Spring (ParserConfig, UseCaseConfig) e configs de infraestrutura
```

`EventEnvelope.payload()` (módulo `common`) é `tools.jackson.databind.JsonNode` — permitido em `application` como
estrutura de dado genérica, não como uso de infraestrutura de serialização (por isso o `ArchitectureTest` bane
Jackson por CLASSE de serviço — `ObjectMapper`/`JsonMapper`/`TypeReference` — em vez de banir o pacote inteiro).

## ArchitectureTest

```bash
mvn -pl services/curve-processor -am test -Dtest=ArchitectureTest
```
