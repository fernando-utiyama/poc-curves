## Purpose

Ajusta o schema das tabelas de curvas de mercado em dois pontos que bloqueiam o que vem a seguir: o ticker Bloomberg completo em `tBbergCurvaPrimr` e a curva diária em `tCurvaData` ligada à curva de mercado, e não aos pontos de `tDadoCurva`.

## ADDED Requirements

### Requirement: Ticker Bloomberg completo
`tBbergCurvaPrimr.cTickerBberg` SHALL ser `VARCHAR(50) NULL`, e não mais `CHAR(20)`, para guardar o ticker exatamente como a Bloomberg publica (ex.: `S0490Z 15M BLC2 Curncy`), sem espaços de preenchimento. Nenhum outro campo da tabela muda.

#### Scenario: Nó da SOFR com tenor de três caracteres
- **WHEN** o feeder Bloomberg grava o nó `S0490Z 15M BLC2 Curncy` da curva `SOFR`
- **THEN** `cTickerBberg` guarda os 22 caracteres, sem corte e sem espaço à direita

### Requirement: Curva diária ligada à curva de mercado
A FK `FK_tDadoCurva_tCurvaData`, que liga (`dBaseReft`, `cTickerIndcd`, `dVertcReft`) de `tCurvaData` a `tDadoCurva`, SHALL ser removida. `tCurvaData` SHALL ter a FK `FK_tCurvaMercd_tCurvaData` de `cTickerIndcd` para `tCurvaMercd.cTickerIndcd`. A PK SHALL continuar (`dBaseReft`, `cTickerIndcd`, `dVertcReft`), um valor por curva, data-base e data, e SHALL ser **clustered** (`XPKtCurvaData`), para leitura contígua por curva e data-base e expurgo por faixa de data-base. `tDadoCurva` não muda.

#### Scenario: Data da curva diária que não é vértice
- **WHEN** a curva diária da `PRE` de `2026-09-14` grava a data `2026-09-16`, que não é um vértice em `tDadoCurva`
- **THEN** a linha é aceita, porque `tCurvaData` só exige que a curva exista em `tCurvaMercd`

#### Scenario: Curva inexistente
- **WHEN** uma linha de `tCurvaData` é gravada com `cTickerIndcd` que não existe em `tCurvaMercd`
- **THEN** o banco recusa pela `FK_tCurvaMercd_tCurvaData`

### Requirement: Script único de alteração
As alterações SHALL ser entregues num único script, `scripts/alter-banco-curvas.sql`, para o dono do schema aplicar no banco ainda sem uso, sem migração de dados: tudo numa transação e, comentada no final, a volta. O schema de teste `db/h2/schema.sql` SHALL receber as mesmas alterações.

#### Scenario: Aplicação no banco vazio
- **WHEN** o script é executado no banco com o schema atual e sem dados
- **THEN** `cTickerBberg` fica `VARCHAR(50)`, `tCurvaData` tem só a `FK_tCurvaMercd_tCurvaData` e a PK clustered, numa única transação

#### Scenario: Falha no meio
- **WHEN** um dos comandos do script falha
- **THEN** a transação é desfeita, e o schema continua como estava
