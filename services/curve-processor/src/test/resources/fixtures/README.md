# Proveniência das fixtures

Recortes reais extraídos pelo Claude, em 2026-08-22, do arquivo de produção
da B3 fornecido pelo usuário (`docs/pesquisa-pregao.zip`, pregão de
2026-08-21, obtido em "Pesquisa por Pregão" — boletim diário). O mesmo
arquivo-fonte já é usado pelas fixtures de `services/feeder-marketdata`.

Encoding original: UTF-8 com BOM. Separador decimal nos campos numéricos
estruturados verificados: ponto (`.`) — não foi encontrado nenhum campo
numérico estruturado usando vírgula nestes dois datasets (vírgula aparece
só em campos de texto livre, ex. `<Desc>`, fora do escopo destas fixtures).

## `bvbg086_di1_4blocos.xml`

4 elementos `<BizGrp>` reais, extraídos do arquivo completo
`PR260821.zip > BVBG.086.01_BV000328...840237278.xml` (175.506.347 bytes,
76.015 elementos `<BizGrp>` no arquivo original — ver
`services/feeder-marketdata/fixtures/README.md` para a mesma medição).

- 3 contratos futuros de DI1 (`DI1Z28`, `DI1J30`, `DI1V31`), cada um com
  `AdjstdQtTax` (taxa de ajuste do dia) populado — é o campo usado pelo
  parser de BVBG.086 como `valor` de `ponto_dado_mercado`.
- 1 registro de opção sobre ação (`TTENT131`), sem `AdjstdQtTax` — inclui
  de propósito um instrumento fora do escopo do parser (futuro de juros),
  para provar que o parser ignora silenciosamente registros sem o campo
  esperado em vez de falhar o bloco inteiro.

## `bvbg028_di1_3blocos.xml`

3 elementos `<BizGrp>` reais do cadastro de instrumentos, mesmo pregão,
`IN260821.zip > BVBG.028.02_BV000327...117330691294583.xml` (800.282.039
bytes, 223.700 elementos `<BizGrp>` no arquivo original), para os mesmos 3
contratos de DI1 do fixture acima — inclui `XprtnDt` (vencimento real) e
`WrkgDays` (dias úteis até o vencimento, já calculado pela própria B3 na
data do pregão).
