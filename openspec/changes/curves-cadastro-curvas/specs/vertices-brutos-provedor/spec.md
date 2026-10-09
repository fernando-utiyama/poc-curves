## Purpose

Na `services/curves`, consultar e manter à mão os vértices brutos que os feeders gravam para B3, ANBIMA e Bloomberg, filtrando por provedor, código na fonte (ticker) e data-base, como contingência para corrigir ou digitar o dado de uma fonte.

## ADDED Requirements

### Requirement: CRUDs dos provedores já existentes
Os CRUDs do dado bruto dos três provedores (`tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr`) já existem na primeira parte do curves. As rotas `/dados-mercado` desta spec SHALL ser a entrada única da tela de dados de mercado, montada sobre os serviços e repositórios que já existem, sem duplicar a gravação; as rotas atuais desses CRUDs MAY continuar.

#### Scenario: Gravação pelo serviço existente
- **WHEN** o front inclui um vértice da Bloomberg por `/dados-mercado/BLOOMBERG/...`
- **THEN** a gravação passa pelo mesmo serviço e repositório do CRUD da Bloomberg que já existe


### Requirement: Provedores e campos dos vértices brutos
O serviço SHALL manter os vértices brutos destes provedores (`iPrvdrDados` de `tCurvaPrvdr`), cada um na sua tabela, com `cTickerIndcd` = nome da curva e `dBaseReft` = data-base:

| Provedor | Tabela | Campos da API (coluna) | Só leitura |
|---|---|---|---|
| `B3` | `tBtrsCurvaPrimr` | `diasCorridos` (`cDiaCorri`), `diasUteis` (`cDiaUtil`), `valor` (`vPrecoTx`), `fatorAcumulado` (`vFatorAcum`, opcional), `fatorDia` (`vFatorDia`, opcional) | `dataVertice` = data-base + `diasCorridos` |
| `ANBIMA` | `tAnbmaCurvaPrimr` | `prazoDiasCorridos` (`vVertcCurva`, dias corridos até a `Data Vencimento`), `taxa` (`vPrecoTx`, opcional) | `vencimento` = data-base + `prazoDiasCorridos` |
| `BLOOMBERG` | `tBbergCurvaPrimr` | `ticker` (`cTickerBberg`, ticker completo), `valor` (`vPrecoUlt`) | — |

Todo vértice SHALL ter também `id` (`cIdtfdUnic`). Decimais SHALL trafegar como string na escala da coluna e ser gravados exatamente como enviados, sem arredondamento (é o dado bruto). Outro provedor no caminho responde 400 `PARAMETRO_INVALIDO`. As demais colunas das tabelas ficam como estão (nulas na inclusão).

#### Scenario: Vértice da ANBIMA
- **WHEN** o cliente consulta a NTN-B de `2026-09-28`
- **THEN** o vértice do título de vencimento `2027-05-15` traz `prazoDiasCorridos` 229, `taxa` `5.541500000000` e `vencimento` `2027-05-15`

### Requirement: Rotas
O serviço SHALL expor (prefixo `/api/v1`), com `{provedor}` = `B3`, `ANBIMA` ou `BLOOMBERG` e `{codigo}` = código da curva de mercado:

| Rota | Uso |
|---|---|
| `GET /dados-mercado/{provedor}/tickers` | códigos na fonte ligados a curvas em `tCurvaPrvdr` para o provedor, cada um com o produto e as curvas ligadas (código e nome), em ordem de código |
| `GET /dados-mercado/{provedor}?tickerProvedor=&dataBase=AAAA-MM-DD` | para cada curva ligada ao código: código e nome da curva, `curvaConstruida`, os vértices da data e os avisos |
| `POST /dados-mercado/{provedor}/{codigo}/{dataBase}/vertices` | incluir um vértice (também numa data sem nenhum) |
| `PUT /dados-mercado/{provedor}/{codigo}/{dataBase}/vertices/{id}` | alterar um vértice, com todos os campos |
| `DELETE /dados-mercado/{provedor}/{codigo}/{dataBase}/vertices/{id}` | excluir um vértice |
| `DELETE /dados-mercado/{provedor}/{codigo}/{dataBase}` | excluir todos os vértices da curva na data |

Os vértices SHALL vir ordenados por `diasCorridos` (B3), `prazoDiasCorridos` (ANBIMA) ou `ticker` (Bloomberg), e por `id`. Curva inexistente ou sem código, e vértice inexistente ou de outra curva ou data, respondem 404 `NAO_ENCONTRADO`. Os erros, a autenticação, o `X-Correlation-Id`, o horário e o contrato de tipos seguem a spec `cadastro-curva-mercado`.

#### Scenario: Tickers da B3
- **WHEN** o cliente chama `GET /api/v1/dados-mercado/B3/tickers`
- **THEN** a resposta traz os códigos `DCL`, `DPL`, `INP`, `PRE` e `PTX`, cada um com o produto `TS` e as curvas ligadas

