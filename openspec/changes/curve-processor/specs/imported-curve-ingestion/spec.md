## ADDED Requirements

### Requirement: Roteamento por tipo de insumo

O processor SHALL desviar o processamento pelo campo `payloadKind` do evento antes de qualquer outra decisão: `INDIVIDUAL_QUOTES` segue para a persistência de market data, `READY_CURVE` segue para a publicação de curva importada. O processor MUST NOT inferir o tipo de insumo pelo conteúdo do payload.

#### Scenario: Dado individual

- **WHEN** chega um evento com `payloadKind` igual a `INDIVIDUAL_QUOTES`
- **THEN** o processor SHALL persistir pontos em `ponto_dado_mercado` e SHALL NOT publicar curva

#### Scenario: Curva pronta

- **WHEN** chega um evento com `payloadKind` igual a `READY_CURVE`
- **THEN** o processor SHALL publicar uma versão de curva e SHALL NOT gravar os vértices como pontos de market data

#### Scenario: Tipo de insumo ausente

- **WHEN** chega um evento sem `payloadKind`
- **THEN** o evento SHALL ir para a dead-letter com motivo `INVALID_ENVELOPE`

### Requirement: Resolução da definição de curva importada

Ao processar um evento de curva pronta, o processor SHALL resolver a definição de curva correspondente pelo mapeamento entre fonte, identificador de curva na origem e código da definição no catálogo. Definição não encontrada SHALL enviar o evento à dead-letter com motivo `UNMAPPED_CURVE`, sem publicar nada.

#### Scenario: Curva mapeada

- **WHEN** o identificador de curva na origem tem definição correspondente no catálogo
- **THEN** o processor SHALL publicar a versão para essa definição

#### Scenario: Curva sem mapeamento

- **WHEN** chega uma curva pronta cujo identificador de origem não tem definição cadastrada
- **THEN** o evento SHALL ir para a dead-letter com motivo `UNMAPPED_CURVE`, e o processamento dos demais eventos SHALL continuar

### Requirement: Coerência entre insumo e modo de origem

O processor SHALL verificar, antes de escrever, que o modo de origem da definição resolvida é compatível com o tipo de insumo recebido. Incompatibilidade SHALL falhar nomeando a definição, o modo declarado e o tipo recebido, e nada SHALL ser publicado.

#### Scenario: Curva pronta para definição construída

- **WHEN** um evento `READY_CURVE` resolve para uma definição com modo `BOOTSTRAPPED`
- **THEN** o processamento SHALL falhar nomeando a definição e os dois modos, e nenhuma versão SHALL ser criada

#### Scenario: Processor não publica curva construída

- **WHEN** o processor tenta publicar versão para uma definição com modo `BOOTSTRAPPED`
- **THEN** a operação SHALL ser recusada, porque publicar curva construída é exclusividade do motor

### Requirement: Transcrição fiel dos vértices recebidos

O processor SHALL transcrever os vértices da curva pronta preservando exatamente os valores recebidos, com a precisão decimal da fonte. O processor MUST NOT recalcular, reinterpolar, reordenar por critério próprio, filtrar nem arredondar os vértices recebidos.

#### Scenario: Vértices preservados

- **WHEN** uma curva pronta com um conjunto de vértices é publicada
- **THEN** cada vértice persistido SHALL ter exatamente o prazo e o valor recebidos da fonte, dígito a dígito

#### Scenario: Tentativa de completar a curva

- **WHEN** a curva recebida não cobre um prazo que a plataforma gostaria de ter
- **THEN** o processor SHALL publicar apenas os vértices recebidos, e MUST NOT acrescentar vértice interpolado na publicação

#### Scenario: Curva vazia

- **WHEN** o payload de curva pronta não contém nenhum vértice
- **THEN** nenhuma versão SHALL ser publicada, e o evento SHALL ir para a dead-letter com motivo `EMPTY_CURVE`

### Requirement: Publicação da curva importada sob as mesmas regras

A publicação de curva importada SHALL seguir as mesmas regras das curvas construídas: criação de nova `versao_curva` com número incremental, marcação da versão anterior como `SUPERSEDED`, escrita atômica de versão, vértices e proveniência, e estado `PUBLISHED` apenas com proveniência completa.

#### Scenario: Republicação da mesma data

- **WHEN** a curva pronta da mesma data é recebida novamente com conteúdo diferente
- **THEN** uma nova versão SHALL ser publicada e a anterior SHALL passar para `SUPERSEDED`, com seus vértices preservados

#### Scenario: Falha na gravação da proveniência

- **WHEN** a gravação da proveniência falha durante a publicação
- **THEN** a transação inteira SHALL ser revertida, e nenhuma versão SHALL ficar publicada

#### Scenario: Recebimento idempotente

- **WHEN** o mesmo evento de curva pronta é consumido duas vezes
- **THEN** SHALL existir apenas uma versão publicada para aquele conteúdo, sem versão duplicada

### Requirement: Proveniência da curva importada

A proveniência de uma curva importada SHALL identificar o lote de ingestão, o `eventId`, o arquivo ou resposta de origem, seu hash, a fonte e o `correlationId` do run que a trouxe.

#### Scenario: Origem auditável

- **WHEN** a proveniência de uma curva importada é consultada
- **THEN** a resposta SHALL permitir identificar exatamente qual conteúdo da fonte gerou aqueles vértices

### Requirement: Notificação de curva importada publicada

Após o commit da publicação, o processor SHALL publicar `curve.published.v1` para a curva importada, com o mesmo contrato usado pelo motor, preservando o `correlationId`.

#### Scenario: Assinantes notificados

- **WHEN** uma curva importada é publicada com sucesso
- **THEN** o evento de curva publicada SHALL ser emitido, e um assinante SHALL NOT conseguir distinguir o formato do evento pelo modo de origem da curva

#### Scenario: Falha antes do commit

- **WHEN** a publicação falha
- **THEN** nenhum evento de curva publicada SHALL ser emitido
