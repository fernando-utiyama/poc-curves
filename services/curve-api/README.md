# curve-api

Consulta somente-leitura do catálogo de curvas de mercado (`tCurvaMercd`) e dos vértices/curva
construídos pelo curve-engine (`tDadoCurva`/`tCurvaData`) — schema legado (db/migration/V22,
grants em V28). As 5 curvas TS B3 (PRE/DCL/PTX/INP/DPL) são hoje as únicas curvas reais no
projeto; o domínio antigo (DefinicaoCurva/VersaoDefinicaoCurva com ciclo de vida
RASCUNHO/ATIVA/APOSENTADA, versionamento, procedência, modelo de carga CSV/XLSX) não tem
equivalente no schema novo e foi removido por completo — não adaptado.

## Layout (arquitetura hexagonal)

```
com.poccurves.api
├── domain/            entidades puras, sem estado de ciclo de vida (CurvaMercado, PontoCurva)
├── application/        casos de uso (CurvaMercadoService, CurvaDadosService) + portas
│                        (CurvaMercadoRepositoryPort, CurvaDadosRepositoryPort)
├── adapter/in/web      controllers REST (CurvaMercadoController — tradutor fino)
├── adapter/out/persistence  repositórios JDBC implementando as portas (só leitura)
├── config/             wiring Spring (UseCaseConfig) e configs de infraestrutura
└── dto/                contratos HTTP (ApiDtos) — application pode depender deste pacote
```

Sem rota de escrita: novas curvas entram por migração + pipeline de aquisição/construção, nunca
por esta API.

## ArchitectureTest

```bash
mvn -pl services/curve-api -am test -Dtest=ArchitectureTest
```

Reprova o build se qualquer classe em `domain`/`application` importar `org.springframework.stereotype..`, `.web..`, `.jdbc..`, `.data..`, `.kafka..`, `.context.annotation..`, `.boot..`, `jakarta.persistence..`, `org.apache.kafka..`, ou uma classe de serviço Jackson (`ObjectMapper`/`JsonMapper`/`TypeReference`).
