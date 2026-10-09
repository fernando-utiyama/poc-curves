# Cadastro dos 3 provedores e das 7 curvas pelo Swagger

## Primeiro: os 3 provedores

`POST /api/v1/provedores`, na ordem (`P1-B3.json`, `P2-ANBIMA.json`, `P3-BLOOMBERG.json`). Vêm antes de tudo porque `tCurvaPrvdr.iPrvdrDados` tem chave estrangeira para `tPrvdrDadoMercd`: ligar a curva a um provedor que não existe dá 404. O `nomeProvedor` é o identificador (`B3`, `ANBIMA`, `BLOOMBERG`) e tem que bater com o que o processor procura. Os nomes dos campos (`nomeProvedor`, `descricao`, `produto`, `nomeCompletoAtivoOuInstrumento`) são os do `ProvedorEntity` do guia; confira no Swagger do `ProvedorRequest`. O `nomeCompletoAtivoOuInstrumento` grava em `iCoplt`, que tem 50 caracteres.

## Depois: as 7 curvas

`POST /api/v1/curvas-mercado`, um arquivo por curva (cole o conteúdo no body). Sem `fimVigencia` (curva em aberto). Ajuste `dono`, `classificacao` e `classeAtivo` se o cadastro real usar outros textos.

| Arquivo | Curva | Unidade | Cotação |
|---|---|---|---|
| 01-PRE | DIxPRE | TAXA | Business252, Compounded |
| 02-DCL | Cupom limpo de dólar | TAXA | Actual360, Simple |
| 03-DPL | Cupom Limpo DI X IPCA | TAXA | Business252, Compounded |
| 04-INP | IBOVESPA | PONTOS | (sem cotação) |
| 05-PTX | PTAX - USD | PRECO | (sem cotação) |
| 06-NTNB | NTN-B | TAXA | Business252, Compounded |
| 07-SOFR | SOFR | TAXA | Actual360, Simple |

Valores iguais aos dos fixtures do engine. Regras do cadastro: `codigo` só com A-Z, 0-9 e sublinhado; `nome` único (sem diferenciar maiúsculas e acentos) contra **todas** as curvas, inclusive as sem código; `dayCounterCotacao` e `compounding` só para `TAXA`.

Se já existem curvas `TBD-...` com o mesmo nome, a criação responde 409 `NOME_EM_USO`: nesse caso, mude o `nome` aqui.

## Depois: ligar cada curva ao provedor (os 7 `V*.json`)

Sem isso a carga não grava nada. Corpo: `provedor`, `produto`, `tickerProvedor` (o ticker do provedor, `cTickerPrvdr`) e `prioridade` (1 = principal). O `{nome}` da URL é o nome da curva (o Swagger codifica os espaços).

| Arquivo | Curva (`{nome}` na URL) | Rota |
|---|---|---|
| `V1-PRE.json` | `DIxPRE` | `POST /api/v1/curvas-mercado/DIxPRE/provedores` |
| `V2-DCL.json` | `Cupom limpo de dólar` | `POST /api/v1/curvas-mercado/Cupom limpo de dólar/provedores` |
| `V3-DPL.json` | `Cupom Limpo DI X IPCA` | `POST /api/v1/curvas-mercado/Cupom Limpo DI X IPCA/provedores` |
| `V4-INP.json` | `IBOVESPA` | `POST /api/v1/curvas-mercado/IBOVESPA/provedores` |
| `V5-PTX.json` | `PTAX - USD` | `POST /api/v1/curvas-mercado/PTAX - USD/provedores` |
| `V6-NTNB.json` | `NTN-B` | `POST /api/v1/curvas-mercado/NTN-B/provedores` |
| `V7-SOFR.json` | `SOFR` | `POST /api/v1/curvas-mercado/SOFR/provedores` |

O `provedor` tem que existir nos 3 provedores cadastrados antes (`P1`, `P2`, `P3`); senão, 404. `NTN-B` e `SOFR` não repetem prioridade com outra ligação da mesma curva. O `tickerProvedor` da ANBIMA é o `Titulo` do arquivo (`NTN-B`); da Bloomberg, o membro do ticker (`S0490Z`).

Ordem completa: provedores (`P1` a `P3`), curvas (`01` a `07`), ligações (`V1` a `V7`) e, por fim, a configuração de cálculo de cada curva (`POST .../configuracoes`).
