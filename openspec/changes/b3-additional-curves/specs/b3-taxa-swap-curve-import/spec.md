## Purpose

Trazer para a plataforma, como curvas publicadas e consultáveis, as curvas de swap adicionais que a B3 já calcula e distribui prontas (DCL, PTX, INP, DPL), a partir do arquivo oficial "Mercado Derivativos – Taxas de Mercado para Swaps" (`TaxaSwap.txt`), sem depender de fontes externas que o projeto não ingere.

## ADDED Requirements

### Requirement: Aquisição do arquivo de taxas de swap

O feeder SHALL adquirir o arquivo de taxas de swap da B3 para uma data de pregão, seguindo o mesmo contrato de disponibilidade e classificação de erro já usado para os demais arquivos compactados da B3 (distinção entre dia sem pregão, dado ainda não divulgado e fonte indisponível).

#### Scenario: Arquivo disponível em dia de pregão

- **WHEN** o feeder é disparado para uma data de pregão em que o arquivo já foi publicado
- **THEN** ele SHALL adquirir o conteúdo e publicá-lo classificado como `payloadKind` `READY_CURVE`

#### Scenario: Dia sem pregão

- **WHEN** o feeder é disparado para uma data que não é dia de pregão B3
- **THEN** ele SHALL retornar `NO_DATA` sem consultar a fonte

#### Scenario: Dado ainda não divulgado

- **WHEN** o feeder consulta o arquivo antes do horário de divulgação e a fonte responde sem conteúdo
- **THEN** o resultado SHALL ser `NO_DATA` com motivo `NOT_YET_PUBLISHED`

### Requirement: Extração dos vértices das curvas alvo

O parser SHALL localizar, dentro do arquivo de taxas de swap, apenas os registros pertencentes aos códigos de curva mapeados no catálogo (`DCL`, `PTX`, `INP`, `DPL`, e `PRE` para validação cruzada), extraindo prazo (dias úteis e dias corridos) e o valor publicado (taxa ou preço, conforme a curva) de cada vértice. Registros de códigos de curva não mapeados no catálogo SHALL ser ignorados sem gerar erro.

#### Scenario: Código de curva mapeado

- **WHEN** o arquivo contém vértices para um código de curva com `definicao_curva` cadastrada
- **THEN** o parser SHALL extrair todos os vértices daquele código, na ordem de prazo crescente

#### Scenario: Código de curva fora do escopo

- **WHEN** o arquivo contém vértices de um código de curva sem `definicao_curva` cadastrada (ex.: os demais índices e moedas presentes no mesmo arquivo)
- **THEN** o parser SHALL ignorar esses registros e MUST NOT falhar nem gerar evento para eles

#### Scenario: Curva alvo ausente na publicação do dia

- **WHEN** o arquivo é publicado sem nenhum registro para um código de curva mapeado
- **THEN** o processamento daquele código SHALL ir para a dead-letter nomeando a curva e a data, sem afetar os demais códigos do mesmo arquivo

### Requirement: Validação do layout por oráculo cruzado

Como o layout de largura fixa do arquivo de taxas de swap não é publicamente documentado pela B3 no projeto, os vértices extraídos para a curva PRE SHALL ser usados como oráculo: eles SHALL ser idênticos, após a mesma política de arredondamento, aos vértices de PRE já publicados pela mesma data de pregão via curva pronta (`B3_REFERENCE_RATES`).

#### Scenario: Consistência entre as duas fontes de PRE

- **WHEN** o arquivo de taxas de swap e a curva pronta de referência são adquiridos para a mesma data de pregão
- **THEN** os vértices de PRE extraídos de ambas as fontes SHALL ser idênticos, prazo a prazo e dígito a dígito após arredondamento

#### Scenario: Divergência detectada

- **WHEN** os vértices de PRE extraídos do arquivo de taxas de swap divergem dos vértices de PRE da curva pronta de referência para a mesma data
- **THEN** o parser SHALL falhar nomeando os prazos divergentes, e MUST NOT publicar nenhuma curva daquela execução

### Requirement: Uma aquisição, múltiplas curvas publicadas

Uma única aquisição do arquivo de taxas de swap SHALL poder originar a publicação de mais de uma curva (uma por código de curva mapeado presente no arquivo), preservando individualmente a proveniência de cada uma.

#### Scenario: Publicação independente por curva

- **WHEN** o arquivo do dia contém vértices válidos para DCL, PTX, INP e DPL
- **THEN** cada uma SHALL ser publicada como uma versão de curva independente, e a falha ao publicar uma delas MUST NOT impedir a publicação das demais
