## ADDED Requirements

### Requirement: Validação do envelope antes do parsing

O processor SHALL validar todo evento consumido contra o schema do envelope antes de qualquer tentativa de interpretar o payload. Evento com envelope inválido SHALL ir para a dead-letter com motivo `INVALID_ENVELOPE`, sem retentativa.

#### Scenario: Envelope válido

- **WHEN** um evento chega com todos os campos obrigatórios do envelope preenchidos
- **THEN** o processor SHALL prosseguir para o roteamento por dataset

#### Scenario: Envelope sem correlation id

- **WHEN** um evento chega sem `correlationId`
- **THEN** o processor SHALL enviá-lo à dead-letter com motivo `INVALID_ENVELOPE`, sem tentar interpretar o payload

### Requirement: Roteamento por dataset

O processor SHALL selecionar o parser pelo campo `dataset` do envelope. O processor MUST NOT inferir o formato inspecionando o conteúdo do payload.

#### Scenario: Dataset com parser registrado

- **WHEN** chega um evento cujo `dataset` tem parser registrado
- **THEN** o processor SHALL usar esse parser, independentemente da estrutura do payload

#### Scenario: Dataset desconhecido

- **WHEN** chega um evento com `dataset` sem parser registrado
- **THEN** o evento SHALL ir para a dead-letter com motivo `UNKNOWN_DATASET`, e o processor SHALL continuar consumindo os eventos seguintes

#### Scenario: Fonte nova com dataset conhecido

- **WHEN** chega um evento de uma `source` nova, com um `dataset` já registrado
- **THEN** o processor SHALL processá-lo com o parser existente, sem alteração de código

### Requirement: Parsers dos datasets B3

O processor SHALL implementar parsers para os datasets `B3_PRICE_REPORT`, `B3_BVBG086` e `B3_BVBG028`, produzindo pontos no modelo canônico a partir do payload bruto e do encoding declarado no evento.

#### Scenario: Arquivo de Preços de Referência

- **WHEN** um evento do dataset de Preços de Referência é processado
- **THEN** o parser SHALL produzir um ponto canônico por instrumento presente no arquivo, com valor, tipo de cotação e vencimento

#### Scenario: Payload estruturalmente inválido

- **WHEN** o payload não corresponde à estrutura esperada pelo parser do dataset
- **THEN** o evento SHALL ir para a dead-letter com motivo `PARSE_FAILED`, informando a posição ou o elemento que falhou, sem retentativa

### Requirement: Conversão numérica com precisão preservada

A conversão de texto para número SHALL produzir `BigDecimal` diretamente, tratando explicitamente o separador decimal da fonte. Nenhum valor de mercado MUST passar por tipo de ponto flutuante em qualquer etapa do processamento.

#### Scenario: Decimal no formato brasileiro

- **WHEN** o payload traz um valor com vírgula como separador decimal
- **THEN** o ponto canônico SHALL conter o valor exato como `BigDecimal`, sem perda de dígito

#### Scenario: Proibição de ponto flutuante

- **WHEN** o código de parsing ou de normalização usa `double` ou `float` para um valor de mercado
- **THEN** a verificação estática do build SHALL falhar

### Requirement: Chave de instrumento determinística

O parser SHALL derivar o `instrumentKey` dos identificadores naturais do dataset, de forma determinística e documentada. O mesmo instrumento na mesma data SHALL sempre produzir a mesma chave.

#### Scenario: Chave estável entre execuções

- **WHEN** o mesmo payload é processado duas vezes
- **THEN** os `instrumentKey` produzidos SHALL ser idênticos

#### Scenario: Instrumentos distintos não colidem

- **WHEN** dois instrumentos diferentes do mesmo dataset são processados
- **THEN** eles SHALL produzir `instrumentKey` distintos

### Requirement: Encoding respeitado

O processor SHALL decodificar o payload usando o encoding declarado no metadado do evento, e MUST NOT assumir um encoding padrão quando o metadado estiver presente.

#### Scenario: Conteúdo em ISO-8859-1

- **WHEN** o evento declara encoding ISO-8859-1
- **THEN** a decodificação SHALL usar esse encoding, e caracteres acentuados SHALL ser preservados corretamente

### Requirement: Ausência de dado não é preenchida

O processor SHALL gravar apenas os pontos efetivamente presentes no payload. O processor MUST NOT interpolar, repetir valor de data anterior, nem inserir valor default para instrumento ausente.

#### Scenario: Instrumento ausente no arquivo

- **WHEN** um instrumento esperado não consta no payload recebido
- **THEN** nenhum ponto SHALL ser criado para ele, e a ausência SHALL ser visível na contagem do lote

### Requirement: Dead-letter com contexto de reprocessamento

Toda mensagem enviada à dead-letter SHALL carregar o motivo, o `correlationId`, o `eventId`, o offset original e o payload, de forma que o reenvio após correção seja possível sem consultar a fonte.

#### Scenario: Reenvio após correção do parser

- **WHEN** um evento em dead-letter por falha de parsing é reenviado depois que o parser é corrigido
- **THEN** ele SHALL ser processado normalmente, preservando o `correlationId` original
