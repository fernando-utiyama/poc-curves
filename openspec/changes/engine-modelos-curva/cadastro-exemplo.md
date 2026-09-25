# Exemplo de cadastro das 7 curvas do primeiro objetivo

Exemplo de preenchimento do cadastro descrito no `design.md` (D4) para as curvas do primeiro objetivo do projeto.

**Isto não é o desenho de produção do cadastro.** No sistema real, curva e provedor são criados e editados por um serviço próprio (`acts-srv-curvas`), via CRUD — o engine só lê. O poc ainda não tem esse serviço (fica para um `services/curves` futuro; ver Non-Goals do `design.md`). Até lá, o SQL abaixo é **só seed de teste**: dá a massa de dados mínima para a tarefa 4.3 e para os testes do engine rodarem, sem representar como o cadastro chega ao banco em produção.

## Pré-requisitos e ressalvas

- **`tParmConfgCurva` em chave/valor** depende da alteração de PK prevista na tarefa 4.1: `(cldtfdConfg, cConfgIdtfd)`, com `cConfgIdtfd VARCHAR(50)`. Com a PK atual (só `cldtfdConfg`), a tabela aceita uma linha por configuração, e os parâmetros abaixo não cabem. Essa decisão ainda está aberta.
- **Modelos nativos desta mudança:** `PRONTA_TS_B3` para as 5 curvas B3, `NTNB_BOOTSTRAP_ANBIMA` para a NTN-B (spec `ntnb-anbima-curve-model`) e `SOFR_ZERO_BLOOMBERG` para o SOFR (spec `sofr-bloomberg-curve-model`).
- **FK das tabelas brutas:** `tBtrsCurvaPrimr` e `tAnbmaCurvaPrimr` têm FK de `cTickerIndcd` para `tCurvaMercd`. Pelo design (D5 e Risks), o bruto é gravado sob o código na fonte (ex.: `PRE`), então cada código na fonte também precisa de uma linha em `tCurvaMercd`, como curva primária. Essas linhas levam `cTickerIdtfdUnic = NULL`, para não colidir com os códigos das curvas de mercado nas rotas por código. Elas não aparecem no SQL abaixo.
- **Dados brutos:** o engine só lê as tabelas brutas, preenchidas pelo conector e pelo processor (design D10). A NTN-B precisa de `dVctoTitulo` em `tAnbmaCurvaPrimr` e da carga de `tSerieTituloNtnb` (D11, D13); o SOFR, da tabela de nós por tenor (D15). Nos testes, as tabelas brutas são carregadas por fixture.
- **Valores a conferir:** as casas decimais do INP (o manual diz só "pontos de índice", e o TaxaSwap mostra 2 casas).

## Visão geral

| Código (`cTickerIdtfdUnic`) | Nome (`cTickerIndcd`) | Fonte | Código na fonte | Construção | Unidade | Grandeza + interpolador | Tempo | Cotação | Extrap. início / fim | Arredondamento |
|---|---|---|---|---|---|---|---|---|---|---|
| `PRE` | DIxPRE | B3 (TaxaSwap) | `PRE` | `PRONTA_TS_B3` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Business252` / `Compounded` / `Annual` | `Disabled` / `FlatForward` | 3 casas, `HALF_UP` |
| `DCL` | Cupom limpo de dólar | B3 (TaxaSwap) | `DCL` | `PRONTA_TS_B3` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Actual360` / `Simple` | `Disabled` / `FlatForward` | 2 casas, `HALF_UP` |
| `PTX` | PTAX - USD | B3 (TaxaSwap) | `PTX` | `PRONTA_TS_B3` | `PRECO` | `Price` + `LogLinear` | `Business252` | — | `Disabled` / `Disabled` | 7 casas, `DOWN` (truncado) |
| `DPL` | Cupom Limpo DI X IPCA | B3 (TaxaSwap) | `DPL` | `PRONTA_TS_B3` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Business252` / `Compounded` / `Annual` | `Disabled` / `FlatForward` | 2 casas, `HALF_UP` |
| `INP` | IBOVESPA | B3 (TaxaSwap) | `INP` | `PRONTA_TS_B3` | `PONTOS` | `Price` + `LogLinear` | `Business252` | — | `Disabled` / `FlatValue` | 2 casas, `HALF_UP` (a conferir) |
| `NTNB` | NTN-B | ANBIMA | `NTN-B` | `NTNB_BOOTSTRAP_ANBIMA` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Business252` / `Compounded` / `Annual` | `Disabled` / `FlatForward` | 4 casas, `HALF_UP` |
| `SOFR` | SOFR | Bloomberg | `S0490Z` | `SOFR_ZERO_BLOOMBERG` | `TAXA` | `CompoundFactor` + `Linear` | `Actual360` | `Actual360` / `Simple` | `Disabled` / `FlatValue` | 3 casas, `HALF_UP` |

