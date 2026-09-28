## Purpose

Ajusta o schema das tabelas de curvas de mercado em dois pontos: o ticker Bloomberg completo em `tBbergCurvaPrimr` e a remoção de `tCurvaData`, que não é usada. A curva construída (os vértices) fica em `tDadoVertcCurva`, e a curva interpolada, em `tDadoCurva`, sem tabela a mais.

## ADDED Requirements

### Requirement: Ticker Bloomberg completo
`tBbergCurvaPrimr.cTickerBberg` SHALL ser `VARCHAR(50) NULL`, e não mais `CHAR(20)`, para guardar o ticker exatamente como a Bloomberg publica (ex.: `S0490Z 15M BLC2 Curncy`), sem espaços de preenchimento. Nenhum outro campo da tabela muda.

#### Scenario: Nó da SOFR com tenor de três caracteres
- **WHEN** o feeder Bloomberg grava o nó `S0490Z 15M BLC2 Curncy` da curva `SOFR`
- **THEN** `cTickerBberg` guarda os 22 caracteres, sem corte e sem espaço à direita

### Requirement: Remoção de tCurvaData
`tCurvaData` SHALL ser removida, com a FK `FK_tDadoCurva_tCurvaData` que a ligava a `tDadoCurva`. O papel das tabelas que ficam SHALL ser:
- `tDadoVertcCurva`: a curva construída, um registro por vértice, com dias úteis, dias corridos, dias 30/360, fatores e taxa;
- `tDadoCurva`: a curva interpolada, um valor por dia corrido da data-base até o fim do domínio da curva.

`tDadoCurva` e `tDadoVertcCurva` não mudam.

#### Scenario: Tabela removida
- **WHEN** o script é aplicado
- **THEN** `tCurvaData` não existe mais, e `tDadoCurva` e `tDadoVertcCurva` continuam com as colunas, PKs e FKs do `001_SCRIPT_INICIAL.sql`

### Requirement: Script único de alteração
As alterações SHALL ser entregues num único script, `scripts/alter-banco-curvas.sql`, para o dono do schema aplicar no banco ainda sem uso, sem migração de dados: tudo numa transação e, comentada no final, a volta, que recria `tCurvaData` como no `001_SCRIPT_INICIAL.sql`. O schema de teste `db/h2/schema.sql` SHALL receber as mesmas alterações.

#### Scenario: Aplicação no banco vazio
- **WHEN** o script é executado no banco com o schema atual e sem dados
- **THEN** `cTickerBberg` fica `VARCHAR(50)` e `tCurvaData` deixa de existir, numa única transação

#### Scenario: Falha no meio
- **WHEN** um dos comandos do script falha
- **THEN** a transação é desfeita, e o schema continua como estava
