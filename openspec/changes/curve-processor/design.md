## Context

O `curve-processor` fica entre o Kafka e o banco. Recebe payload bruto publicado pelos feeders — que, por decisão de `feeder-b3-marketdata`, não interpretam nada — e é o único lugar onde o conhecimento de formato da fonte se encontra com o modelo canônico da plataforma.

O que chega da B3 é heterogêneo: arquivo de Preços de Referência em texto posicional, BVBG.086 e BVBG.028 em XML padrão FIXML/BVBG, tudo em ISO-8859-1 com decimal por vírgula. O que sai é uniforme: linhas em `ponto_dado_mercado` com valor em `DECIMAL(28,12)`.

Os parsers desses formatos são implementação nova neste repositório, escritos contra fixtures reais da B3. A metodologia de referência já foi validada em outro projeto, mas o código é daqui e evolui daqui.

Restrição estruturante: entrega Kafka é *at-least-once*. O processor **vai** receber evento repetido, e precisa terminar no mesmo estado.

## Goals / Non-Goals

**Goals:**

- Idempotência real, verificada por teste: consumir o mesmo evento N vezes deixa o banco idêntico a consumir uma vez.
- Rastreabilidade do ponto até o lote, o evento e o run que o originaram.
- Extensibilidade por dataset: fonte nova entra com um parser, sem tocar no roteamento nem no schema.
- Precisão decimal preservada da fonte até o banco, sem passar por ponto flutuante em momento algum.

- Separar com clareza os dois caminhos de insumo — dado individual e curva pronta — de modo que a curva construída e a curva importada existam lado a lado e possam ser comparadas.

**Non-Goals:**

- Fazer bootstrap, montar curva a partir de instrumentos, validar coerência financeira entre instrumentos ou decidir se há dado suficiente — isso é do `curve-engine`.
- Julgar ou corrigir a curva pronta recebida da fonte oficial: transcrever é o trabalho, calcular não é.
- Buscar dado na fonte externa — isso é do feeder.
- Preencher lacuna de dado por qualquer meio.
- Fazer bootstrap ou qualquer cálculo de curva a partir dos instrumentos.

## Decisions

### D1 — Roteamento por `dataset`, parser registrado

Um `Map<String, DatasetParser>` resolve o parser a partir do campo `dataset` do envelope. O parser recebe o payload bruto e o encoding declarado, e devolve uma lista de pontos canônicos. Dataset sem parser registrado vai para dead-letter com `UNKNOWN_DATASET`, e o consumidor segue processando os demais.

*Alternativa considerada*: detectar o formato inspecionando o payload. Rejeitada — heurística de formato é frágil e falha em silêncio; o `dataset` é declarado pelo produtor e é contrato.

### D1b — Dois caminhos de processamento, separados por `payloadKind`

O primeiro desvio do consumidor não é por dataset, é por natureza do insumo:

- **`INDIVIDUAL_QUOTES`** → parser de instrumentos → `ponto_dado_mercado`. O motor monta a curva depois.
- **`READY_CURVE`** → parser de vértices → `versao_curva` + `vertice_curva` diretamente. Não há bootstrap.

O segundo caminho é transcrição, não cálculo: os vértices que a B3 divulgou entram como estão, com a precisão preservada. O processor não valida se a curva "faz sentido" financeiramente — ela é o que a fonte oficial publicou, e alterá-la seria falsificá-la.

*Consequência*: o processor passa a escrever nas tabelas de curva, o que a arquitetura originalmente reservava ao motor. A restrição que substitui a anterior é mais estreita e mais verificável: o processor só publica para definições cujo modo de origem é `IMPORTED`, e o motor só publica para as `BOOTSTRAPPED`. A checagem é feita contra o catálogo, em tempo de execução, antes da escrita.

*Alternativa considerada*: mandar a curva pronta para o motor "publicar", mantendo o motor como único autor de vértices. Rejeitada — o motor teria que existir e estar disponível para uma operação que não usa nada dele, e o caminho ganharia um salto de rede e um ponto de falha sem nenhuma contrapartida.

### D1c — Incoerência entre insumo e modo de origem é falha, não adaptação

Se chega `READY_CURVE` para uma definição `BOOTSTRAPPED`, ou `INDIVIDUAL_QUOTES` mapeado para uma definição `IMPORTED`, o processor falha nomeando a definição, o modo declarado e o tipo recebido. Não tenta adivinhar a intenção nem "fazer funcionar".

