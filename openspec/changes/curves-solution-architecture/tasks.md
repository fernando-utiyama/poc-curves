## 1. Estrutura do monorepo

- [ ] 1.1 Criar a árvore `services/`, `web/`, `db/migration/`, `deploy/podman/`, `contracts/`, `docs/`, `scripts/`
- [ ] 1.2 Criar o `pom.xml` agregador Java 21 / Spring Boot 3.4.x, sem Lombok, com módulos para `curve-processor`, `curve-engine`, `curve-api` e `curve-bff`
- [ ] 1.3 Criar o módulo `libs/curve-kernel` como biblioteca pura (sem Spring, sem banco, sem rede) e declará-lo como dependência do módulo de motor
- [ ] 1.4 Configurar formatação, `.editorconfig`, `.gitignore` e `git init` do repositório
- [ ] 1.5 Escrever o `README.md` raiz com a topologia, os pré-requisitos e o comando único de subida

## 2. Contratos de evento

- [ ] 2.1 Escrever `contracts/events/envelope.schema.json` com os campos obrigatórios do envelope e taxa como string
- [ ] 2.2 Escrever os schemas de payload `marketdata-raw`, `marketdata-normalized`, `curve-build-requested` e `curve-published`
- [ ] 2.3 Definir em `contracts/events/topics.yaml` o catálogo de tópicos, partições, retenção e chave de partição
- [ ] 2.3.1 Definir retenção da dead-letter maior que a do tópico de origem, e uma dead-letter por grupo de consumo
- [ ] 2.3.2 Definir os cabeçalhos obrigatórios da mensagem em dead-letter
- [ ] 2.3.3 Definir no catálogo as três faixas de ingestão, com grupos de consumo próprios
- [ ] 2.3.4 Acrescentar `loteId`, `sequencia` e `totalBlocos` ao envelope, com `loteId` determinístico pelo hash do conteúdo
- [ ] 2.3.5 Documentar a invariante de avanço de offset e os limites de tempo de consumo como parte do contrato
- [ ] 2.4 Implementar a biblioteca Java compartilhada de envelope (record + validação de schema) em `services/common`
- [ ] 2.5 Implementar o equivalente TypeScript do envelope para os feeders, gerado a partir do mesmo schema
- [ ] 2.6 Escrever os testes de contrato que validam produtor e consumidor contra os schemas
- [ ] 2.7 Escrever o teste de compatibilidade que falha quando um campo obrigatório é removido ou muda de tipo

## 3. Modelo de dados e migrações

- [ ] 3.1 Migração `V1__definicao_curva.sql`: `definicao_curva` (modo de origem restrito por constraint e `horario_limite_publicacao` obrigatório) e `versao_definicao_curva` com orçamento por etapa e janela de bloqueio
- [ ] 3.2 Migração `V2__dado_mercado.sql`: `ponto_dado_mercado` com unicidade `(fonte, conjunto_dados, data_referencia, chave_instrumento)` e `lote_ingestao`
- [ ] 3.3 Migração `V3__execucao_curva.sql`: `execucao_curva` com estado, `correlacao_id`, `disparado_por`, faixa de ingestão, horário limite aplicável, margem e duração por etapa
- [ ] 3.4 Migração `V4__versao_curva.sql`: `versao_curva` (com os estados incluindo `EM_VALIDACAO` e `REPROVADA`), `vertice_curva` (`DECIMAL(28,12)`), `procedencia_curva` e `validacao_curva`
- [ ] 3.5 Migração `V5__modelo_curva.sql`: `modelo_curva` (tipo `BUILTIN` / `GROOVY`, estado, checksum), referência ao modelo em `versao_definicao_curva` e coluna de modelo em `procedencia_curva`
- [ ] 3.6 Migração `V6__pendencia_dlq.sql`: `pendencia_dlq` com unicidade de `id_evento` e índice de agrupamento para o alerta
- [ ] 3.7 Migração `V7__indices.sql`: índices do caminho de consulta declarados na spec
- [ ] 3.8 Escrever o teste de conformidade de esquema que rejeita coluna de valor de mercado em ponto flutuante
- [ ] 3.9 Escrever o teste de ida e volta de precisão em `DECIMAL(28,12)`

## 4. Ambiente local em Podman

