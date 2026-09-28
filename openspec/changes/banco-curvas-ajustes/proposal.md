## Why

As changes `engine-modelos-curva`, `curves-cadastro-curvas` e `conector-b3-webhook-ingest` foram escritas sem mudar o banco, porque o schema oficial não podia ser alterado nesta fase. Dois pontos do schema, porém, atrapalham diretamente o que vem a seguir e precisam ser levados ao dono do schema:

- **Ticker Bloomberg curto demais.** `tBbergCurvaPrimr.cTickerBberg` é `CHAR(20)`. Os nós da SOFR são publicados como `S0490Z <tenor> BLC2 Curncy`, e `S0490Z 15M BLC2 Curncy` tem 22 caracteres. Sem aumentar a coluna, o feeder Bloomberg teria de guardar uma forma encurtada e inventada do ticker, diferente do que a Bloomberg publica.
- **Curva data presa aos pontos.** `tCurvaData` (a curva diária, interpolada) tem FK para `tDadoCurva` com as mesmas três colunas da PK (`dBaseReft`, `cTickerIndcd`, `dVertcReft`). Isso obriga cada data da curva diária a ser também um vértice gravado em `tDadoCurva`, o que é impossível: a curva diária tem uma linha por dia até o horizonte (cerca de 12.600 por curva e data-base em 50 anos), e os pontos são algumas centenas. Por isso o engine não grava `tCurvaData` nesta fase.

## What Changes

- **`tBbergCurvaPrimr.cTickerBberg`** passa de `CHAR(20)` para `VARCHAR(50)`, do tamanho de `cTickerIndcd`, guardando o ticker completo da Bloomberg sem espaços à direita.
- **`tCurvaData` ligada à curva de mercado, não aos pontos:** sai a FK `FK_tDadoCurva_tCurvaData` e entra `FK_tCurvaMercd_tCurvaData` (`cTickerIndcd` → `tCurvaMercd`). A PK continua (`dBaseReft`, `cTickerIndcd`, `dVertcReft`), um valor por curva, data-base e data, e passa a ser **clustered**, porque a leitura é sempre por curva e data-base e o expurgo é por faixa de data-base.
- Um único script (`scripts/alter-banco-curvas.sql`) para o dono do schema aplicar no banco ainda sem uso, sem migração de dados, com a volta comentada no final; o schema H2 de teste recebe as mesmas alterações.

## Capabilities

### New Capabilities
- `schema-curvas-mercado`: ajustes de schema nas tabelas de curvas de mercado: ticker Bloomberg completo e desacoplamento entre a curva diária e os pontos.

### Modified Capabilities
<!-- Nenhuma. -->

## Impact

- **Banco:** `ALTER` em `tBbergCurvaPrimr` e `tCurvaData`, no banco ainda vazio. Nenhuma tabela nova.
- **Engine (`engine-modelos-curva`):** o modelo `SOFR_ZERO_BLOOMBERG` lê os nós em `tBbergCurvaPrimr` (sem tabela `mkt.SofrCurveRaw`), com o ticker completo depois desta change. A gravação da curva diária em `tCurvaData` fica liberada pelo schema, mas continua fora do escopo do engine atual; entra numa change própria.
- **Feeder e processor Bloomberg:** gravam `cTickerBberg` com o ticker completo.
- **`curve-api-legado`:** lê `tCurvaData`; a troca da FK não muda a leitura.
- **Dono do schema:** precisa aprovar e aplicar no ambiente real.
- **Fora de escopo:** os demais alvos registrados nos designs para quando o banco puder mudar (ver design, Open Questions).
