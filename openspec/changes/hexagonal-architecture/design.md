## Context

Os 6 módulos Java hoje usam pacote-por-feature: um pacote por funcionalidade
(`construcao/`, `agendamento/`, `validacao/`, `pendencia/` etc.) com `@Service` Spring, listener
Kafka, repositório JDBC e regra de negócio na mesma classe. Ver `proposal.md` - Why para a
motivação. `libs/curve-kernel` já é uma biblioteca pura (sem Spring, sem banco, sem rede) e fica
fora do escopo desta migração. `curve-bff` já não acessa banco nem Kafka por desenho (enforcer
plugin bane essas dependências no pom) — é o módulo mais simples e menor (37 arquivos-fonte).
Os demais variam de 7 (`common`) a 93 (`curve-processor`) arquivos-fonte.

A base é Spring Boot 4.0.8 (atualizado nesta sessão) — Jackson 3, Testcontainers 2.0,
`spring-boot-starter-restclient`, `spring-boot-webmvc-test`. Essa atualização já tocou a camada de
wiring de quase todo módulo; a migração hexagonal parte desse estado, não do Spring Boot 3.4.x
mencionado no contexto padrão do projeto.

## Goals / Non-Goals

**Goals:**
- Mesmo nome de camada e mesma direção de dependência (`adapter` → `application` → `domain`,
  nunca o inverso) em todos os 6 módulos, para que a fronteira seja reconhecível entre serviços.
- Regra de negócio testável em memória, sem Spring context, sem banco, sem Kafka.
- Fronteira verificada pelo build (ArchUnit), não apenas por convenção de nome de pacote.
- Migração incremental módulo a módulo, com a suíte de testes existente (491 testes) verde a cada
  módulo concluído — nunca uma migração monorepo-wide de uma vez.

**Non-Goals:**
- Nenhuma mudança de contrato HTTP, evento Kafka ou schema de banco (Flyway).
- `libs/curve-kernel` fica fora — já é framework-free, não precisa de adaptador.
- Sem exercício de modelagem DDD tática (agregados, bounded context) além de ports/adapters.
- `function-marketdata` (Node/TS) e `curve-web-ui` (Angular) fora de escopo — só os 6 módulos Java.
- Sem split físico em módulos Maven separados por camada (domain/application em jar próprio) —
  fica registrado como questão em aberto abaixo, não decidido nesta rodada.

## Decisions

**Nomes de camada**: `domain/`, `application/`, `adapter/in/{web,messaging}/`,
`adapter/out/{persistence,messaging,http,cache}/`. Escolhido em vez do vocabulário clássico
"hexagon/core/ports" porque mapeia direto para o que já existe hoje (controller → `adapter/in/web`,
listener Kafka → `adapter/in/messaging`, repositório JDBC → `adapter/out/persistence`) e deixa
`in` vs. `out` como o eixo principal — mais legível para quem não conhece o jargão de arquitetura
hexagonal do que "driving/driven adapter".

**Enforcement por ArchUnit, não Maven enforcer**: um teste ArchUnit por módulo
(`ArchitectureTest`, rodando junto com `mvn test`) verifica que nenhuma classe em
`domain`/`application` importa tipo de `org.springframework.stereotype.*`,
`org.springframework.web.*`, `org.springframework.jdbc.*`, `org.springframework.data.*`,
`org.springframework.kafka.*`, `org.springframework.context.annotation.*`,
`jakarta.persistence.*`, `org.apache.kafka.*`, `tools.jackson.*` ou driver JDBC. Alternativa
considerada — Checkstyle import-control: rejeitada porque a API fluente do ArchUnit expressa
melhor a regra "quem pode depender de quem" no nível de pacote, e o projeto já tem cultura de
verificação via JUnit ("fixture real, sempre"). O enforcer-plugin existente em `curve-bff`
(banindo `mssql-jdbc` e `spring-kafka` como dependência inteira) continua — ArchUnit só enxerga
import em código-fonte compilado, não a árvore de dependência do Maven, então os dois mecanismos
são complementares, não substitutos um do outro.

