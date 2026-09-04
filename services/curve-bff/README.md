# curve-bff

Única fronteira exposta ao navegador — agrega `curve-api`, `curve-engine` e `curve-orchestrator` para o front Angular.

## Layout (arquitetura hexagonal)

```
com.poccurves.bff
├── application/        casos de uso (11 services: CatalogoService, ComparacaoService, InterpolacaoService,
│                        ModelosService, ExecucoesService, PainelDoDiaService, AlertasService, CurvaViewerService,
│                        DisparoManualService, PendenciasService, CargaManualService) + portas
│                        (CurveApiPort, CurveEnginePort, CurveOrchestratorPort)
├── adapter/in/web       controllers REST (tradutores finos, incl. GlobalExceptionHandler)
├── adapter/out/http     clientes HTTP implementando as portas
├── config/              wiring Spring (UseCaseConfig)
└── dto/                 contratos HTTP (BffDtos) — application pode depender deste pacote
```

Módulo sem banco/Kafka por desenho — não há pacote `domain/` porque não há entidade própria a modelar (o BFF só agrega e traduz).

`CargaManualService.carregarCurva` recebe `byte[]`/`String` (nunca `MultipartFile`) — a tradução do protocolo HTTP para bytes fica no controller.

## ArchitectureTest

```bash
mvn -pl services/curve-bff -am test -Dtest=ArchitectureTest
```
