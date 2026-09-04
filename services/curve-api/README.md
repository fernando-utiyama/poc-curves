# curve-api

Cadastro de definição de curva e consulta do que já foi publicado.

## Layout (arquitetura hexagonal)

```
com.poccurves.api
├── domain/            entidades e regras puras (DefinicaoCurva, VersaoDefinicaoCurva, ModeloCargaService, ComparadorCurvas...)
├── application/        casos de uso (DefinicaoCurvaService, CurvaConsultaService, DefinicaoCoerenciaValidator) + portas
│                        (DefinicaoCurvaRepositoryPort, VersaoDefinicaoCurvaRepositoryPort, ModeloCurvaRepositoryPort,
│                        CurvaConsultaRepositoryPort, JsonPort)
├── adapter/in/web      controllers REST (tradutores finos)
├── adapter/out/persistence  repositórios JDBC implementando as portas
├── adapter/out/json    JacksonJsonAdapter implementando JsonPort
├── config/             wiring Spring (UseCaseConfig) e configs de infraestrutura
└── dto/                contratos HTTP (ApiDtos) — application pode depender deste pacote
```

`@Transactional` em `application` (`DefinicaoCurvaService`) é uma exceção deliberada: demarcação de transação é tratada como concern de caso de uso, não de infraestrutura.

## ArchitectureTest

```bash
mvn -pl services/curve-api -am test -Dtest=ArchitectureTest
```

Reprova o build se qualquer classe em `domain`/`application` importar `org.springframework.stereotype..`, `.web..`, `.jdbc..`, `.data..`, `.kafka..`, `.context.annotation..`, `.boot..`, `jakarta.persistence..`, `org.apache.kafka..`, ou uma classe de serviço Jackson (`ObjectMapper`/`JsonMapper`/`TypeReference`).
