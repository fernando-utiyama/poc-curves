## Purpose

Mantém em `tCurvaMercd` o resumo da última construção de cada curva (`dBaseReft` e `cUsuarCalc`). O registro de cada gravação no log e o arquivo de auditoria estão na change `engine-modelos-curva`.

## ADDED Requirements

### Requirement: Resumo da última construção em tCurvaMercd
Na mesma transação de uma construção ou reconstrução bem-sucedida, o engine SHALL atualizar, na linha da curva em `tCurvaMercd` (já travada pela construção):
- `dBaseReft` = a maior entre a data-base construída e o valor atual (nunca retrocede);
- `cUsuarCalc` = usuário ou identidade de serviço que construiu.

O engine MUST NOT alterar nenhuma outra coluna de `tCurvaMercd`. A edição manual de pontos no `services/curves` também não altera essas colunas. O catálogo de curvas SHALL devolver `dBaseReft` como `ultimaDataBase`.

#### Scenario: Reconstrução de data antiga
- **WHEN** a `PRE` tem `dBaseReft` = `2026-09-14` e a data `2026-09-10` é reconstruída
- **THEN** `dBaseReft` continua `2026-09-14`, e `cUsuarCalc` passa a ser o usuário da reconstrução