- [ ] 4.1 Escrever `deploy/podman/compose.yaml` com Kafka KRaft, SQL Server 2022, Redis e Keycloak, com healthcheck e volume nomeado em cada serviço
- [ ] 4.2 Adicionar o perfil reduzido (`compose.lite.yaml`) sem serviços opcionais
- [ ] 4.3 Escrever o container de bootstrap que cria os tópicos do catálogo com broker sem criação automática
- [ ] 4.4 Escrever o container de migração Flyway que roda contra o SQL Server e termina com código zero
- [ ] 4.5 Escrever `deploy/podman/up.sh` e `up.ps1` com espera ativa por prontidão real de cada serviço, independentemente de `depends_on`
- [ ] 4.6 Escrever `down.sh` / `down.ps1`, com e sem remoção de volumes
- [ ] 4.7 Escrever `scripts/doctor.sh` que verifica versão do Podman, memória disponível, portas livres e socket para Testcontainers
- [ ] 4.8 Documentar em `docs/ambiente-local.md` os pré-requisitos, as portas e a configuração de `DOCKER_HOST` para Testcontainers sob Podman

## 5. Seed de dados B3

- [ ] 5.1 Selecionar e versionar em `data/b3/` uma data de pregão completa (arquivo PR, BVBG.086, BVBG.028)
- [ ] 5.2 Versionar a taxa de referência oficial da B3 da mesma data, como oráculo de reconciliação
- [ ] 5.3 Escrever o carregador de seed que popula `ponto_dado_mercado` e as duas definições de curva: a PRE construída a partir de DI1 (`BOOTSTRAPPED`) e a PRE oficial da B3 (`IMPORTED`)
- [ ] 5.4 Verificar que o seed é reprodutível: recriar o ambiente duas vezes e comparar o dado resultante

## 6. Desenho de arquitetura

- [ ] 6.1 Produzir `docs/architecture/curves-platform.drawio` com as visões de contexto, componentes e fluxo ponta a ponta
- [ ] 6.2 Exportar `docs/architecture/curves-platform.svg` para leitura direta no repositório
- [ ] 6.3 Escrever `docs/architecture/README.md` mapeando cada bloco do desenho ao componente e à mudança OpenSpec correspondente
- [ ] 6.4 Revisar o desenho contra as fronteiras de componente da spec, garantindo que nenhuma seta viole uma fronteira

## 7. Observabilidade transversal

- [ ] 7.1 Implementar o filtro/interceptador que lê, gera e propaga `correlationId` em HTTP e em Kafka nos módulos Java
- [ ] 7.2 Implementar o equivalente nos feeders Node
- [ ] 7.3 Padronizar log estruturado em JSON com `correlationId`, `component`, `runId` e `referenceDate`
- [ ] 7.4 Expor endpoint de saúde e de métricas em cada serviço Java
- [ ] 7.5 Escrever o teste de integração que verifica o mesmo `correlationId` atravessando os quatro componentes do fluxo
- [ ] 7.6 Implementar o monitoramento de offset confirmado e lag **por partição**, com alerta de offset parado
- [ ] 7.7 Expor a margem para o horário limite como métrica por curva e por dia
- [ ] 7.8 Escrever o procedimento de emergência de destravamento de partição e ensaiá-lo no ambiente local
- [ ] 7.9 Escrever o teste de conformidade que falha se a contagem de partições divergir do declarado no catálogo

## 8. Aceite ponta a ponta

- [ ] 8.1 Escrever o smoke test que dispara a ingestão, aguarda a publicação e consulta a curva interpolada
- [ ] 8.2 Integrar o `B3ReferenceRateReconciler` ao smoke test, comparando vértice a vértice com arredondamento pela mesma política de produção
- [ ] 8.2.1 Fazer o smoke test publicar as duas curvas da mesma data — a construída a partir de DI1 e a importada da B3 — e comparar uma contra a outra pela API de comparação
- [ ] 8.3 Fazer o smoke test reportar a etapa de parada e o `correlationId` em caso de falha
- [ ] 8.4 Escrever o teste que falha o build se qualquer módulo declarar dependência de repositório externo de curvas
- [ ] 8.5 Rodar o fluxo completo em ambiente limpo e registrar o resultado em `docs/aceite-poc.md`
