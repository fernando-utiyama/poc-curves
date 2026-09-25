## Purpose

Constrói a curva NTN-B a partir das taxas indicativas por título publicadas pela ANBIMA, convertendo yield-to-maturity de cada papel em taxa zero por bootstrap sequencial — sem reimplementar a metodologia de precificação da ANBIMA, e sem depender de vértices padronizados de fonte alguma.

## ADDED Requirements

### Requirement: Bootstrap a partir das taxas indicativas por título
O modelo de construção `NTNB_BOOTSTRAP_ANBIMA` SHALL montar o dado curva da `NTN-B` a partir das taxas indicativas por título publicadas pela ANBIMA na data-base, convertendo cada YTM em taxa zero. Para cada título, o modelo MUST montar o fluxo de caixa real (cupom semestral + principal na data de vencimento) segundo a estrutura de cupom cadastrada daquela série. Os títulos SHALL ser processados em ordem crescente de vencimento; ao resolver a taxa zero de um título, os cupons que caem em datas anteriores ao seu vencimento MUST ser descontados pelas taxas zero já resolvidas nos vencimentos anteriores (interpoladas quando a data do cupom não coincidir com um vencimento já resolvido).

#### Scenario: Dois títulos, sem cupom intermediário
- **WHEN** o título mais curto vence antes do primeiro cupom do título seguinte
- **THEN** a taxa zero do título mais curto é resolvida diretamente da sua taxa indicativa, sem desconto de cupom intermediário

#### Scenario: Título com cupom em data já resolvida
- **WHEN** um título paga cupom numa data que coincide com o vencimento de um título mais curto já resolvido
- **THEN** esse cupom é descontado pela taxa zero exata daquele vencimento, sem interpolação

#### Scenario: Título com cupom em data não resolvida
- **WHEN** um título paga cupom numa data entre dois vencimentos já resolvidos
- **THEN** a taxa de desconto daquele cupom é interpolada entre as duas taxas zero adjacentes, pelo modelo de interpolação cadastrado para a curva

### Requirement: Pontos do dado curva em datas reais de vencimento
Os pontos do dado curva da `NTN-B` SHALL usar a data real de vencimento de cada título, não uma grade de vértices padronizada. O dado curva MAY ter um número de pontos diferente a cada data-base, conforme os títulos em circulação naquele dia.

#### Scenario: Mudança na quantidade de títulos
- **WHEN** a `NTN-B` é construída em duas datas-base diferentes, com um título a mais vencido na segunda
- **THEN** o dado curva da segunda data tem um ponto a menos que o da primeira

### Requirement: Taxa real, base 252, capitalização composta
A curva `NTN-B` SHALL ser cadastrada como taxa real (não nominal), convenção 252 dias úteis, capitalização composta anual — sem ajuste de inflação embutido no bootstrap, porque a taxa indicativa da ANBIMA já é uma taxa real.

#### Scenario: Cadastro da NTN-B
- **WHEN** a curva `NTN-B` é cadastrada
- **THEN** a unidade é `TAXA`, o `DayCounter` do eixo é `Business252`, e a cotação é `Business252`/`Compounded`/`Annual`

### Requirement: Estrutura de cupom por série do título
A frequência de cupom, a taxa de cupom real e a data de vencimento de cada série de NTN-B SHALL ser dado de configuração, não valor fixo no código. Uma série nova de título MUST poder ser adicionada sem alteração de código.

#### Scenario: Série nova cadastrada
- **WHEN** uma nova série de NTN-B é cadastrada com sua data de vencimento e taxa de cupom
- **THEN** o próximo bootstrap da `NTN-B` já inclui essa série, sem deploy

### Requirement: Título sem preço na data não interrompe o bootstrap
Se um título em circulação não tiver taxa indicativa publicada na data-base (feriado, ausência de negociação), o bootstrap MUST excluir esse título da construção daquela data e seguir com os demais. A construção MUST NOT falhar só por causa de um título sem preço, desde que reste pelo menos um título para formar a curva.

#### Scenario: Um título sem preço
- **WHEN** a `NTN-B` é construída numa data em que um dos títulos em circulação não tem taxa indicativa publicada
- **THEN** o dado curva é montado com os demais títulos, sem o título ausente

#### Scenario: Nenhum título com preço
- **WHEN** nenhum título em circulação tem taxa indicativa publicada na data
- **THEN** a construção falha informando `NTN-B` e a data, como qualquer outra curva sem insumo (requisito "Insumo ausente interrompe a construção" do `curve-build-pipeline`)

### Requirement: Determinismo do bootstrap
Com as mesmas taxas indicativas de entrada e a mesma estrutura de cupom cadastrada, o bootstrap MUST produzir exatamente as mesmas taxas zero.

#### Scenario: Duas construções idênticas
- **WHEN** a `NTN-B` da mesma data é construída duas vezes sem mudança de taxa indicativa ou de estrutura de cupom
- **THEN** os pontos do dado curva são idênticos nas duas construções
