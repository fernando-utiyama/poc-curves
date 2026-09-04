## Purpose

Define a convenção de fronteira ports & adapters que todo serviço Java deste monorepo deve
seguir, e como essa fronteira é verificada de forma automática no build — para que regra de
negócio permaneça isolável de framework e testável sem infraestrutura.

## ADDED Requirements

### Requirement: Isolamento da camada de domínio e aplicação
As camadas `domain` e `application` de cada módulo Java (`common`, `curve-api`, `curve-bff`,
`curve-engine`, `curve-orchestrator`, `curve-processor`) SHALL NOT importar tipo de nenhum
framework de infraestrutura (Spring MVC/web, Spring Data/JDBC, Kafka, Jackson, Redis) ou de
protocolo externo (HTTP servlet, driver JDBC). Regra de negócio SHALL residir exclusivamente
nessas camadas. Exceção deliberada: `org.springframework.transaction..` (demarcação de
transação, ex. `@Transactional`) é permitido em `application`, por ser tratado como concern de
caso de uso, não de infraestrutura — decisão registrada em `design.md`.

#### Scenario: Classe de domínio importa tipo de framework
- **WHEN** uma classe em `domain` ou `application` de qualquer módulo importa um tipo de
  `org.springframework.stereotype.*`, `org.springframework.web.*`, `org.springframework.jdbc.*`,
  `org.springframework.data.*`, `org.springframework.kafka.*`,
  `org.springframework.context.annotation.*`, `jakarta.persistence.*`, `org.apache.kafka.*`,
  `tools.jackson.*` ou driver JDBC
- **THEN** o build do módulo falha, com o nome da classe e do tipo importado na mensagem

#### Scenario: Demarcação de transação é permitida em application
- **WHEN** um caso de uso em `application` usa `org.springframework.transaction.annotation.Transactional`
  para demarcar a fronteira transacional do próprio caso de uso
- **THEN** o build do módulo não reprova por causa desse import específico

#### Scenario: Regra de negócio pura passa sem infraestrutura
- **WHEN** um caso de uso em `application` é testado diretamente com objetos de domínio em
  memória, sem Spring context, sem banco, sem Kafka
- **THEN** o teste roda e produz o mesmo resultado que rodaria com a infraestrutura real conectada

### Requirement: Toda dependência externa é acessada por porta
Cada dependência externa (banco, mensageria, cache, chamada HTTP a outro serviço) usada por um
caso de uso SHALL ser declarada como uma interface de porta pertencente a `domain` ou
`application`, nunca ao adaptador. O caso de uso SHALL depender apenas da interface, nunca de uma
implementação concreta de adaptador.

#### Scenario: Caso de uso depende de porta, não de implementação
- **WHEN** um caso de uso em `application` precisa persistir ou publicar um evento
- **THEN** ele recebe/injeta uma interface declarada em `domain` ou `application` (ex.:
  `VersaoCurvaRepositoryPort`), nunca uma classe concreta de `adapter/out`

#### Scenario: Adaptador implementa a porta, não o inverso
- **WHEN** uma classe em `adapter/out/persistence`, `adapter/out/messaging`, `adapter/out/http`
  ou `adapter/out/cache` é criada
- **THEN** ela implementa uma interface de porta já existente em `domain`/`application` — nenhuma
  porta é declarada dentro de um pacote `adapter`

### Requirement: Adaptadores são finos
Adaptadores de entrada (`adapter/in/web`, `adapter/in/messaging`) e de saída (`adapter/out/*`)
SHALL conter apenas tradução entre o protocolo/tecnologia externa e o caso de uso — sem regra de
negócio, sem decisão que altere o resultado do domínio.

#### Scenario: Controller REST delega sem decidir
- **WHEN** uma requisição HTTP chega a um controller em `adapter/in/web`
- **THEN** o controller apenas traduz a requisição para a chamada do caso de uso correspondente e
  traduz o resultado de volta para a resposta HTTP, sem aplicar regra de negócio própria

#### Scenario: Listener Kafka delega sem decidir
- **WHEN** uma mensagem chega a um listener em `adapter/in/messaging`
- **THEN** o listener apenas desserializa o envelope e chama o caso de uso correspondente, sem
  aplicar regra de negócio própria

### Requirement: Migração preserva contrato externo
A migração de um módulo para o layout hexagonal SHALL NOT alterar contrato de API HTTP, contrato
de evento Kafka ou schema de banco (migração Flyway) desse módulo. O comportamento externo
observável SHALL permanecer idêntico ao estado anterior à migração.

#### Scenario: Suíte de testes existente continua verde
- **WHEN** um módulo é migrado para o layout hexagonal
- **THEN** a suíte de testes existente desse módulo (unitários e de integração) continua passando
  sem alteração de asserção, apenas de estrutura interna do código de produção

#### Scenario: Contrato de API e evento inalterados
- **WHEN** um módulo migrado é comparado ao seu contrato OpenAPI/schema de evento anterior à
  migração
- **THEN** nenhum campo, endpoint, tópico ou schema de evento foi adicionado, removido ou
  renomeado como efeito da migração