Calendário: `Brazil` / `Settlement` / `Following` para as curvas brasileiras; `UnitedStates` / `FederalReserve` / `ModifiedFollowing` para o SOFR. Horizonte de exemplo: `10Y`. O domínio da interpolação sempre vai pelo menos até o último ponto real, então o horizonte só importa quando passa dele.

Origem das convenções:
- **Curvas B3:** Manual de Curvas B3, itens 1.4.2/1.4.6 (PRE, DPL), 1.4.3/1.4.10 (DCL), 1.4.5/1.4.8 (INP). A PTX não tem função própria no manual e fica com extrapolação `Disabled`.
- **SOFR:** convenção da curva zero cupom de SOFR do manual (ZUS, item 2.11: interpolação 360 linear do item 1.4.11 e Flat no fim).
- **NTN-B:** convenção de taxa real ANBIMA em 252 dias úteis; pontos nos vencimentos dos títulos, obtidos por bootstrap.

## SQL de exemplo (SQL Server)

### Provedores

```sql
INSERT INTO tPrvdrDadoMercd (iPrvdrDados, cProdt, cInfoProdt) VALUES
 ('B3',        'TS', 'Taxas de Mercado para Swaps (TaxaSwap.txt)'),
 ('ANBIMA',    'TP', 'Taxas indicativas de títulos públicos'),
 ('BLOOMBERG', 'ZR', 'Zero rates de SOFR (curve member S0490Z)');
```

### Curvas de mercado (`tCurvaMercd`)

`cTickerIndcd` é o nome (único, chave de todas as FKs); `cTickerIdtfdUnic` é o código usado nas rotas por código.

```sql
INSERT INTO tCurvaMercd
 (cTickerIndcd, cTickerIdtfdUnic, cTpoVlr, cNormaDia, cTpoJuro, iPrvdrDados, cMoedaNegoc, cPaisInstt, cSitReg, dInicVgcia) VALUES
 ('DIxPRE',                'PRE',  'TAXA',   'Business252', 'Compounded', 'B3',        'BRL', 'BR', 'ATIVO', '2026-01-01'),
 ('Cupom limpo de dólar',  'DCL',  'TAXA',   'Actual360',   'Simple',     'B3',        'USD', 'BR', 'ATIVO', '2026-01-01'),
 ('PTAX - USD',            'PTX',  'PRECO',  NULL,          NULL,         'B3',        'BRL', 'BR', 'ATIVO', '2026-01-01'),
 ('Cupom Limpo DI X IPCA', 'DPL',  'TAXA',   'Business252', 'Compounded', 'B3',        'BRL', 'BR', 'ATIVO', '2026-01-01'),
 ('IBOVESPA',              'INP',  'PONTOS', NULL,          NULL,         'B3',        'BRL', 'BR', 'ATIVO', '2026-01-01'),
 ('NTN-B',                 'NTNB', 'TAXA',   'Business252', 'Compounded', 'ANBIMA',    'BRL', 'BR', 'ATIVO', '2026-01-01'),
 ('SOFR',                  'SOFR', 'TAXA',   'Actual360',   'Simple',     'BLOOMBERG', 'USD', 'US', 'ATIVO', '2026-01-01');
```

### Origem (`tCurvaPrvdr`)

Liga a curva de mercado ao código dela na fonte. `cPriorCsumo = 1` é a fonte principal.

```sql
INSERT INTO tCurvaPrvdr (cldtfdUnic, cTickerIndcd, iPrvdrDados, cPrvdrMercd, cTickerPrvdr, cPriorCsumo) VALUES
 (1, 'DIxPRE',                'B3',        'TS', 'PRE',          1),
 (2, 'Cupom limpo de dólar',  'B3',        'TS', 'DCL',          1),
 (3, 'PTAX - USD',            'B3',        'TS', 'PTX',          1),
 (4, 'Cupom Limpo DI X IPCA', 'B3',        'TS', 'DPL',          1),
 (5, 'IBOVESPA',              'B3',        'TS', 'INP',          1),
 (6, 'NTN-B',                 'ANBIMA',    'TP', 'NTN-B',        1),
 (7, 'SOFR',                  'BLOOMBERG', 'ZR', 'S0490Z',       1);
```

### Configuração (`tConfgCurva`)

`cMotorCalc` = modelo de construção, `cRotnaCalc` = interpolador. Vigência a partir de 2026-01-01, sem fim.

