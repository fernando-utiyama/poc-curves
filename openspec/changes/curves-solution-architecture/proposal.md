## Why

A plataforma de curvas existe hoje como peças que funcionam isoladamente — feeders em Azure Functions, Kafka, `curve-processor`, motor de cálculo, `curve-orchestrator` e um SQL na Azure — mas **não existe um desenho único que amarre as peças, defina os contratos entre elas e feche o ciclo até a tela do usuário**. Cada projeto hoje presume um contrato que nunca foi escrito, e não há como rodar o fluxo ponta a ponta fora da Azure para demonstrar ou depurar.

Esta é a mudança **guarda-chuva**: ela fixa a topologia, os contratos de evento, o modelo de dados canônico e o runtime local em Podman. As outras sete mudanças (`feeder-b3-marketdata`, `curve-processor`, `curve-engine`, `curve-orchestrator`, `curve-api`, `curve-bff`, `curve-web-ui`) implementam contra os contratos definidos aqui.

## What Changes

- **Topologia formal ponta a ponta**: `curve-orchestrator` → feeders → Kafka → `curve-processor` → SQL → `curve-engine` → vértices no SQL → `curve-api` → `curve-bff` → front Angular no shell Liquid. Cada aresta ganha um contrato escrito e versionado.
- **Contrato de eventos Kafka**: tópicos `marketdata.raw.v1`, `marketdata.normalized.v1`, `curve.build.requested.v1`, `curve.published.v1` e `*.dlq`, com envelope comum (`eventId`, `correlationId`, `source`, `referenceDate`, `producedAt`, `schemaVersion`), chave de partição definida e regra de evolução compatível para trás.
- **Modelo de dados canônico** (SQL Server local espelhando Azure SQL, migrações Flyway): `definicao_curva`, `versao_definicao_curva`, `ponto_dado_mercado`, `execucao_curva`, `versao_curva`, `vertice_curva`, `procedencia_curva`, `lote_ingestao`.
- **Duas origens de curva, coexistindo como curvas distintas**: a curva **construída** (`BOOTSTRAPPED`), montada pelo `curve-engine` a partir de dado individual por instrumento — DI1, por exemplo — e a curva **importada** (`IMPORTED`), recebida já pronta do endpoint de curva da B3 e persistida vértice a vértice sem bootstrap. As duas ocupam o mesmo modelo de dados, respondem pelas mesmas APIs e podem ser comparadas entre si.
- **Modo de acionamento híbrido do motor**, formalizado: construção da curva é assíncrona e persistida; interpolação ad-hoc é síncrona, determinística e cacheável — e vale igualmente para curva construída e para curva importada.
- **Rastreabilidade obrigatória**: `correlationId` atravessa orchestrator → feeder → Kafka → processor → engine → API, e toda curva publicada aponta para o `execucao_curva` e para o hash dos insumos que a geraram.
- **Ciclo de vida da curva**: `PENDING` → `BUILDING` → `PUBLISHED` → `SUPERSEDED` / `FAILED`, com distinção entre curva de abertura, intradiária e de fechamento. Reprocessar uma data gera nova `versao_curva`, nunca sobrescreve.
- **Runtime local em Podman rootless**: `podman compose` sobe Kafka KRaft, SQL Server 2022, Redis, bootstrap de tópicos, migração de banco e seed B3 — um comando, sem Azure e sem Docker Desktop.
- **Gate de qualidade de dado**: insumo faltante interrompe a construção com erro nomeando índice e data; curva nunca é publicada parcial.
- **Carga manual de curva como contingência**: quando a curva não sai pelo fluxo normal antes do corte, o operador carrega um arquivo CSV ou planilha pela tela, com modelo disponível para download. A versão nasce marcada como carregada, exige justificativa, e **passa pelo mesmo gate de validação** — curva digitada erra mais, não menos.
- **Validação de consistência como gate de publicação**: entre construir e publicar entra uma peça que submete a curva aos testes padrão de mercado — reprecificação dos instrumentos de calibração, ausência de arbitragem, monotonicidade dos fatores de desconto, faixas plausíveis, suavidade e variação contra o dia anterior. Curva reprovada **não é publicada**; curva com aviso é publicada e sinalizada.
- **Prazo de publicação como requisito, não como expectativa**: cada curva declara um horário limite de publicação, o orçamento de tempo é decomposto por etapa, e a plataforma sinaliza **antes** do corte quando a curva do dia está em risco — em vez de reportar a falha depois que já era tarde.
- **Três faixas de ingestão isoladas**: rotina agendada, disparo manual prioritário e backfill em massa, cada uma com tópico, grupo de consumo e pool de thread próprios. Uma faixa travada não alcança as outras, e o operador nunca fica atrás de uma mensagem presa.
- **Ingestão em blocos**: o feeder quebra o arquivo da fonte em blocos de registros, com identificação de lote e sequência. Elimina o limite de tamanho de mensagem, reduz latência dentro da janela crítica e faz um registro defeituoso contaminar um bloco, não o dia inteiro.
- **Invariante contra fila travada**: nenhum caminho de erro pode terminar sem avançar o offset — ou processa, ou vai para a dead-letter e confirma. Somado ao detector de offset parado por partição, torna estruturalmente impossível uma partição ficar presa indefinidamente.
- **Modelo de construção selecionável por curva**: cada definição de curva aponta para um modelo de construção. O padrão é um **modelo fixo embutido** no motor — a curva PRE de DI1 da B3, por exemplo, nasce apontando para o modelo padrão dela. Quem opera pode, **pela tela**, trocar essa definição para um **modelo Groovy importado**, sem recompilar nem redeployar o motor. O modelo que produziu cada curva entra na proveniência.

