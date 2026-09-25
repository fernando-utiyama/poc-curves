## Purpose

Constrói a curva SOFR a partir dos nós do curve member Bloomberg publicados por tenor, convertendo cada tenor num vértice de data pelo calendário dos EUA, sem usar o formato de contrato futuro (`BloombergCurveRaw`), que não corresponde a este dado.

## ADDED Requirements

### Requirement: Leitura dos nós por tenor, não por contrato futuro
O modelo de construção `SOFR_ZERO_BLOOMBERG` SHALL montar o dado curva da `SOFR` a partir dos nós do curve member Bloomberg (`S0490Z <tenor> BLC2 Curncy`) publicados para a data-base, um ponto por tenor. O modelo MUST NOT usar o formato de contrato futuro (`BloombergCurveRaw`: data de último negócio, data de liquidação, preço de ajuste) para esta curva.

#### Scenario: Construção da SOFR
- **WHEN** a curva `SOFR` de uma data-base é construída e existem nós para os 21 tenores publicados naquele dia
- **THEN** o dado curva tem 21 pontos, um por tenor, com a data derivada do tenor e o valor publicado

### Requirement: Conversão de tenor em vértice de data
Cada tenor (`1D`, `1M`, `3M`, `6M`, `9M`, `1Y`, `15M`, `2Y`, `3Y`, `4Y`, `5Y`, `7Y`, `10Y`, `12Y`, `15Y`, `20Y`, `25Y`, `30Y`, `40Y`, `50Y`) SHALL ser convertido num vértice de data avançando a data-base pelo tenor (quantidade + `TimeUnit`, no formato `Period` do QuantLib), ajustado pelo calendário e pela `BusinessDayConvention` cadastrados da curva. A conversão MUST aceitar qualquer combinação de quantidade e `TimeUnit`, sem se limitar a uma lista fixa de tenores comuns — cobrindo tenores não padronizados como `9M` e `15M`.

#### Scenario: Tenor padrão
- **WHEN** o tenor `10Y` é convertido a partir de uma data-base
- **THEN** o vértice resultante é a data-base avançada em 10 anos, ajustada pelo calendário

#### Scenario: Tenor não padronizado
- **WHEN** o tenor `15M` é convertido a partir de uma data-base
- **THEN** o vértice resultante é a data-base avançada em 15 meses, sem erro por não ser um tenor comum (1Y, 2Y...)

### Requirement: Calendário dos EUA para SOFR
A curva `SOFR` SHALL usar um calendário dos EUA (`UnitedStates`, mercado `FederalReserve`) para a conversão de tenor em data e para a contagem de dias da interpolação. Este calendário é distinto do `Brazil`/`Settlement` usado pelas curvas B3 e ANBIMA.

#### Scenario: Feriado americano não é feriado brasileiro
- **WHEN** a data-base mais um tenor cai num feriado dos EUA que não é feriado no Brasil
- **THEN** o vértice da `SOFR` é ajustado para o próximo dia útil pelo calendário `UnitedStates`, não pelo `Brazil`

### Requirement: Valor tratado como taxa zero, sem bootstrap adicional
O valor de cada nó publicado SHALL ser tratado como taxa zero já pronta — usado diretamente como ponto do dado curva, sem nenhuma conversão de par rate para taxa zero. `S0490Z` é a série de zero rates do SOFR do Bloomberg (confirmado; ver `design.md`), então não há par rate a converter.

#### Scenario: Valor usado diretamente
- **WHEN** o nó do tenor `5Y` publica o valor 3,850
- **THEN** o ponto de 5 anos no dado curva da `SOFR` tem taxa 3,850, sem nenhum ajuste

### Requirement: Duplicidade de tenor tratada de forma explícita
Se a fonte publicar mais de um nó para o mesmo tenor na mesma data-base, o modelo MUST aplicar uma regra determinística e documentada (ex.: descartar duplicata idêntica, ou tratar como dois instrumentos distintos por um campo adicional) — nunca descartar silenciosamente um dos dois sem registro, nem falhar a construção inteira por causa da duplicidade.

#### Scenario: Dois nós no tenor 1D com valor idêntico
- **WHEN** a fonte publica dois nós de tenor `1D` com o mesmo valor
- **THEN** o dado curva tem um único ponto em `1D`, e a duplicidade descartada é registrada no log

### Requirement: Insumo ausente interrompe a construção
Quando não houver nenhum nó publicado para a `SOFR` na data-base, a construção MUST falhar informando `SOFR` e a data, seguindo o requisito "Insumo ausente interrompe a construção" do `curve-build-pipeline`.

#### Scenario: Data sem publicação
- **WHEN** a construção da `SOFR` é pedida para uma data sem nenhum nó publicado
- **THEN** a construção falha informando `SOFR` e a data
