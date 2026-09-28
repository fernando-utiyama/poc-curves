## Context

- Schema oficial, o do `001_SCRIPT_INICIAL.sql`:
  - `tBbergCurvaPrimr`: `cldtfdUnic INT NOT NULL` (PK nonclustered, sem identity), `cTickerIndcd VARCHAR(50)` (FK para `tCurvaMercd`), `cTickerBberg CHAR(20)`, `dBaseReft`, `dVctoContr`, `vPrecoUlt DECIMAL(28,12)` e colunas de contrato futuro;
  - `tCurvaData`: `dBaseReft`, `cTickerIndcd`, `dVertcReft`, `vPrecoTx`, PK nonclustered nessas três colunas e `FK_tDadoCurva_tCurvaData` para a PK de `tDadoCurva`; não é usada.
  - `tDadoVertcCurva` (curva construída) e `tDadoCurva` (curva interpolada), com a mesma PK (`dBaseReft`, `cTickerIndcd`, `dVertcReft`).
- As outras changes foram escritas sem mudança de schema e registraram "alvos ideais" nos seus designs.

## Goals / Non-Goals

**Goals:**
- Guardar o ticker Bloomberg como publicado.
- Tirar do schema a tabela que não é usada, `tCurvaData`.
- Um único script, aplicado no banco ainda sem uso e sem migração de dados.

**Non-Goals:**
- Os demais alvos registrados (ver Open Questions).
- Tabela nova para os nós da SOFR: não é necessária.

## Decisions

### D1. SOFR na tabela Bloomberg existente
Os nós da SOFR (`S0490Z <tenor> BLC2 Curncy`) cabem em `tBbergCurvaPrimr`: `cTickerIndcd` = curva de mercado `SOFR`, `cTickerBberg` = ticker completo (o tenor é o segundo termo), `dBaseReft` = data-base, `vPrecoUlt` = taxa zero em percentual, demais colunas nulas. As linhas da SOFR ficam separadas das de contrato futuro pelo `cTickerIndcd`. O único impedimento era o tamanho do ticker. **Alternativa rejeitada:** a tabela nova `mkt.SofrCurveRaw` do design anterior do engine, que criava tabela no schema oficial sem necessidade.

### D2. `VARCHAR(50)`, não um tamanho justo
50 é o tamanho de `cTickerIndcd` e cobre os tickers Bloomberg usuais (membro, tenor, fonte de preço e yellow key). `VARCHAR` evita os espaços à direita do `CHAR`, que obrigam todo leitor a aparar o valor.

### D3. `tCurvaData` removida
O papel das tabelas é: `tDadoVertcCurva` guarda a curva construída (os vértices, com dias úteis, corridos, 30/360, fatores e taxa), e `tDadoCurva` guarda a curva interpolada, um valor por dia corrido até o fim do domínio. `tCurvaData` repetiria a interpolada e não é usada; a FK dela para `tDadoCurva` ainda amarraria a interpolada a si mesma. Com o banco vazio, remover não custa nada. **Alternativa rejeitada (versão anterior desta change):** manter `tCurvaData` como curva diária, trocando a FK para `tCurvaMercd` e a PK para clustered.

### D5. `tAnbmaCurvaPrimr` aceita a curva zero da ANBIMA sem mudança de coluna
Verificado com o arquivo real `CurvaZero_25092026.txt`, gravado em SQL Server 2022 sobre o schema do `001_SCRIPT_INICIAL.sql`: as quatro curvas do arquivo (ETTJ IPCA com 65 vértices, ETTJ PREF e Inflação Implícita com 19, PREFIXADOS da Circular 3.361 com 10), 113 linhas no total, cabem em `tAnbmaCurvaPrimr` com `cTickerIndcd` = curva de mercado, `dBaseReft` = data de referência, `vVertcCurva` = vértice em dias úteis e `vPrecoTx` = taxa em percentual ao ano. Os parâmetros Svensson e o erro por título do arquivo não têm lugar na tabela e não são necessários para a curva por vértice.

## Risks / Trade-offs

- **Outro sistema pode depender de `CHAR(20)` ou de `tCurvaData`.** → Confirmar com o dono do schema ao pedir o ALTER; a volta, comentada no final do script, recria a tabela como no `001_SCRIPT_INICIAL.sql`.
- **`tDadoCurva` passa a ser a tabela de maior volume** (cerca de 12.400 linhas por curva e data-base na PRE, com o último vértice em 2060), com a PK nonclustered do schema. → Nada muda nesta change; a PK clustered fica registrada em Open Questions, para decidir com o dono do schema.
- **O banco deixa de estar vazio antes do ALTER.** → O script não converte dados; se já houver linhas, reavaliar (ticker com espaços à direita) antes de aplicar.

## Migration Plan

1. Pedir ao dono do schema a execução de `scripts/alter-banco-curvas.sql` no banco ainda sem uso. O script foi testado em SQL Server 2022 sobre o schema do `001_SCRIPT_INICIAL.sql`: aplicação, ticker de 22 caracteres gravado, `tCurvaData` removida com a FK, `tDadoCurva` e `tDadoVertcCurva` intactas (colunas, PKs e FKs), a volta comentada recriando `tCurvaData` como no `001_SCRIPT_INICIAL.sql` (colunas e PK nonclustered), e uma falha no `DROP TABLE` desfazendo também o `ALTER` do ticker. Retestado em 2026-09-28 com esta versão do script.
2. Ajustar `db/h2/schema.sql` com as mesmas alterações.
3. **Rollback:** a volta comentada no final do script.

## Open Questions

- Incluir aqui, ou em change própria, os outros alvos já registrados para quando o banco puder mudar:
  - tabelas de auditoria (`tAuditCurva`, `tHistDadoCurva`) e de histórico do cadastro, hoje substituídas por log;
  - `tParmConfgCurva` com PK (`cldtfdConfg`, `cConfgIdtfd`), no lugar do JSON em `tConfgCurva.cModDado`;
  - `READ_COMMITTED_SNAPSHOT`, para leituras não esperarem uma reconstrução;
  - unicidade de `tCurvaMercd.cTickerIdtfdUnic` (código), hoje garantida só pelo serviço.
- Tamanho de `cTickerBberg` para outros produtos Bloomberg além da SOFR, se algum passar de 50.
- PK de `tDadoCurva` clustered, agora que ela guarda a curva interpolada (o maior volume): pedir ou não ao dono do schema.

## Nota: não bloqueia a homologação
A lista de tickers da SOFR está fixa no conector. Até a homologação, ele grava a forma curta (ex.: `S0490Z 15M`), que cabe no `CHAR(20)` atual; para produção, com este ALTER aplicado, o ticker fica livre e passa a ser gravado completo. O modelo do engine lê as duas formas, então a homologação não depende deste ALTER.