#### Scenario: Código ligado a duas curvas
- **WHEN** o código `PRE` da B3 está ligado às curvas `PRE` e `DI_MERCADO`, e o cliente consulta `GET /api/v1/dados-mercado/B3?tickerProvedor=PRE&dataBase=2026-09-14`
- **THEN** a resposta traz as duas curvas, cada uma com os seus 278 vértices

### Requirement: Gravação do vértice
A inclusão e a alteração SHALL acontecer numa transação que trava a linha da curva em `tCurvaMercd` (`UPDLOCK, ROWLOCK`, 60 segundos, a mesma trava do processor e do engine) e gera `cIdtfdUnic` por `MAX + 1` lido com `UPDLOCK, HOLDLOCK`. O serviço MUST NOT escrever em `tCurvaMercd`, `tCurvaPrvdr`, `tDadoVertcCurva` nem `tDadoCurva`, e MUST NOT disparar o engine: a correção só vale para a curva construída num recálculo.

A gravação SHALL ser recusada com 422 `DADOS_INVALIDOS`, sem gravar nada e com um item em `detalhes` por campo, só quando o vértice não pode ser gravado de forma consistente:
- falta um campo obrigatório (B3: `diasCorridos`, `diasUteis`, `valor`; ANBIMA: `prazoDiasCorridos`; Bloomberg: `ticker`, `valor`);
- um inteiro não cabe em `INT`, ou `prazoDiasCorridos` é fracionário;
- um decimal não é número ou não cabe na coluna (`DECIMAL(28,12)` nos valores e taxas, `DECIMAL(28,16)` nos fatores);
- o `ticker` passa de 50 caracteres.

As demais regras SHALL virar avisos, e o vértice é gravado. Os avisos SHALL vir na resposta de toda gravação e da consulta, calculados sobre todos os vértices da curva na data depois da gravação:

| Aviso | Provedor | Quando |
|---|---|---|
| `DIAS_CORRIDOS_NAO_POSITIVO` | B3 | `diasCorridos` menor que 1 |
| `DIAS_UTEIS_INCOERENTES` | B3 | `diasUteis` menor que 1 ou maior que `diasCorridos` |
| `DIAS_CORRIDOS_REPETIDOS` | B3 | dois vértices da data com os mesmos `diasCorridos` |
| `PRAZO_NAO_POSITIVO` | ANBIMA | `prazoDiasCorridos` menor que 1 |
| `PRAZO_REPETIDO` | ANBIMA | dois vértices da data com o mesmo `prazoDiasCorridos` |
| `TAXA_AUSENTE` | ANBIMA | vértice sem `taxa` (o engine descarta o título com `SEM_TAXA`) |
| `TICKER_REPETIDO` | Bloomberg | dois vértices da data com o mesmo `ticker` |
| `CURVA_SEM_PROVEDOR` | todos | a curva não tem provedor desse `iPrvdrDados` em `tCurvaPrvdr` (o feeder não grava nem substitui essa curva) |
| `CURVA_JA_CONSTRUIDA` | todos | a curva já tem vértices em `tDadoVertcCurva` na data (a correção só vale num recálculo) |

#### Scenario: Prazo repetido na ANBIMA
- **WHEN** o gestor inclui na NTN-B de `2026-09-28` um vértice com `prazoDiasCorridos` 229, que já existe
- **THEN** o vértice é gravado, e a resposta é 201 com o aviso `PRAZO_REPETIDO` citando os dois vértices

#### Scenario: Valor que não cabe na coluna
- **WHEN** o gestor envia na B3 `valor` `13.1234567890123`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS` no campo `valor`, e nada é gravado

#### Scenario: Correção depois da construção
- **WHEN** o gestor altera um vértice da `PRE` de `2026-09-14`, já construída
- **THEN** o vértice é gravado, a resposta traz `CURVA_JA_CONSTRUIDA`, e `tDadoVertcCurva` não muda

### Requirement: Log da edição do bruto
Toda gravação pelas rotas `/dados-mercado` (vértice ou exclusão da data; a planilha vem na change `curves-operacao-curvas`) SHALL emitir, depois do commit, o evento de log `CURVA_PRIMARIA_EDITADA` (nível `AVISO`) com provedor, código e nome da curva, data-base, operação (`INCLUSAO`, `ALTERACAO`, `EXCLUSAO`, `EXCLUSAO_DATA`, `PLANILHA`), vértice antes e depois (quando for um só), quantidades antes e depois, usuário (o `X-Usuario`, nulo sem ele), instante (Brasília) e `correlationId`, sem auditoria nem Blob.

#### Scenario: Vértice da ANBIMA alterado
- **WHEN** o gestor altera a taxa de um vértice da NTN-B de `2026-09-28`
- **THEN** o log tem um `CURVA_PRIMARIA_EDITADA` com provedor `ANBIMA`, operação `ALTERACAO` e o vértice antes e depois
