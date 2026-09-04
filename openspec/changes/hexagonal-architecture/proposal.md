## Why

Hoje os 6 módulos Java do monorepo (`common`, `curve-api`, `curve-bff`, `curve-engine`,
`curve-orchestrator`, `curve-processor`) são organizados por pacote-por-feature: um pacote
por funcionalidade (`construcao/`, `agendamento/`, `validacao/` etc.) onde `@Service` Spring,
listener Kafka, repositório JDBC e regra de negócio convivem na mesma classe. Não há fronteira
explícita entre regra de domínio e infraestrutura — o projeto já sente falta disso pontualmente
(ex.: `curve-bff` usa um enforcer-plugin ad-hoc só para banir dependência de banco/Kafka, uma
fronteira aplicada por exceção em vez de por desenho). Isso dificulta testar regra de negócio
isolada de framework, trocar um adaptador de infraestrutura sem tocar a regra que ele serve, e
aplicar de forma sistemática a mesma disciplina de fronteira que o projeto já valoriza.

Agora é um bom momento: a atualização para Spring Boot 4.0.8 (já implementada e verificada nesta
sessão — `mvn compile`/`test-compile`/`test` com 491 testes verdes e `mvn package` limpo) acabou
de tocar a camada de wiring (Jackson 3, `RestClient`, anotações de teste) de quase todo módulo,
então o mapa de dependências de framework está fresco. Fazer a reestruturação agora evita
reescrever a mesma lógica duas vezes conforme o backlog pendente de `curve-engine` (~59%) e
`curve-orchestrator` (~46%) avança.

## What Changes

- Introduzir layout hexagonal (ports & adapters) em cada um dos 6 módulos Java:
  - `domain/`: entidades, objetos de valor e regra de negócio pura — sem import de Spring, JPA/JDBC,
    Kafka, Jackson ou qualquer tipo de framework.
  - `application/`: casos de uso que orquestram o domínio e declaram as portas (interfaces) de que
    precisam (ex.: `VersaoCurvaRepositoryPort`, `CurvaPublicadaEventPort`, `FeederAcquisitionPort`).
  - `adapter/in/{web,messaging}/`: controllers REST e listeners Kafka — tradutores finos entre o
    mundo externo e os casos de uso, sem regra de negócio.
  - `adapter/out/{persistence,messaging,http,cache}/`: implementações concretas das portas —
    repositório JDBC, produtor Kafka, cliente `RestClient`, cache Redis.
- Mover a regra de negócio hoje misturada em classes `@Service` (bootstrap e construção de curva,
  bateria de validação, agendamento e backfill, materialização e reprocessamento de dead-letter
  etc.) para classes de domínio/aplicação sem anotação de framework; as classes `@Service`
  remanescentes viram adaptadores finos de orquestração.
- Portas explícitas como interfaces pertencentes ao domínio/aplicação; adaptadores em `adapter/out`
  implementam essas interfaces — nunca o inverso.
- Introduzir enforcement estrutural por módulo (ex.: teste ArchUnit) que reprova o build se
  `domain`/`application` importar tipo de framework, tornando a fronteira uma regra verificada,
  não uma convenção de nome de pacote.
- **BREAKING (somente interno)**: pacotes reorganizados nos 6 módulos. Fora de escopo: contrato de
  API HTTP, contrato de evento Kafka, schema de banco — nenhum desses muda. O comportamento externo
  observável deve permanecer idêntico, verificado pela suíte de testes existente (491 testes)
  continuando verde a cada módulo migrado.
- Registra como contexto/baseline (sem trabalho novo) a atualização já concluída para Spring Boot
  4.0.8 nesta sessão (Jackson 3 → `tools.jackson.*`, Testcontainers 2.0 com artefatos
  `testcontainers-*`, `com.networknt:json-schema-validator` 3.0.7, `spring-boot-starter-restclient`,
  `spring-boot-webmvc-test`) — é o estado de partida sobre o qual esta migração acontece.

## Capabilities

### New Capabilities
- `architecture/hexagonal-design`: a convenção de fronteira ports & adapters que os serviços Java
  deste monorepo devem seguir — nomes de camada, direção de dependência permitida, o que pertence a
  domínio vs. adaptador, e como isso é verificado no build.

### Modified Capabilities
<!-- Nenhuma: openspec/specs/ ainda não tem nenhuma capability arquivada neste repositório. -->

## Impact

- Reorganização de pacote em todos os 6 módulos Java (contagem real de arquivos-fonte hoje:
  `curve-processor` 93, `curve-engine` 74, `curve-orchestrator` 67, `curve-bff` 37, `curve-api` 30,
  `common` 7 — `libs/curve-kernel` fica de fora, já é uma biblioteca pura sem framework).
- Nenhuma mudança de contrato de API HTTP, de evento Kafka ou de schema de banco (migração Flyway).
- Build: novo mecanismo de enforcement estrutural (ArchUnit ou equivalente) por módulo.
- Suíte de testes existente (491 testes, todos verdes hoje) é o oráculo de não-regressão de
  comportamento durante a migração.