*Por quê*: o caso silencioso aqui seria publicar como curva oficial algo que na verdade é insumo bruto — erro que só apareceria muito depois, na mesa.

### D2 — `instrumentKey` determinístico e estável

A chave do instrumento é derivada dos identificadores naturais do dataset (por exemplo, símbolo do contrato + vencimento), normalizada e documentada por parser. Ela participa da chave única de `ponto_dado_mercado`, então mudar a regra de derivação é uma migração de dado, não uma refatoração.

*Trade-off*: amarra o modelo à convenção de nomeação da fonte. Aceito, porque o alternativo — chave sintética — perderia a capacidade de reprocessar de forma idempotente.

### D2b — Transação por bloco, lote consolidado por contagem

Com a quebra em blocos, "uma transação por mensagem" deixa de coincidir com "uma transação por arquivo". A escolha: **cada bloco é uma transação atômica própria**, e a completude do lote é verificada pela contagem declarada em `totalBlocos`.

O `lote_ingestao` é criado no primeiro bloco que chega e consolidado quando o último completa a contagem. Enquanto não completa, o lote está aberto.

*Por que a completude importa tanto*: sem ela, um feeder que morreu no meio do arquivo deixaria metade dos contratos no banco, e o motor construiria uma curva com insumo parcial — exatamente o que o gate de qualidade existe para impedir. Com a contagem, o lote incompleto é detectável e **nenhum pedido de construção é emitido**.

*Lote que nunca completa*: passado o tempo limite, é marcado como incompleto com os blocos faltantes nomeados. É falha visível, não espera indefinida.

*Ordem de chegada não importa*: os blocos podem chegar fora de ordem; o que fecha o lote é a contagem, não a sequência do último.

### D2c — Um listener container por faixa

As três faixas são consumidas por listeners independentes, cada um com grupo de consumo e pool de thread próprios.

*Por que thread e não só partição*: se a faixa prioritária fosse apenas outro tópico consumido pelo mesmo consumidor, uma thread bloqueada retentando a mensagem travada da rotina não iria buscar a mensagem prioritária. **O isolamento necessário é de consumidor e thread**, e listeners separados são o que entrega isso.

*Consequência operacional*: a faixa de massa pode ser pausada — na janela crítica, por exemplo — sem tocar nas outras duas.

### D3 — Upsert transacional por lote, não por ponto

Todos os pontos de um evento são gravados em uma única transação, junto com o `lote_ingestao`. Falha no meio reverte o lote inteiro e o evento é retentado. Isso evita o estado meio-gravado que faria o motor construir curva com insumo parcial.

*Alternativa considerada*: gravar ponto a ponto com commit individual. Rejeitada — cria exatamente a janela de insumo parcial que o gate de qualidade tenta evitar.

### D4 — Divergência de valor é registrada, não silenciada

Se um ponto já existe com valor diferente, o valor novo prevalece (a fonte é a autoridade), mas a divergência — valor antigo, valor novo, evento que causou — é registrada no `lote_ingestao`. Assim "o número mudou" tem resposta em vez de virar mistério.

*Alternativa considerada*: rejeitar a regravação. Rejeitada — a B3 de fato republica dado corrigido, e recusar a correção seria pior.

### D4b — Nenhum caminho de erro termina sem avançar o offset

Invariante do serviço: ou o bloco é processado com sucesso, ou vai para a dead-letter **e o offset é confirmado**. Retentativa sem terminador é proibida.

Quatro caminhos precisam respeitá-la: falha de desserialização (exige desserializador que encapsule em vez de lançar, senão o erro ocorre antes do tratamento e produz laço infinito), retentativa sem recuperador terminal, mensagem lenta demais que estoura o intervalo de poll e provoca *livelock* por rebalance, e chamada externa sem timeout.

*Consequência sobre a retentativa transitória*: além do teto de tentativas, existe teto de **tempo**, derivado do tempo restante até o horário limite de publicação. Retentar dez minutos às 14h é razoável; às 18h50 consome metade do que sobrou.

### D5 — Idempotência ancorada em `eventId`, reforçada pela chave única

