## ADDED Requirements

### Requirement: Consulta de curva publicada

A API SHALL retornar a curva publicada para um código de curva, data de referência e momento de curva, identificando sempre na resposta qual versão foi usada, mesmo quando o cliente não solicitou versão específica.

#### Scenario: Versão corrente

- **WHEN** a consulta não informa versão
- **THEN** SHALL ser retornada a versão no estado publicado, e a resposta SHALL identificar o seu número e identificador

#### Scenario: Versão explícita

- **WHEN** a consulta informa um identificador de versão
- **THEN** SHALL ser retornada exatamente aquela versão, ainda que esteja substituída

#### Scenario: Consulta por instante

- **WHEN** a consulta informa `asOf`
- **THEN** SHALL ser retornada a versão que estava publicada naquele instante, e a resposta SHALL indicar que a seleção foi por instante

#### Scenario: Sem curva para a data

- **WHEN** não existe versão publicada para a curva na data solicitada
- **THEN** a resposta SHALL informar a ausência explicitamente, e MUST NOT devolver a curva de outra data

### Requirement: Contrato uniforme entre origens de curva

A consulta SHALL usar o mesmo contrato de resposta para curvas construídas e importadas. A resposta SHALL indicar o modo de origem, sem alterar a estrutura dos dados.

#### Scenario: Curva importada consultada

- **WHEN** uma curva de modo importado é consultada
- **THEN** a resposta SHALL ter a mesma estrutura da de uma curva construída, com o modo de origem indicado

### Requirement: Listagem de vértices

A API SHALL retornar os vértices de uma versão de curva, ordenados por prazo crescente, com paginação. A página SHALL ser resolvida contra uma versão fixa, de forma que o cliente nunca receba vértices de versões diferentes na mesma navegação.

#### Scenario: Paginação estável

- **WHEN** o cliente navega pelas páginas de vértices enquanto uma nova versão é publicada
- **THEN** todas as páginas SHALL vir da mesma versão inicialmente resolvida

#### Scenario: Ordenação por prazo

- **WHEN** os vértices são listados
- **THEN** eles SHALL vir ordenados por prazo crescente

### Requirement: Precisão na serialização

Taxa, fator de desconto, cotação e demais valores com política de arredondamento de mercado SHALL ser serializados como texto numérico. A API MUST NOT serializá-los como número de ponto flutuante.

#### Scenario: Valor com muitas casas decimais

- **WHEN** um vértice com doze casas decimais é retornado
- **THEN** o cliente SHALL receber todos os dígitos, sem arredondamento imposto pela serialização

### Requirement: Curva interpolada delegada ao motor

A API SHALL expor a consulta de curva interpolada em prazo arbitrário, delegando o cálculo ao motor. A API MUST NOT implementar interpolação própria.

#### Scenario: Interpolação atendida

- **WHEN** a curva interpolada é solicitada para um prazo arbitrário
- **THEN** a API SHALL delegar ao motor e retornar o resultado com a versão de curva usada

#### Scenario: Motor indisponível

- **WHEN** o serviço de interpolação está indisponível
- **THEN** a resposta SHALL distinguir essa condição de "curva inexistente", e as consultas não interpoladas SHALL continuar funcionando

#### Scenario: Prazo fora do intervalo sob política estrita

- **WHEN** o prazo solicitado está fora do intervalo dos vértices e a política da curva é estrita
- **THEN** a resposta SHALL ser um erro nomeando o prazo pedido e o intervalo disponível

### Requirement: Histórico de versões

A API SHALL retornar o histórico de versões publicadas de uma curva em uma data e momento, com número da versão, estado, instante de publicação, execução que a gerou e modelo utilizado.

#### Scenario: Data republicada

- **WHEN** o histórico de uma data republicada é consultado
- **THEN** SHALL listar todas as versões, com a corrente marcada como publicada e as anteriores como substituídas

#### Scenario: Causa da diferença visível

- **WHEN** duas versões da mesma data foram construídas com modelos diferentes
- **THEN** o histórico SHALL mostrar o modelo de cada uma, tornando a causa da diferença explícita

### Requirement: Origem da versão nas consultas

Toda resposta que retorne uma versão de curva SHALL informar a sua origem entre `CALCULADA`, `IMPORTADA` e `CARREGADA`.

#### Scenario: Curva carregada identificada

- **WHEN** uma versão carregada manualmente é consultada
- **THEN** a resposta SHALL indicar a origem `CARREGADA`, e a procedência SHALL trazer arquivo, autor e justificativa

#### Scenario: Origem nunca omitida

- **WHEN** qualquer versão é retornada
- **THEN** a origem SHALL constar, para que consumidor algum confunda curva calculada com curva digitada

### Requirement: Consulta de procedência

A API SHALL retornar a procedência de uma versão de curva. Para curva construída, SHALL informar execução, versão da definição, modelo, checksum do modelo quando Groovy, referências dos insumos e hash do conjunto. Para curva importada, SHALL informar o lote de ingestão, o arquivo de origem e seu hash.

#### Scenario: Procedência de curva construída

- **WHEN** a procedência de uma curva construída é consultada
- **THEN** a resposta SHALL permitir reconstituir quais insumos, qual definição e qual modelo produziram aqueles vértices

#### Scenario: Procedência de curva importada

- **WHEN** a procedência de uma curva importada é consultada
- **THEN** a resposta SHALL identificar o conteúdo da fonte que originou os vértices, com o campo de modelo vazio

### Requirement: Comparação entre curvas

A API SHALL comparar duas curvas para a mesma data de referência e momento, retornando, por prazo, o valor de cada uma e a diferença. Prazos presentes em apenas uma das curvas SHALL ser sinalizados explicitamente e MUST NOT ser preenchidos por interpolação.

#### Scenario: Construída contra importada

- **WHEN** a comparação é solicitada entre a curva construída e a importada da mesma data
- **THEN** a resposta SHALL listar prazo a prazo os dois valores e a diferença

#### Scenario: Prazos não coincidentes

- **WHEN** uma das curvas tem prazos que a outra não tem
- **THEN** esses prazos SHALL ser sinalizados como presentes em apenas uma curva, sem valor interpolado para a outra

#### Scenario: Uma das curvas sem publicação na data

- **WHEN** uma das curvas não tem versão publicada para a data
- **THEN** a resposta SHALL informar qual está ausente, e MUST NOT substituí-la pela versão de outra data

### Requirement: Fronteira de escrita

O `curve-api` MUST NOT escrever em `versao_curva`, `vertice_curva`, `procedencia_curva`, `ponto_dado_mercado`, `lote_ingestao` ou `execucao_curva`. Sua única escrita permitida é o cadastro de definição de curva.

#### Scenario: Tentativa de escrita fora da fronteira

- **WHEN** o serviço tenta gravar em uma tabela de curva publicada
- **THEN** o teste de fronteira SHALL falhar o build, e a credencial de banco do serviço SHALL NOT conceder a permissão

### Requirement: Contrato OpenAPI versionado

O contrato da API SHALL residir em `contracts/openapi`, versionado no repositório, e SHALL ser verificado por teste contra a implementação.

#### Scenario: Implementação diverge do contrato

- **WHEN** um endpoint passa a devolver um campo ausente do contrato
- **THEN** o teste de conformidade SHALL falhar no build
