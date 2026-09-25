## Purpose

Constrói as curvas B3 do primeiro objetivo (PRE, DCL, PTX, DPL e INP) a partir dos vértices prontos do arquivo Taxas de Mercado para Swaps (`TaxaSwap.txt`), sem reimplementar a metodologia da B3. O vínculo entre cada curva e o seu código na fonte fica no cadastro, e o conector entrega cada curva com o seu próprio código.

## ADDED Requirements

### Requirement: Construção a partir dos vértices prontos, pela origem cadastrada
O modelo de construção `PRONTA_TS_B3` SHALL montar a curva da data com os vértices publicados no `TaxaSwap.txt` para o **código na fonte cadastrado** na curva e para a data-base. Ele usa todos os vértices padronizados do arquivo, com os dias corridos e os dias úteis como publicados. O modelo MUST NOT conter código de curva fixo: o código a buscar vem sempre do cadastro. O valor de cada vértice MUST ser usado como publicado, respeitando o sinal, sem recalcular a partir de contratos, indicadores ou outras curvas.

#### Scenario: Curva de mercado vinculada ao DIxPRE
- **WHEN** a curva `PRE` está cadastrada com origem `TS_B3` e código na fonte `PRE` (DIxPRE), e é construída para `2026-09-14`
- **THEN** a curva da data tem os 278 vértices do código `PRE` do arquivo, o primeiro com 1 dia corrido, 1 dia útil e taxa 13,9000000

#### Scenario: Nome da curva diferente do código na fonte
- **WHEN** uma curva `DI_MERCADO` é cadastrada com origem `TS_B3` e código na fonte `PRE`
- **THEN** `DI_MERCADO` é construída com os mesmos vértices do código `PRE` do arquivo

#### Scenario: Valor negativo preservado
- **WHEN** a curva `DCL` de `2026-09-14` é construída e o primeiro vértice publicado vale -11,7960000
- **THEN** o primeiro vértice da curva da data vale -11,7960000

#### Scenario: Código ausente no arquivo da data
- **WHEN** a curva `DPL` é construída para uma data cujo arquivo não traz vértices do código cadastrado
- **THEN** a construção falha informando `DPL`, a fonte `TS_B3`, o código na fonte e a data

### Requirement: Cadastro inicial das curvas do primeiro objetivo
As cinco curvas SHALL ser cadastradas com construção `PRONTA_TS_B3`, origem `TS_B3` e calendário `Brazil`/`Settlement`/`Following`, com a semântica abaixo, que reproduz o Manual de Curvas B3. A coluna "Curva" é o código usado nas rotas por código; o nome de exibição é usado nas rotas por nome:

| Curva | Nome de exibição | Código na fonte | Unidade | Grandeza + interpolador | Tempo | Cotação | Extrap. fim | Função B3 |
|---|---|---|---|---|---|---|---|---|
| `PRE` | DIxPRE | `PRE` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Business252`, `Compounded`, `Annual` | `FlatForward` | 1.4.2 / 1.4.6 |
| `DCL` | Cupom limpo de dólar | `DCL` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Actual360`, `Simple` | `FlatForward` | 1.4.3 / 1.4.10 |
| `DPL` | Cupom Limpo DI X IPCA | `DPL` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Business252`, `Compounded`, `Annual` | `FlatForward` | 1.4.2 / 1.4.6 |
| `INP` | IBOVESPA | `INP` | `PONTOS` | `Price` + `LogLinear` | `Business252` | — | `FlatValue` | 1.4.5 / 1.4.8 |
| `PTX` | PTAX - USD | `PTX` | `PRECO` | `Price` + `LogLinear` | `Business252` | — | `Disabled` | sem função própria: no manual, cada vértice é derivado de PRE e DOL |

A extrapolação de início SHALL ser `Disabled` para as cinco curvas: o primeiro vértice publicado já está em 1 dia útil. O horizonte e o arredondamento SHALL ser definidos no cadastro de cada curva. Para a `PTX`, com fim `Disabled`, o horizonte MUST NOT passar do último vértice publicado.

#### Scenario: Interpolação da DCL
- **WHEN** a curva `DCL` é interpolada entre dois vértices publicados
- **THEN** o valor é o da Interpolação Flat Forward 252 com Convenção Linear do manual (item 1.4.3)

#### Scenario: PTX sem fatores de juros
- **WHEN** a curva `PTX` é gravada
- **THEN** os pontos trazem preço em R$/US$, sem fator diário nem acumulado

### Requirement: Tipo de curva pelo código exato no conector
O conector SHALL classificar cada linha do `TaxaSwap.txt` pelo código exato da curva (posições 22 a 26 do leiaute oficial). `PRE`, `DCL`, `PTX`, `DPL`, `INP`, `ZUS` e `TIC` SHALL ser publicados cada um com o seu próprio código. A descrição da linha MUST NOT ser usada para decidir o tipo quando o código for conhecido. As linhas de títulos públicos (códigos numéricos, como `076`) MUST continuar separadas das curvas de mesmo nome (como `TIC`, descrita "NTN-B").

#### Scenario: DCL não vira DOL
- **WHEN** o conector processa as linhas com código `DCL` e descrição `CUPOM LIMPO - S`
- **THEN** as linhas são publicadas com o código `DCL`

#### Scenario: Curvas de outros indexadores não viram PRE
- **WHEN** o conector processa as linhas com código `SLP` (`SELICxPRE`) e `TFP` (`TBFxPRE`)
- **THEN** nenhuma delas é publicada como `PRE`

#### Scenario: PTX e INP publicados
- **WHEN** o conector processa as linhas com código `PTX` e `INP`
- **THEN** as linhas são publicadas com os códigos `PTX` e `INP`, e não descartadas como tipo desconhecido

#### Scenario: Título e curva NTN-B separados
- **WHEN** o conector processa linhas do título com código `076` e da curva `TIC`, ambas descritas `NTN-B`
- **THEN** as duas são publicadas com códigos distintos, sem misturar seus vértices