Duas camadas: o `eventId` já processado é reconhecido e o lote é ignorado; e, independentemente disso, a chave única de `ponto_dado_mercado` garante que mesmo um caminho inesperado não duplique linha. A segunda camada existe porque a primeira depende de estado que pode se perder.

### D6 — `BigDecimal` da borda ao banco, com escala explícita

A conversão de texto para número acontece uma vez, no parser, direto para `BigDecimal`, com o separador decimal da fonte tratado explicitamente. Nenhum ponto do caminho usa `double`. O JSON de saída, quando houver, serializa como string.

*Por que é decisão e não detalhe*: em taxa, `double` perde dígito de forma que só aparece na reconciliação contra a B3 — ou seja, tarde.

### D7 — Evento de normalizado sai depois do commit

`marketdata.normalized.v1` só é publicado após o commit da transação do lote. Publicar antes criaria a janela em que o motor tenta ler um insumo que ainda não está visível.

*Trade-off*: se o processo morrer entre o commit e a publicação, o evento se perde. Mitigação: o consumo é retentado a partir do offset não confirmado, o lote é reconhecido como já processado e o evento é republicado — o `eventId` determinístico faz isso ser seguro.

### D7b — Gravação com ordenação determinística

Como as faixas podem processar o mesmo conteúdo em paralelo — o caso normal quando o operador redispara algo que travou —, duas transações podem tocar as mesmas linhas ao mesmo tempo.

A proteção primária é o registro de eventos processados com unicidade em `id_evento`: quem chega primeiro grava, o segundo recebe violação de chave e reconhece como duplicado, sem janela de verificar-e-agir. Como defesa contra deadlock quando ambas passam, as gravações dentro de um bloco seguem ordenação determinística por chave.

### D8 — Dead-letter carrega contexto suficiente para reprocessar

Toda mensagem em `.dlq` leva motivo, `correlationId`, `eventId`, o offset original e o payload. Reenviar da dead-letter depois de corrigir o parser é uma operação de rotina, não uma escavação.

## Risks / Trade-offs

- **Formato da B3 muda e o parser quebra em produção** → o payload bruto continua no tópico por 7 dias, então corrigir o parser e reprocessar do offset é possível sem rebaixar dado da fonte.
- **Regra de `instrumentKey` mal escolhida agora custa migração depois** → documentar a derivação por dataset e cobrir com teste de fixture real, tornando qualquer mudança visível.
- **Lote grande em uma transação pode estourar tempo ou memória** → limite configurável de pontos por transação, com o lote dividido em blocos e o `lote_ingestao` consolidando as contagens; a atomicidade do bloco é preservada.
- **Divergência de valor registrada mas ignorada por todos** → a divergência aparece na tela de monitoramento de execuções, não só no banco.
- **Duas camadas de idempotência dão falsa sensação de segurança** → o teste de idempotência consome o mesmo evento repetidamente e compara o estado completo do banco, não apenas a contagem de linhas.
- **Parser novo reintroduzir um erro de interpretação de formato já resolvido em outro lugar** → mitigado por fixtures reais e pela reconciliação da curva construída contra a taxa de referência oficial da B3, que é oráculo externo e pega erro de parsing por consequência.

## Migration Plan

1. Consumidor com validação de envelope e dead-letter, sem parser nenhum — verifica o caminho de rejeição primeiro.
2. Parser do arquivo de Preços de Referência e persistência do primeiro dataset ponta a ponta.
3. Parsers BVBG.086 e BVBG.028.
4. Lote de ingestão, divergência e emissão do evento de normalizado.
5. Testes de idempotência e de replay contra o Kafka local.

**Rollback**: o serviço é sem estado além do offset do grupo de consumo. Reverter é voltar a imagem; o dado já gravado permanece válido, e o reprocessamento é idempotente por construção.

## Open Questions

- Qual o limite prático de pontos por transação no SQL Server sob Podman? Determina o tamanho do bloco.
- O BVBG.028 (cadastro de instrumentos) deve virar linhas em `ponto_dado_mercado` ou merece uma tabela de referência própria? Hoje o modelo o trata como market data; se o volume de atributos crescer, isso incomoda.
- Divergência de valor deve alarmar ativamente ou apenas ficar registrada e visível? Depende de quão comum é a republicação corrigida da B3 na prática.
- Reprocessamento em massa (uma janela de datas) roda pelo mesmo caminho de consumo ou merece um modo em lote dedicado?
