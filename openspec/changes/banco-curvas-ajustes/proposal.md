## Why

As changes `engine-modelos-curva`, `curves-cadastro-curvas` e `conector-b3-webhook-ingest` foram escritas sem mudar o banco, porque o schema oficial não podia ser alterado nesta fase. Dois pontos do schema, porém, atrapalham diretamente o que vem a seguir e precisam ser levados ao dono do schema:

- **Ticker Bloomberg curto demais.** `tBbergCurvaPrimr.cTickerBberg` é `CHAR(20)`. Os nós da SOFR são publicados como `S0490Z <tenor> BLC2 Curncy`, e `S0490Z 15M BLC2 Curncy` tem 22 caracteres. Sem aumentar a coluna, o feeder Bloomberg teria de guardar uma forma encurtada e inventada do ticker, diferente do que a Bloomberg publica.
- **`tCurvaData` sobrando.** A curva construída (os vértices, com dias e fatores) fica em `tDadoVertcCurva`, e a curva interpolada, em `tDadoCurva`. `tCurvaData` não é usada por ninguém e tem uma FK para `tDadoCurva` que só confunde o modelo; sai.

## What Changes

- **`tBbergCurvaPrimr.cTickerBberg`** passa de `CHAR(20)` para `VARCHAR(50)`, do tamanho de `cTickerIndcd`, guardando o ticker completo da Bloomberg sem espaços à direita.
- **`tCurvaData` removida**, com a FK `FK_tDadoCurva_tCurvaData`. `tDadoCurva` e `tDadoVertcCurva` não mudam.
- Um único script (`scripts/alter-banco-curvas.sql`) para o dono do schema aplicar no banco ainda sem uso, sem migração de dados, com a volta comentada no final. Não há schema de teste a ajustar: nenhum serviço usa H2.

## Capabilities

### New Capabilities
- `schema-curvas-mercado`: ajustes de schema nas tabelas de curvas de mercado: ticker Bloomberg completo e remoção de `tCurvaData`.

### Modified Capabilities
<!-- Nenhuma. -->

## Impact

- **Banco:** `ALTER` em `tBbergCurvaPrimr` e `DROP` de `tCurvaData`, no banco ainda vazio. Nenhuma tabela nova.
- **Engine (`engine-modelos-curva`):** o modelo `SOFR_ZERO_BLOOMBERG` lê os nós em `tBbergCurvaPrimr` (sem tabela `mkt.SofrCurveRaw`), com o ticker completo depois desta change. O engine grava a curva construída em `tDadoVertcCurva` e a interpolada em `tDadoCurva`, e não usa `tCurvaData`.
- **Feeder e processor Bloomberg:** gravam `cTickerBberg` com o ticker completo.
- **Leitores de `tCurvaData`:** nenhum. A curva interpolada vem de `tDadoCurva`, gravada pelo engine.
- **Dono do schema:** precisa aprovar e aplicar no ambiente real.
- **Fora de escopo:** os demais alvos registrados nos designs para quando o banco puder mudar (ver design, Open Questions).