```sql
INSERT INTO tConfgCurva (cTickerIndcd, cAtivoFincr, cMotorCalc, cRotnaCalc, cVrsaoReg, dInicVgcia, dValidAte) VALUES
 ('DIxPRE',                1, 'PRONTA_TS_B3',           'LogLinear', 1, '2026-01-01', NULL),
 ('Cupom limpo de dólar',  1, 'PRONTA_TS_B3',           'LogLinear', 1, '2026-01-01', NULL),
 ('PTAX - USD',            1, 'PRONTA_TS_B3',           'LogLinear', 1, '2026-01-01', NULL),
 ('Cupom Limpo DI X IPCA', 1, 'PRONTA_TS_B3',           'LogLinear', 1, '2026-01-01', NULL),
 ('IBOVESPA',              1, 'PRONTA_TS_B3',           'LogLinear', 1, '2026-01-01', NULL),
 ('NTN-B',                 1, 'NTNB_BOOTSTRAP_ANBIMA',  'LogLinear', 1, '2026-01-01', NULL),
 ('SOFR',                  1, 'SOFR_ZERO_BLOOMBERG',    'Linear',    1, '2026-01-01', NULL);
```

### Parâmetros (`tParmConfgCurva`)

Chave em `cConfgIdtfd`, valor texto em `cTpoInstt` e valor numérico em `vPrecoTx`. Depende da PK composta (ver Pré-requisitos).

Exemplo completo para a **DIxPRE**:

```sql
INSERT INTO tParmConfgCurva (cldtfdConfg, cConfgIdtfd, cTpoInstt, vPrecoTx)
SELECT c.cldtfdConfg, p.chave, p.texto, p.numero
FROM tConfgCurva c
CROSS JOIN (VALUES
 ('GRANDEZA',                'Discount',    NULL),
 ('DAY_COUNTER_TEMPO',       'Business252', NULL),
 ('FREQUENCY',               'Annual',      NULL),
 ('CALENDARIO',              'Brazil',      NULL),
 ('MERCADO_CALENDARIO',      'Settlement',  NULL),
 ('BUSINESS_DAY_CONVENTION', 'Following',   NULL),
 ('EXTRAPOLACAO_INICIO',     'Disabled',    NULL),
 ('EXTRAPOLACAO_FIM',        'FlatForward', NULL),
 ('HORIZONTE',               '10Y',         NULL),
 ('CASAS_DECIMAIS',          NULL,          3),
 ('MODO_ARREDONDAMENTO',     'HALF_UP',     NULL)
) AS p(chave, texto, numero)
WHERE c.cTickerIndcd = 'DIxPRE' AND c.dValidAte IS NULL;
```

As demais curvas usam o mesmo `INSERT`, trocando o `WHERE` e os valores que diferem da DIxPRE:

| Curva | `GRANDEZA` | `DAY_COUNTER_TEMPO` | `FREQUENCY` | `CALENDARIO` / `MERCADO_CALENDARIO` | `BUSINESS_DAY_CONVENTION` | `EXTRAPOLACAO_FIM` | `CASAS_DECIMAIS` | `MODO_ARREDONDAMENTO` |
|---|---|---|---|---|---|---|---|---|
| Cupom limpo de dólar | `Discount` | `Business252` | — | `Brazil` / `Settlement` | `Following` | `FlatForward` | 2 | `HALF_UP` |
| PTAX - USD | `Price` | `Business252` | — | `Brazil` / `Settlement` | `Following` | `Disabled` | 7 | `DOWN` |
| Cupom Limpo DI X IPCA | `Discount` | `Business252` | `Annual` | `Brazil` / `Settlement` | `Following` | `FlatForward` | 2 | `HALF_UP` |
| IBOVESPA | `Price` | `Business252` | — | `Brazil` / `Settlement` | `Following` | `FlatValue` | 2 | `HALF_UP` |
| NTN-B | `Discount` | `Business252` | `Annual` | `Brazil` / `Settlement` | `Following` | `FlatForward` | 4 | `HALF_UP` |
| SOFR | `CompoundFactor` | `Actual360` | — | `UnitedStates` / `FederalReserve` | `ModifiedFollowing` | `FlatValue` | 3 | `HALF_UP` |

Todas usam `EXTRAPOLACAO_INICIO = Disabled` e `HORIZONTE = 10Y`. `FREQUENCY` só se aplica à cotação `Compounded`; para `Simple` e para curvas de preço ou pontos, a linha é omitida.

## Resultado esperado

Com esse cadastro e o `TaxaSwap.txt` de 14/09/2026 carregado em `tBtrsCurvaPrimr`:
- `POST /api/v1/curvas/PRE/2026-09-14/construcao` grava 278 pontos da DIxPRE em `tDadoCurva`, o primeiro com taxa 13,900.
- `GET /api/v1/curvas/PRE/2026-09-14/interpolacao?du=7406` devolve 14,167 (ponto publicado, 3 casas).
- `GET /api/v1/curvas/por-nome/2026-09-14?nome=cupom limpo de dolar` devolve os pontos da DCL, com o código `DCL`.
- `POST /api/v1/curvas/NTNB/2026-09-14/construcao` grava um ponto por NTN-B com taxa na data, no vencimento de cada título.
- `POST /api/v1/curvas/SOFR/2026-09-14/construcao` grava um ponto por tenor publicado do `S0490Z` (21 na lista atual).
