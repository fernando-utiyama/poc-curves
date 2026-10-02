## Purpose

Constrói a curva SOFR a partir das zero rates por tenor do curve member Bloomberg `S0490Z` (`S0490Z <tenor> BLC2 Curncy`), convertendo cada tenor numa data pelo calendário dos EUA, sem bootstrap. Os nós ficam na tabela bruta da Bloomberg que já existe, `tBbergCurvaPrimr`, sem tabela nova.

## ADDED Requirements

### Requirement: Leitura dos nós por tenor
O modelo `SOFR_ZERO_BLOOMBERG` SHALL exigir origem com fonte `BLOOMBERG` e produto `BLC2`; caso contrário, `CADASTRO_INVALIDO`. O modelo SHALL ler as linhas de `tBbergCurvaPrimr` com `cTickerIndcd` = nome da curva (o feeder grava os nós sob a curva de mercado ligada ao membro em `tCurvaPrvdr`) e `dBaseReft` = data-base. Cada linha é um nó:
- `cTickerBberg` = ticker da Bloomberg, no formato `{membro} {tenor}` seguido ou não da fonte e da yellow key (ex.: `S0490Z 15M BLC2 Curncy`, completo, que é o que o processor grava na coluna `VARCHAR(50)` do `001_SCRIPT_INICIAL.sql`, ou a forma curta `S0490Z 15M`, também aceita); o primeiro termo MUST ser igual ao código na fonte da origem, e o segundo é o tenor;
- `vPrecoUlt` = taxa zero em percentual ao ano, usada sem conversão.

Ticker fora desse formato ou de outro membro MUST resultar em `INSUMO_INVALIDO`. As colunas de contrato futuro (`dVctoContr`, `vPrecoMed` e as demais) MUST NOT ser usadas. O modelo MUST NOT ler `BloombergCurveRaw`.

#### Scenario: Construção da SOFR
- **WHEN** a `SOFR` de uma data-base é construída e há nós para 21 tenores distintos
- **THEN** a curva tem 21 pontos, um por tenor, com o valor publicado

#### Scenario: Valor usado diretamente
- **WHEN** o nó `5Y` tem valor 3,850
- **THEN** o ponto de 5 anos tem valor calculado 3,850, sem nenhum ajuste

### Requirement: Conversão de tenor em data
O tenor SHALL casar com a expressão `^([1-9][0-9]*)([DWMY])$`; outro formato MUST resultar em `INSUMO_INVALIDO`. Para a data-base `B`, quantidade `n` e o calendário e a `BusinessDayConvention` cadastrados, a data do ponto SHALL ser:
- `D`: `B` avançada `n` dias úteis;
- `W`: `B + 7·n` dias corridos, ajustada pela convenção;
- `M`: `B + n` meses (último dia do mês quando o dia não existe), ajustada pela convenção;
- `Y`: `B + n` anos (mesma regra), ajustada pela convenção.

A fonte não publica dias úteis: `diasUteisPublicados` é nulo, e os dias úteis gravados em `tDadoVertcCurva` são os do calendário `UnitedStates` na construção.

#### Scenario: Tenor não padronizado
- **WHEN** o tenor `15M` é convertido a partir de `B` = `2026-09-14`
- **THEN** a data é `2027-12-14`, que já é dia útil

#### Scenario: Feriado americano
- **WHEN** a data de um tenor cai num feriado do `UnitedStates`/`FederalReserve` que não é feriado no Brasil
- **THEN** a data é ajustada pelo calendário `UnitedStates`, não pelo `Brazil`

### Requirement: Regras do arquivo
Para cada situação dos nós lidos de `tBbergCurvaPrimr`, o resultado SHALL ser:

| Situação | Resultado |
|---|---|
| nenhum nó da curva na data | falha: `INSUMO_AUSENTE` |
| `cTickerBberg` fora do formato `{membro} {tenor}...`, ou de outro membro | falha: `INSUMO_INVALIDO` (nó) |
| tenor fora de `^([1-9][0-9]*)([DWMY])$` | falha: `INSUMO_INVALIDO` (nó) |
| `vPrecoUlt` nulo | falha: `INSUMO_INVALIDO` (nó) |
| o mesmo tenor mais de uma vez com o mesmo valor | fica um nó; os demais são descartados com o motivo `DUPLICADO`, no log e na memória |
| o mesmo tenor com valores diferentes | falha: `INSUMO_INVALIDO` (o tenor e os valores) |
| dois tenores diferentes que resultam na mesma data | falha: `INSUMO_INVALIDO` (os dois nós) |
| taxa negativa | constrói, sem aviso |

A fonte não publica dias úteis, então não há `CALENDARIO_DIVERGENTE` para a SOFR.

#### Scenario: Dois nós 1D idênticos
- **WHEN** a fonte tem dois nós `1D` com o mesmo valor
- **THEN** a curva tem um ponto em `1D`, e o descarte aparece no log

#### Scenario: Dois nós 1D divergentes
- **WHEN** a fonte tem dois nós `1D` com valores diferentes
- **THEN** a construção falha com `INSUMO_INVALIDO`, informando o tenor e os dois valores

### Requirement: Cadastro da SOFR
A curva SHALL ser cadastrada com:
- código `SOFR`, nome `SOFR`, origem `BLOOMBERG`/`BLC2`/`S0490Z`;
- construção `SOFR_ZERO_BLOOMBERG`, unidade `TAXA`;
- `CompoundFactor` + `Linear`, eixo `Actual360`, cotação `Actual360`/`Simple` (como a `ZUS` do Manual de Curvas B3, item 2.11, com a interpolação 1.4.11);
- calendário `UnitedStates`/`FederalReserve`/`ModifiedFollowing`;
- extrapolação de início `Disabled` e de fim `FlatValue`;
- horizonte `10Y`, 7 casas `HALF_UP`.

#### Scenario: Primeiro ponto no dia útil seguinte
- **WHEN** a `SOFR` é construída com o nó `1D`
- **THEN** o primeiro ponto é o primeiro dia útil do `UnitedStates`/`FederalReserve` após a data-base