**Exceção deliberada: `org.springframework.transaction..` permitido em `application`**. Achado
real durante a migração de `curve-api`: `DefinicaoCurvaService.criarDefinicao`/`atualizarDefinicao`
usa `@Transactional` para demarcar a transação em torno de múltiplas escritas de repositório
(definição + versão). Mover a demarcação transacional para fora do caso de uso exigiria um
adaptador extra só para isso (ex. `TransactionTemplate` envolvendo a chamada), sem ganho real de
testabilidade — o caso de uso já é testável em memória com portas mockadas, com ou sem a anotação
presente. Tratado como a mesma exceção pragmática que a literatura de arquitetura hexagonal já
reconhece: demarcação de transação alinha com fronteira de caso de uso, não é acesso a
infraestrutura. O ban list do ArchUnit ficou explícito por pacote (em vez de banir
`org.springframework..` inteiro) exatamente para permitir esse import sem abrir a porta para o
resto do Spring.

**Portas só onde há dependência externa real hoje**: uma interface de porta é criada para cada
dependência externa que um caso de uso já usa (ex.: `VersaoCurvaRepositoryPort`,
`CurvaPublicadaEventPort`, `FeederAcquisitionPort` em `curve-orchestrator`) — nunca uma porta
especulativa para algo com uma única implementação concebível (ex.: política de arredondamento
continua classe de domínio comum, não vira porta).

**Sub-pacotes de feature preservados**: a divisão em `domain`/`application`/`adapter` é a única
camada nova; os pacotes de funcionalidade que já existem (`construcao`, `agendamento`,
`validacao`, `pendencia`...) continuam como sub-pacotes dentro de `domain`/`application`/
`adapter/*` — mantém o diff de cada módulo revisável e evita misturar renomeação de feature com
introdução de camada.

**Classificação de `@Service` caso a caso**: cada classe `@Service` hoje é migrada para (a) uma
classe de domínio/aplicação pura com a anotação Spring removida, injetada via construtor por um
adaptador fino, ou (b) o próprio ponto de entrada do adaptador que invoca o caso de uso —
decidido por classe durante a migração, conforme ela contém regra de negócio ou só orquestra I/O.
Não há regra cega de "toda `@Service` vira X".

**Ordem de migração**: `curve-bff` (menor, já sem banco/Kafka) → `curve-api` (tem banco, sem
Kafka) → `curve-processor` → `curve-engine` → `curve-orchestrator` (banco + Kafka, maiores) →
`common` (só recebe o guard ArchUnit — já é framework-free, sem código para mover). Menor módulo
primeiro para provar o padrão barato antes de aplicar aos maiores.

## Risks / Trade-offs

- **Diff grande por módulo, difícil de revisar** → um módulo por PR/rodada de tarefas, na ordem
  acima, nunca uma reorganização monorepo-wide de uma vez.
- **Explosão de interface**: porta + implementação para cada dependência externa aumenta o número
  de arquivos sem aumentar capacidade → só criar porta para dependência externa real e hoje usada
  (ver Decisions), nunca especulativa.
- **ArchUnit adiciona dependência de teste e tempo de build** → escopo do teste limitado à análise
  de grafo de import do próprio módulo (rápido, sem varrer o reator inteiro), um teste por módulo.
- **Regressão de comportamento durante a migração** → a suíte de 491 testes hoje verde é o oráculo;
  um módulo só é considerado migrado quando seus próprios testes passam sem alteração de asserção,
  só de estrutura do código de produção.
- **Sobreposição com o enforcer-plugin existente em `curve-bff`** → mantidos como mecanismos
  complementares (ver Decisions); nenhum dos dois é removido.

## Migration Plan

Um módulo por vez, na ordem em Decisions. Para cada módulo: (1) criar os pacotes `domain/`,
`application/`, `adapter/in/*`, `adapter/out/*`; (2) mover classe por classe, começando pelas sem
dependência de framework; (3) extrair porta quando uma classe de domínio/aplicação precisar de
uma dependência externa; (4) adicionar o teste ArchUnit do módulo; (5) rodar a suíte de testes do
módulo e confirmar zero mudança de asserção; (6) só então avançar para o próximo módulo. Sem plano
de rollback além de reverter o commit do módulo — nenhuma migração de dado ou contrato externo
ocorre em nenhum passo.

## Open Questions

- Vale a pena, num momento futuro, separar `domain`/`application` em módulo Maven físico
  (JAR próprio) em vez de pacote dentro do módulo do serviço, para tornar a fronteira também uma
  fronteira de build e não só de import? Não muda os requisitos desta spec (que são sobre
  direção de import, não sobre fronteira de JAR) nem a ordem de migração — pode ser decidido depois
  que os 6 módulos já estiverem no layout de pacote proposto aqui.
