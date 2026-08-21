## ADDED Requirements

### Requirement: Construção disparada por evento

O motor SHALL consumir `curve.build.requested.v1` e iniciar a construção da curva identificada, para a data de referência e o momento de curva informados, propagando o `correlationId` recebido.

#### Scenario: Pedido de construção válido

- **WHEN** chega um pedido de construção para uma definição existente e uma data com insumos disponíveis
- **THEN** o motor SHALL construir a curva e prosseguir para a publicação

#### Scenario: Definição inexistente

- **WHEN** o pedido referencia uma definição de curva que não existe
- **THEN** o run SHALL falhar nomeando o identificador solicitado, e o evento SHALL ir para a dead-letter

#### Scenario: Construção concorrente da mesma curva

- **WHEN** dois pedidos de construção da mesma curva, data e momento chegam simultaneamente
- **THEN** apenas uma construção SHALL publicar, e a outra SHALL ser reconhecida como redundante, sem criar duas versões concorrentes

### Requirement: Resolução de definição, versão e modelo

Antes de construir, o motor SHALL resolver a definição da curva, a versão de definição vigente para a data e o modelo de construção que ela referencia. Modelo desabilitado ou ausente SHALL falhar a construção nomeando o modelo.

#### Scenario: Versão de definição vigente

- **WHEN** existem várias versões da definição com intervalos de vigência distintos
- **THEN** o motor SHALL usar a versão vigente na data de referência da construção

#### Scenario: Modelo referenciado desabilitado

- **WHEN** a definição referencia um modelo marcado como desabilitado
- **THEN** a construção SHALL falhar nomeando o modelo, e nada SHALL ser publicado

### Requirement: Resolução dos insumos pelo motor

O motor SHALL montar o conjunto de insumos exigido pela definição a partir de `ponto_dado_mercado`, antes de executar o modelo. O modelo MUST NOT consultar dado por conta própria.

#### Scenario: Insumos carregados antes da execução

- **WHEN** a construção inicia
- **THEN** os insumos SHALL ser resolvidos e validados antes de qualquer código de modelo executar

#### Scenario: Insumo de outra data

- **WHEN** o conjunto exigido inclui um fixing de data anterior declarado na definição
- **THEN** o motor SHALL carregá-lo explicitamente conforme a definição, e MUST NOT substituí-lo por valor de data diferente da declarada

### Requirement: Gate de insumo faltante

A construção SHALL falhar quando qualquer insumo exigido pela definição estiver ausente, nomeando o índice ou o instrumento e a data faltantes. O motor MUST NOT interpolar, repetir valor anterior nem aplicar valor default para completar insumo, e MUST NOT publicar curva parcial.

#### Scenario: Fixing ausente

- **WHEN** um fixing exigido não existe para a data
- **THEN** a construção SHALL falhar nomeando índice e data, o run SHALL ir para `FAILED`, e nenhuma versão SHALL ser criada

#### Scenario: Conjunto de contratos incompleto

- **WHEN** o número de contratos disponíveis é menor que o mínimo declarado na definição
- **THEN** a construção SHALL falhar informando o esperado e o recebido

#### Scenario: Preenchimento proibido

- **WHEN** o código tenta completar um insumo ausente por interpolação, repetição ou default
- **THEN** o teste correspondente SHALL falhar, porque ausência de insumo é ausência de dado

### Requirement: Execução do modelo de construção

O motor SHALL executar o modelo referenciado pela definição, entregando os insumos resolvidos e o contexto, e SHALL receber dele a lista de vértices. O motor SHALL validar que os vértices retornados são não vazios, com prazos distintos e ordenáveis.

#### Scenario: Modelo devolve vértices válidos

- **WHEN** o modelo executa com sucesso e devolve vértices consistentes
- **THEN** o motor SHALL prosseguir para a publicação

#### Scenario: Modelo devolve conjunto vazio

- **WHEN** o modelo devolve nenhum vértice
- **THEN** a construção SHALL falhar nomeando o modelo, e nada SHALL ser publicado

#### Scenario: Modelo devolve prazos duplicados

- **WHEN** o modelo devolve dois vértices com o mesmo prazo
- **THEN** a construção SHALL falhar nomeando o prazo em conflito

### Requirement: Determinismo da construção

Construir a mesma curva com os mesmos insumos, a mesma versão de definição e o mesmo modelo SHALL produzir vértices idênticos, valor a valor.

#### Scenario: Reconstrução idêntica

- **WHEN** a mesma construção é executada duas vezes sem que nada tenha mudado
- **THEN** os vértices resultantes SHALL ser exatamente iguais, dígito a dígito

#### Scenario: Diferença tem causa identificável

- **WHEN** duas construções da mesma curva e data produzem vértices diferentes
- **THEN** a diferença SHALL ser explicável por mudança de insumo, de versão de definição ou de modelo, registrada na proveniência

### Requirement: Precisão no caminho de construção

Todo valor com política de arredondamento de mercado SHALL transitar como decimal de precisão arbitrária durante a construção. Ponto flutuante MUST NOT ser usado para esses valores, nem como intermediário.

#### Scenario: Verificação estática

- **WHEN** o código de construção usa ponto flutuante para valor de mercado
- **THEN** a verificação estática do build SHALL falhar

### Requirement: Dependência entre curvas

Quando a definição declara que a curva depende de outra curva publicada, o motor SHALL exigir que a curva dependida esteja `PUBLISHED` para a mesma data antes de construir. Dependência não satisfeita SHALL falhar nomeando a curva ausente.

#### Scenario: Dependência satisfeita

- **WHEN** a curva dependida está publicada para a data
- **THEN** a construção SHALL prosseguir usando os vértices publicados dela

#### Scenario: Dependência ausente

- **WHEN** a curva dependida não tem versão publicada para a data
- **THEN** a construção SHALL falhar nomeando a curva e a data, e MUST NOT usar a curva de outra data

#### Scenario: Ciclo de dependência

- **WHEN** as definições formam um ciclo de dependência
- **THEN** a construção SHALL falhar nomeando o ciclo, em vez de recursar indefinidamente

### Requirement: Estado do run durante a construção

O motor SHALL atualizar o estado do run ao longo da construção, registrando início, conclusão ou falha, com a causa em caso de falha, e a duração da execução.

#### Scenario: Falha visível na tela

- **WHEN** uma construção falha por qualquer motivo
- **THEN** o run SHALL registrar o estado `FAILED` com código e mensagem de erro, visíveis na tela de monitoramento

### Requirement: Fronteira de publicação por modo de origem

O motor SHALL construir e publicar exclusivamente para definições com modo de origem `BOOTSTRAPPED`. Pedido de construção para definição `IMPORTED` SHALL ser recusado nomeando a definição e o modo.

#### Scenario: Pedido para curva importada

- **WHEN** chega um pedido de construção para uma definição com modo `IMPORTED`
- **THEN** o motor SHALL recusar nomeando a definição e o modo, e nenhuma versão SHALL ser criada
