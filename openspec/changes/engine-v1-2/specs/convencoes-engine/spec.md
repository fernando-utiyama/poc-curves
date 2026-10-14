## ADDED Requirements

### Requirement: Hexagonal de verdade
O código do engine SHALL respeitar as camadas: `domain` e `application` MUST NOT importar nada de `adapter` (nem classes de persistência, de REST ou de Kafka), nem tipos de `org.springframework.data`, `jakarta.persistence` ou `com.fasterxml.jackson.databind`/`tools.jackson` (as anotações de Jackson e as de Spring `@Service` e `@Transactional` são aceitas). Um `controller` SHALL chamar um **use case** (interface em `application/port/in`), e MUST NOT chamar um service concreto nem importar tipo de `application/port/out`. Um `service` SHALL depender só de portas (`application/port/out`) e de tipos de `domain`. Cada tipo de resposta que o controller devolve SHALL ser de `domain` ou um DTO do próprio `adapter/in`.

#### Scenario: Controller chama service concreto
- **WHEN** um controller importa `application.service.ConstruirCurvaService` em vez do use case
- **THEN** a conferência registra **alterar**: criar a interface em `application/port/in` e trocar o import

#### Scenario: Service importa adaptador
- **WHEN** um service importa uma classe de `adapter.out` ou `adapter.in`
- **THEN** a conferência registra **alterar**: usar a porta de saída correspondente

### Requirement: Tipos de domínio fortes
Dentro de `domain`, das portas e dos use cases, nada de `Map<String, Object>`, `Object[]`, `Object` genérico ou `String` com JSON dentro. Valores fechados SHALL ser `enum`, dados SHALL ser `record` e variantes SHALL ser `sealed interface` com records. JSON cru SHALL ficar só na borda (controller, cliente HTTP e a coluna `cModDado`).

#### Scenario: Parâmetros em mapa
- **WHEN** uma porta recebe `Map<String, Object>` para os parâmetros de cálculo
- **THEN** a conferência registra **alterar**: usar o record de parâmetros que o cadastro já tem

### Requirement: Um tipo por arquivo, sem record dentro de interface
Records de dados SHALL ficar em arquivo próprio, em `domain` (ou no `adapter/in` quando forem DTO). Um record MUST NOT ser declarado dentro de um use case ou de uma porta (as variantes de uma `sealed interface` de domínio, como `ResultadoConstrucao`, são a exceção). Um tipo de resposta MUST NOT existir em duas cópias iguais (por exemplo, um record na porta de saída e outro no use case com os mesmos campos).

#### Scenario: Record aninhado na porta
- **WHEN** a porta `EnginePort`-like declara `record RespostaX(int status, String corpo)` dentro de si e o use case declara outro igual
- **THEN** a conferência registra **alterar**: um único record em `domain`, usado pelos dois

### Requirement: Nomes e comentários
Nomes SHALL dizer o que a coisa faz e MUST NOT usar "canônico". Métodos privados de uma linha que só envolvem outro método (sem regra, usados uma vez) MUST NOT existir. O javadoc SHALL ter uma linha e dizer só o que a coisa faz; a explicação vai no guia de implementação.

#### Scenario: Javadoc longo
- **WHEN** um método tem javadoc de três linhas explicando o motivo
- **THEN** a conferência registra **alterar**: uma linha, e o motivo vai para o guia

### Requirement: Jackson 3 e parâmetros em objeto
O mapper injetado SHALL ser o de `tools.jackson.databind`, e MUST NOT haver `com.fasterxml.jackson.databind` no código. Um controller SHALL receber, com mais de dois parâmetros de consulta ou de corpo, um record (`@ModelAttribute` no `GET` e `@RequestBody` no `POST`/`PUT`), deixando `@PathVariable` separado.

#### Scenario: Fila de `@RequestParam`
- **WHEN** um controller tem quatro `@RequestParam`
- **THEN** a conferência registra **alterar**: agrupar num record

### Requirement: Tempo sem constante de fuso
O fuso SHALL ser fixado uma vez (`TimeZoneConfig`/`main`, `America/Sao_Paulo`, com recusa de subida em outro fuso), e o código MUST NOT ter constante `ZoneId` própria: `LocalDate.now()` e `OffsetDateTime.now()` sem argumento.

#### Scenario: Constante de zona
- **WHEN** um service declara `ZoneId.of("America/Sao_Paulo")` para chamar `now(...)`
- **THEN** a conferência registra **alterar**: `now()` sem zona e a constante sai

### Requirement: Swagger completo e erro com mensagem inteira
Toda rota do engine SHALL ter `@Operation` (resumo e descrição), `@Parameter` com descrição e exemplo nos parâmetros de caminho e de consulta, `@ApiResponses` com os erros esperados da classe (`404`, `422`, `409`, `503` onde existirem) e `produces` JSON. Os DTOs de entrada e de resposta SHALL ter `@Schema` com descrição e exemplo por campo. Cada recusa SHALL ter a mensagem completa e explicada (o front só mostra o texto).

#### Scenario: Rota sem documentação
- **WHEN** uma rota tem só `@GetMapping`
- **THEN** a conferência registra **alterar**: `@Operation`, `@Parameter` e `@ApiResponses`

### Requirement: Testes que testam regra
Cada service e cada modelo de construção SHALL ter teste de regra com valor de referência. Um teste de adaptador de persistência que só confere delegação ao repositório ou a cópia de campos MUST NOT existir (a persistência é conferida pelas listagens e pelo teste manual). Testes de controller (`@WebMvcTest`) SHALL conferir o JSON e o status.

#### Scenario: Teste de adaptador espelho
- **WHEN** existe um `...PersistenceAdapterTest` que só mocka o repositório e confere o `toDomain`
- **THEN** a conferência registra **alterar**: apagar o teste

### Requirement: Sem infraestrutura nova
Esta change MUST NOT criar tópico Kafka, DLQ, tabela, coluna, bean de cache ou dependência nova; reaproveita o que o engine já tem.

#### Scenario: Tópico novo
- **WHEN** uma correção parece pedir um tópico
- **THEN** ela é descartada e registrada como pendência para quem decide a infra
