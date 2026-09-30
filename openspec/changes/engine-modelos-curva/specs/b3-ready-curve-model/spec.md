## Purpose

Registro do modelo na memória de cálculo (abas da planilha), entregue com a memória de cálculo desta change. O modelo em si está na change `engine-construcao-curvas`.

## ADDED Requirements

### Requirement: Memória de cálculo do modelo
O modelo SHALL registrar na memória de cálculo: na aba `Insumos`, a tabela `tBtrsCurvaPrimr` e as colunas lidas `cTickerIndcd`, `dBaseReft`, `cDiaCorri`, `cDiaUtil`, `vPrecoTx`; na aba `Pontos`, as colunas extras `DC publicado` e `DU publicado`. O modelo não registra fluxos.

#### Scenario: Divergência visível na planilha
- **WHEN** a simulação da `PRE` tem calendário divergente
- **THEN** a aba `Insumos` mostra a linha com o `cDiaUtil` publicado, e a aba `Eventos` mostra o aviso com o `DU` calculado

