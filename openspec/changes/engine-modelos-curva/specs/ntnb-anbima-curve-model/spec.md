## Purpose

Registro do modelo na memória de cálculo (abas da planilha), entregue com a memória de cálculo desta change. O modelo em si está na change `engine-construcao-curvas`.

## ADDED Requirements

### Requirement: Memória de cálculo do modelo
O modelo SHALL registrar na memória de cálculo: na aba `Insumos`, a tabela `tAnbmaCurvaPrimr` e as colunas lidas `cTickerIndcd`, `dBaseReft`, `vVertcCurva`, `vPrecoTx`; na aba `Pontos`, as colunas extras `Vencimento nominal`, `Taxa indicativa`, `Cotacao`, `Iteracoes` e `Residuo` (`f(z_n)`). A aba `Fluxos` SHALL ter uma linha por evento de cada título, com as colunas `Titulo` (vencimento nominal), `Data nominal`, `Data pagamento`, `DU`, `Fluxo`, `Origem DF`, `DF` (calculado com o `z_n` final) e `Valor presente`.

#### Scenario: Fluxos na planilha
- **WHEN** a `NTN-B` de uma data com 13 títulos é simulada
- **THEN** a aba `Fluxos` tem uma linha por evento de cada título, e a soma de `Valor presente` de cada título é igual à `Cotacao` desse título, a menos do `Residuo`

