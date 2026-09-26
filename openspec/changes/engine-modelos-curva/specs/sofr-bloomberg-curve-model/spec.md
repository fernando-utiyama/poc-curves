## Purpose

Constrói a curva SOFR a partir das zero rates por tenor do curve member Bloomberg `S0490Z` (`S0490Z <tenor> BLC2 Curncy`), convertendo cada tenor numa data pelo calendário dos EUA, sem bootstrap e sem usar o formato de contrato futuro (`BloombergCurveRaw`).

## ADDED Requirements

### Requirement: Leitura dos nós por tenor
O modelo `SOFR_ZERO_BLOOMBERG` SHALL exigir origem com fonte `BLOOMBERG` e produto `ZR`; caso contrário, `CADASTRO_INVALIDO`. O modelo SHALL ler a tabela `mkt.SofrCurveRaw` (colunas `curve_member`, `tenor`, `ref_date`, `valor DECIMAL(28,12)`) com `curve_member` = código na fonte e `ref_date` = data-base. Cada linha é um nó; o valor é a taxa zero em percentual ao ano, usada sem conversão. O modelo MUST NOT ler `BloombergCurveRaw` nem `tBbergCurvaPrimr`.

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

#### Scenario: Tenor não padronizado
- **WHEN** o tenor `15M` é convertido a partir de `B` = `2026-09-14`
- **THEN** a data é `2027-12-14`, que já é dia útil

#### Scenario: Feriado americano
- **WHEN** a data de um tenor cai num feriado do `UnitedStates`/`FederalReserve` que não é feriado no Brasil
- **THEN** a data é ajustada pelo calendário `UnitedStates`, não pelo `Brazil`

### Requirement: Validação e duplicidade
A construção MUST falhar com:
- `INSUMO_AUSENTE`, se não houver nenhum nó;
- `INSUMO_INVALIDO`, informando os nós envolvidos, se um valor for nulo, se o mesmo tenor aparecer com valores diferentes ou se dois tenores diferentes resultarem na mesma data.

Se o mesmo tenor aparecer mais de uma vez com o mesmo valor, SHALL ficar um nó, e os demais SHALL ser descartados com o motivo `DUPLICADO`, registrado no log e na memória.

#### Scenario: Dois nós 1D idênticos
- **WHEN** a fonte tem dois nós `1D` com o mesmo valor
- **THEN** a curva tem um ponto em `1D`, e o descarte aparece no log e na aba `Insumos`

#### Scenario: Dois nós 1D divergentes
- **WHEN** a fonte tem dois nós `1D` com valores diferentes
- **THEN** a construção falha com `INSUMO_INVALIDO`, informando o tenor e os dois valores

### Requirement: Memória de cálculo do modelo
O modelo SHALL registrar na memória de cálculo: na aba `Insumos`, a tabela `mkt.SofrCurveRaw` e as colunas lidas `curve_member`, `tenor`, `ref_date`, `valor`; na aba `Pontos`, as colunas extras `Tenor` e `Data nao ajustada`. O modelo não registra fluxos.

#### Scenario: Ajuste visível
- **WHEN** a data de um tenor é ajustada por feriado
- **THEN** a aba `Pontos` mostra a data não ajustada e a data do ponto

### Requirement: Cadastro da SOFR
A curva SHALL ser cadastrada com:
- código `SOFR`, nome `SOFR`, origem `BLOOMBERG`/`ZR`/`S0490Z`;
- construção `SOFR_ZERO_BLOOMBERG`, unidade `TAXA`;
- `CompoundFactor` + `Linear`, eixo `Actual360`, cotação `Actual360`/`Simple` (como a `ZUS` do Manual de Curvas B3, item 2.11, com a interpolação 1.4.11);
- calendário `UnitedStates`/`FederalReserve`/`ModifiedFollowing`;
- extrapolação de início `Disabled` e de fim `FlatValue`;
- horizonte `10Y`, 7 casas `HALF_UP`.

#### Scenario: Primeiro ponto no dia útil seguinte
- **WHEN** a `SOFR` é construída com o nó `1D`
- **THEN** o primeiro ponto é o primeiro dia útil do `UnitedStates`/`FederalReserve` após a data-base
