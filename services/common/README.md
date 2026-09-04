# common

Biblioteca pura compartilhada por todos os serviços Java: o envelope de evento padronizado
(`EventEnvelope`, espelhando `contracts/events/envelope.schema.json`) e seu validador de JSON Schema.

## Layout

```
com.poccurves.common.event
├── EventEnvelope.java                  record + invariantes de construção
├── EventEnvelopeSchemaValidator.java    valida um envelope contra o contrato JSON Schema
├── EventSource.java / PayloadKind.java  enums do envelope
```

Sem separação `domain`/`application`/`adapter`: o módulo já é uma biblioteca pura, sem framework nem I/O, consumida
como um todo pelos outros módulos — não há infraestrutura a isolar aqui. Jackson (`JsonNode`, `@JsonInclude`) e
`com.networknt.schema` são a própria razão de existir do módulo, não infraestrutura de framework.

## ArchitectureTest

```bash
mvn -pl services/common -am test -Dtest=ArchitectureTest
```

Reprova o build se qualquer classe do módulo (não só um subpacote — o módulo inteiro) passar a depender de
`org.springframework..`: isso obrigaria todo módulo consumidor a herdar Spring transitivamente só por depender de `common`.
