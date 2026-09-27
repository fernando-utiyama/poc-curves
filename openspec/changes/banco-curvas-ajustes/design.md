## Context

- Schema oficial replicado no poc pela `V22__legado_schema_curvas_mercado.sql`:
  - `tBbergCurvaPrimr`: `cldtfdUnic INT NOT NULL` (PK nonclustered, sem identity), `cTickerIndcd VARCHAR(50)` (FK para `tCurvaMercd`), `cTickerBberg CHAR(20)`, `dBaseReft`, `dVctoContr`, `vPrecoUlt DECIMAL(28,12)` e colunas de contrato futuro;
  - `tCurvaData`: `dBaseReft`, `cTickerIndcd`, `dVertcReft`, `vPrecoTx`, PK nonclustered nessas três colunas e `FK_tDadoCurva_tCurvaData` para a PK de `tDadoCurva`.
- A `V23` criou `seq_tbtrscurvaprimr_cidtfdunic` para o bruto da B3, pelo mesmo motivo do `cldtfdUnic` sem identity.
- As outras changes foram escritas sem mudança de schema e registraram "alvos ideais" nos seus designs.

## Goals / Non-Goals

**Goals:**
- Guardar o ticker Bloomberg como publicado e gerar o id do bruto sem `MAX + 1`.
- Permitir que a curva diária seja gravada em `tCurvaData`.
- Um único script, aplicado no banco ainda sem uso e sem migração de dados.

**Non-Goals:**
- Gravar a curva diária: é uma change futura do engine.
- Os demais alvos registrados (ver Open Questions).
- Tabela nova para os nós da SOFR: não é necessária.

## Decisions

### D1. SOFR na tabela Bloomberg existente
Os nós da SOFR (`S0490Z <tenor> BLC2 Curncy`) cabem em `tBbergCurvaPrimr`: `cTickerIndcd` = curva de mercado `SOFR`, `cTickerBberg` = ticker completo (o tenor é o segundo termo), `dBaseReft` = data-base, `vPrecoUlt` = taxa zero em percentual, demais colunas nulas. As linhas da SOFR ficam separadas das de contrato futuro pelo `cTickerIndcd`. O único impedimento era o tamanho do ticker. **Alternativa rejeitada:** a tabela nova `mkt.SofrCurveRaw` do design anterior do engine, que criava tabela no schema oficial sem necessidade.

### D2. `VARCHAR(50)`, não um tamanho justo
50 é o tamanho de `cTickerIndcd` e cobre os tickers Bloomberg usuais (membro, tenor, fonte de preço e yellow key). `VARCHAR` evita os espaços à direita do `CHAR`, que obrigam todo leitor a aparar o valor.

### D3. Sequência, sem identity
Mesmo padrão da `V23`: a sequência dá ids únicos sob concorrência sem mudar o tipo da coluna nem o contrato da tabela. Começa em 1, porque o banco está vazio.

### D4. Curva diária presa à curva, não aos pontos
A FK antiga exigiria que toda data da curva diária fosse um ponto gravado, o que só vale nos vértices. Ligar `tCurvaData` a `tCurvaMercd` mantém a integridade que importa (a curva existe) e deixa pontos e curva diária independentes: a curva diária pode ser expurgada sem tocar nos pontos, e os pontos podem ser recalculados, com a curva diária regravada na mesma transação pelo engine (change futura).

### D5. PK mantida, agora clustered
A PK (`dBaseReft`, `cTickerIndcd`, `dVertcReft`) já expressa a regra certa: um valor por curva, data-base e data. Com a curva diária sendo o maior volume do modelo (cerca de 12.600 linhas por curva e data-base em 50 anos), a PK clustered deixa contíguas as linhas lidas juntas (uma curva numa data-base) e torna o expurgo por faixa de `dBaseReft` barato. A troca é feita com o banco ainda vazio, sem custo. **Alternativa rejeitada:** ordem (`cTickerIndcd`, `dBaseReft`, `dVertcReft`), que favorece leitura de uma curva em muitas datas-base mas deixa o expurgo por data-base espalhado.

## Risks / Trade-offs

- **Outro sistema pode depender de `CHAR(20)` ou da FK antiga.** → Confirmar com o dono do schema ao pedir o ALTER; a volta está comentada no final do script.
- **O banco deixa de estar vazio antes do ALTER.** → O script não converte dados; se já houver linhas, reavaliar (ticker com espaços à direita, sequência começando acima do maior id) antes de aplicar.

## Migration Plan

1. Pedir ao dono do schema a execução de `scripts/alter-banco-curvas.sql` no banco ainda sem uso. O script foi testado em SQL Server 2022 sobre o schema da `V22`: aplicação, ticker de 22 caracteres gravado, id pela sequência, curva diária fora dos vértices aceita, e a volta comentada restaurando o schema anterior.
2. Na mesma execução, o `GRANT` da sequência ao login do gravador Bloomberg (trocar o marcador no script).
3. Ajustar `db/h2/schema.sql` com as mesmas alterações.
4. **Rollback:** a volta comentada no final do script.

## Open Questions

- Incluir aqui, ou em change própria, os outros alvos já registrados para quando o banco puder mudar:
  - tabelas de auditoria (`tAuditCurva`, `tHistDadoCurva`) e de histórico do cadastro, hoje substituídas por log;
  - `tParmConfgCurva` com PK (`cldtfdConfg`, `cConfgIdtfd`), no lugar do JSON em `tConfgCurva.cModDado`;
  - sequência para `tCurvaPrvdr.cldtfdUnic`, no lugar de `MAX + 1` com trava;
  - `READ_COMMITTED_SNAPSHOT`, para leituras não esperarem uma reconstrução;
  - unicidade de `tCurvaMercd.cTickerIdtfdUnic` (código), hoje garantida só pelo serviço.
- Tamanho de `cTickerBberg` para outros produtos Bloomberg além da SOFR, se algum passar de 50.

## Nota: não bloqueia a homologação
A lista de tickers da SOFR está fixa no conector. Até a homologação, ele grava a forma curta (ex.: `S0490Z 15M`), que cabe no `CHAR(20)` atual; para produção, com este ALTER aplicado, o ticker fica livre e passa a ser gravado completo. O modelo do engine lê as duas formas, então a homologação não depende deste ALTER.