## Capabilities

### New Capabilities

- `platform-topology`: topologia ponta a ponta, responsabilidade de cada componente, ciclo de vida e estados da curva, propagação de `correlationId`, gates de qualidade de dado, prazo de publicação e janela crítica diária, detecção de fila travada e política de reprocessamento.
- `event-contracts`: envelope e schemas dos tópicos Kafka, as três faixas de ingestão, identificação de lote e bloco, chave de partição, garantias de entrega, idempotência do consumidor, invariante de avanço de offset, dead-letter e regras de evolução de contrato para acomodar novos feeders (Bloomberg, LSEG).
- `curve-data-store`: modelo relacional canônico, migrações Flyway, modo de origem da definição, origem da versão (`CALCULADA` / `IMPORTADA` / `CARREGADA`), registro de validação, prazo de publicação, versionamento e procedência, índices de consulta e política de retenção/reprocessamento.
- `local-runtime-podman`: ambiente local completo em Podman rootless — serviços, healthchecks, bootstrap de tópicos, migração, seed de dados B3 e comando único de subida e de smoke test ponta a ponta.

### Modified Capabilities

<!-- Nenhuma: openspec/specs/ está vazio; todas as capacidades são novas. -->

## Impact

- **Repositório**: cria a estrutura do monorepo — `services/`, `web/`, `db/migration/`, `deploy/podman/`, `contracts/` (schemas de evento e OpenAPI), `docs/`.
- **Contratos como código**: schemas de evento em `contracts/events/*.json` e OpenAPI em `contracts/openapi/*.yaml` viram a fonte de verdade compartilhada pelas outras sete mudanças.
- **Kernel próprio**: a matemática de curva, as convenções B3/ANBIMA e o reconciliador vivem em `libs/curve-kernel/`, módulo novo e independente deste repositório. `curve-platform` é referência de metodologia, não dependência — nenhum módulo daqui declara dependência dele.
- **Infraestrutura**: Podman rootless no lugar de Docker Compose — portas, healthcheck, rede e socket para Testcontainers precisam ser validados sob Podman.
- **Fora de escopo**: deploy em Azure (AKS, Functions, Event Hubs, Azure SQL), Bloomberg e LSEG em produção, DR/multi-região.
