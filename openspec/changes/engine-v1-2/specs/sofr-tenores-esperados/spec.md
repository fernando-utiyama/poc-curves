## ADDED Requirements

### Requirement: Conjunto esperado de tenores da SOFR
O modelo `SOFR_ZERO_BLOOMBERG` SHALL construir a curva só quando os tenores lidos de `tBbergCurvaPrimr` (curva e data-base, depois de descartar o duplicado idêntico) forem exatamente o conjunto esperado do membro `S0490Z`. Se faltar ou sobrar algum tenor, a construção SHALL falhar com `INSUMO_INVALIDO`, e a mensagem MUST listar os tenores que faltam e os que sobram. O conjunto esperado SHALL ser o da fonte (lista a confirmar, ver `design.md`, pergunta 1) e MUST ser uma constante do modelo, sem tabela nem parâmetro novo.

#### Scenario: Carga completa
- **WHEN** a `SOFR` de uma data-base é construída com os nós de todos os tenores esperados
- **THEN** a curva é construída com um ponto por tenor

#### Scenario: Tenor ausente
- **WHEN** a `SOFR` é construída sem o nó `15M`
- **THEN** a construção falha com `INSUMO_INVALIDO` e a mensagem diz `faltam 15M`, e nada é gravado

#### Scenario: Tenor a mais
- **WHEN** a tabela traz um tenor que não está no conjunto esperado (por exemplo `18M`)
- **THEN** a construção falha com `INSUMO_INVALIDO` e a mensagem diz `sobram 18M`

#### Scenario: Duplicado idêntico não conta como a mais
- **WHEN** o tenor `1D` aparece duas vezes com o mesmo valor
- **THEN** fica um nó, e o conjunto continua completo
