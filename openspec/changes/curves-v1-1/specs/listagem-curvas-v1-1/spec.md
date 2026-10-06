## ADDED Requirements

### Requirement: Dono da curva
O CRUD da curva SHALL gravar o campo `dono` (texto livre, opcional, até 50 caracteres) em `tCurvaMercd.cPprioDado` na criação e na alteração, e devolvê-lo na consulta e na listagem.

#### Scenario: Curva com dono
- **WHEN** o front cria a curva `PRE` com `dono` = `Tesouraria`
- **THEN** `cPprioDado` é gravado com `Tesouraria`, e a consulta da curva devolve `dono` = `Tesouraria`

### Requirement: Listagem com provedores, dono e última execução
`GET /api/v1/curvas-mercado` SHALL aceitar, além dos filtros que já tem, `provedor` (curvas com esse provedor em `tCurvaPrvdr.iPrvdrDados`, comparado depois de `trim`) e `dono` (trecho de `cPprioDado`, sem diferenciar maiúsculas e acentos), e cada item SHALL trazer, além do que já traz:
- `provedores`: os provedores da curva em `tCurvaPrvdr`, sem repetição, na ordem de prioridade (`cPriorCsumo`), o primeiro sendo o principal; lista vazia quando não há;
- `dono`: `cPprioDado`;
- `ultimaExecucao`: `{ "dataBase", "usuario" }` de `dBaseReft` e `cUsuarCalc` de `tCurvaMercd`; nulo sem `dBaseReft`.

Os provedores SHALL vir numa consulta só para a página inteira. O envelope da página continua o que já existe (`totalElementos`, `totalPaginas`).

#### Scenario: Curva da B3 já construída
- **WHEN** o front lista com `provedor` = `B3`, e a `PRE` tem provedores `B3` (prioridade 1) e `BLOOMBERG` (prioridade 2), `cPprioDado` = `Tesouraria` e `dBaseReft` = `2026-09-14`
- **THEN** o item da `PRE` traz `provedores` = `["B3", "BLOOMBERG"]`, `dono` = `Tesouraria` e `ultimaExecucao.dataBase` = `2026-09-14`

#### Scenario: Curva nunca construída
- **WHEN** a curva `DCL` não tem `dBaseReft`
- **THEN** o item traz `ultimaExecucao` = `null`
