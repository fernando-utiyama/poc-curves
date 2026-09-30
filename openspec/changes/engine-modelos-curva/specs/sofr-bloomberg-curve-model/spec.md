## Purpose

Registro do modelo na memória de cálculo (abas da planilha), entregue com a memória de cálculo desta change. O modelo em si está na change `engine-construcao-curvas`.

## ADDED Requirements

### Requirement: Memória de cálculo do modelo
O modelo SHALL registrar na memória de cálculo: na aba `Insumos`, a tabela `tBbergCurvaPrimr` e as colunas lidas `cldtfdUnic`, `cTickerIndcd`, `cTickerBberg`, `dBaseReft`, `vPrecoUlt`, mais o tenor extraído; na aba `Pontos`, as colunas extras `Tenor` e `Data nao ajustada`. O modelo não registra fluxos.

#### Scenario: Ajuste visível
- **WHEN** a data de um tenor é ajustada por feriado
- **THEN** a aba `Pontos` mostra a data não ajustada e a data do ponto

