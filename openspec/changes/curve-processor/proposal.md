## Why

O `curve-processor` é a peça que transforma "arquivo que a B3 publicou" em dado confiável dentro da plataforma. Hoje ele existe e grava no banco, mas sem contrato: não há garantia de que reprocessar um tópico não duplique dado, não há registro de que lote gerou que ponto, não há resposta para "esse valor mudou porque a fonte corrigiu ou porque alguém regravou errado?", e adicionar uma fonte nova significa mexer no consumidor.

Além disso, o processor recebe **duas naturezas distintas de insumo**, e hoje elas não são tratadas como coisas diferentes:

- **dado individual por instrumento** — os contratos DI1 e demais preços vindos de BVBG e do arquivo de preços de referência, que viram `ponto_dado_mercado` e depois são montados em curva pelo `curve-engine`;
- **curva já pronta** — a curva oficial que a B3 entrega vértice a vértice pelo seu endpoint de curva, que não passa por bootstrap nenhum e é publicada diretamente como curva.

As duas resultam em **curvas distintas no catálogo**, e o processor é quem separa os dois caminhos.

Esta mudança especifica o processor como um consumidor **idempotente, rastreável e extensível por dataset**: ele valida o envelope, roteia por tipo de insumo e por `dataset`, normaliza para o modelo canônico com a precisão exigida, faz upsert determinístico e registra o lote de ingestão.

## What Changes

- **Um listener por faixa**: rotina, prioritária e massa consumidas por *listener containers* independentes, com grupos de consumo e pools de thread próprios. Faixa travada não alcança as outras — o redisparo manual nunca fica atrás de uma mensagem presa.
- **Processamento por bloco, lote consolidado por contagem**: cada bloco é uma transação própria; o lote só é considerado completo quando todos os blocos declarados chegam. **Lote incompleto é falha explícita e não gera pedido de construção** — melhor não construir do que construir com metade dos contratos.
- **Invariante de avanço de offset**: todo caminho de erro termina em sucesso ou em dead-letter com o offset confirmado. Retentativa sem terminador não existe, e nenhuma partição fica presa.
- **Consumo validado**: todo evento é validado contra o schema do envelope antes de qualquer parsing; envelope inválido vai direto para dead-letter, sem tentativa.
- **Roteamento em dois níveis**: primeiro por `payloadKind` — `INDIVIDUAL_QUOTES` versus `READY_CURVE` — e depois por `dataset` para o parser correto. Nunca por inspeção do formato do payload.
- **Caminho de curva pronta**: eventos `READY_CURVE` são normalizados em vértices e publicados como `versao_curva` + `vertice_curva` para a definição de curva `IMPORTED` correspondente, sem bootstrap, obedecendo às mesmas regras de versionamento e proveniência das curvas construídas.
- **Parsers dos datasets B3**: arquivo de Preços de Referência, BVBG.086 e BVBG.028, convertendo do formato original (ISO-8859-1, decimal por vírgula) para o modelo canônico com `BigDecimal`.
- **Normalização canônica**: cada ponto vira `(fonte, conjunto_dados, data_referencia, chave_instrumento, valor, tipo_cotacao, data_vencimento)`, com `instrumentKey` estável e determinístico.
- **Upsert idempotente** pela chave única do modelo, com registro de divergência quando um ponto já existente é regravado com valor diferente.
- **Lote de ingestão** (`lote_ingestao`) por evento processado: quantos pontos chegaram, quantos foram gravados, quantos divergiram, hash do payload e `correlationId`.
- **Emissão de `marketdata.normalized.v1`** ao concluir o lote, sinalizando insumo disponível para a data.
- **Retentativa e dead-letter** conforme o contrato: transitório retenta com backoff, permanente vai para `.dlq` com motivo.
- **Regra de dado ausente**: o processor grava o que veio e nunca preenche buraco — ausência de ponto é ausência, e quem reclama é o motor na hora de construir.

## Capabilities

### New Capabilities

- `market-data-normalization`: validação do envelope, roteamento por `dataset`, parsers dos datasets B3, conversão para o modelo canônico com precisão decimal, construção do `instrumentKey` e tratamento de dead-letter para dataset desconhecido ou payload inválido.
- `market-data-persistence`: upsert idempotente em `ponto_dado_mercado`, registro do lote de ingestão com contagens e hash, detecção e registro de divergência de valor, transacionalidade do lote e emissão do evento de dado normalizado.
- `manual-curve-upload`: carga manual de curva por CSV ou planilha como contingência — leiaute declarado, erros reportados por linha, justificativa obrigatória, precisão preservada, mesmo gate de validação, versionamento idêntico e origem permanentemente distinguível.
- `imported-curve-ingestion`: caminho de curva pronta — resolução da definição `IMPORTED` correspondente, normalização dos vértices recebidos, publicação como nova versão de curva com proveniência do arquivo de origem, e recusa explícita quando o tipo de insumo não bate com o modo de origem da definição.

### Modified Capabilities

<!-- Nenhuma. -->

## Impact

- **Novo serviço**: `services/curve-processor/` em Java 21 / Spring Boot 3.4.x, sem Lombok, consumidor Kafka com Spring for Apache Kafka.
- **Depende de** `curves-solution-architecture`: envelope, catálogo de tópicos, modelo de dados e migrações Flyway.
- **Consome** os eventos produzidos por `feeder-marketdata`.
- **Escreve** em `ponto_dado_mercado` e `lote_ingestao` para todo insumo, e — exclusivamente para versões que chegaram prontas, de origem `IMPORTADA` ou `CARREGADA` — também em `versao_curva`, `vertice_curva`, `procedencia_curva` e `validacao_curva`.
- **Fronteira**: não faz bootstrap, não monta curva a partir de instrumentos, não decide quando construir, não acessa fonte externa. Publicar curva pronta é transcrever vértices recebidos, não calculá-los.
- **Precisão**: todo valor de mercado transita como `BigDecimal` e é gravado em `DECIMAL(28,12)`; `double` é proibido no caminho.
- **Nova dependência**: biblioteca de leitura de planilha, para a carga manual. Usada apenas no caminho de carga.
- **Reusa** o módulo de validação de consistência do `curve-engine`, que é biblioteca pura e serve aos dois.
