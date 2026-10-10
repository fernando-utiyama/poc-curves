## Purpose

Na `services/curves`, consultar e manter à mão os vértices brutos que os feeders gravam para B3, ANBIMA e Bloomberg, por curva (identificada pelo nome) e data-base, como contingência para corrigir ou digitar o dado de uma fonte.

## ADDED Requirements

### Requirement: Provedores e campos dos vértices brutos
O serviço SHALL manter os vértices brutos destes provedores, cada um na sua tabela, com `cTickerIndcd` = nome da curva e `dBaseReft` = data-base:

| Provedor | Tabela | Campos da API (coluna) | Só leitura |
|---|---|---|---|
| `B3` | `tBtrsCurvaPrimr` | `diasCorridos` (`cDiaCorri`), `diasUteis` (`cDiaUtil`), `valor` (`vPrecoTx`), `fatorAcumulado` (`vFatorAcum`, opcional), `fatorDia` (`vFatorDia`, opcional) | `dataVertice` = data-base + `diasCorridos` |
| `ANBIMA` | `tAnbmaCurvaPrimr` | `prazoDiasCorridos` (`vVertcCurva`, dias corridos até o vencimento do título), `taxa` (`vPrecoTx`, opcional) | `vencimento` = data-base + `prazoDiasCorridos` |
| `BLOOMBERG` | `tBbergCurvaPrimr` | `tickerBloomberg` (`cTickerBberg`), `precoUltimo` (`vPrecoUlt`), `precoLiquidacao` (`vPrecoLiqdc`), `precoMedio` (`vPrecoMed`), `diaVencimento` (`cDiaVcto`), `dataLiquidacaoFinanceira` (`dLiqdcFincr`), `formaLiquidacao` (`cFormaLiqdc`), `dataVencimentoContrato` (`dVctoContr`), `dataUltimoNegocio` (`dUltNegoc`) | — |

Todo vértice SHALL ter também `id` (`cIdtfdUnic`, inteiro). Decimais SHALL sair como texto, exatamente como gravados, e ser gravados como enviados, sem arredondamento (é o dado bruto).

#### Scenario: Vértice da ANBIMA
- **WHEN** o cliente consulta a NTN-B de `2026-09-28`
- **THEN** o vértice do título de vencimento `2027-05-15` traz `prazoDiasCorridos` 229, `taxa` `5.541500000000` e `vencimento` `2027-05-15`

### Requirement: Rotas
O serviço SHALL expor (prefixo `/api/v1`), com `{p}` = `b3`, `anbima` ou `bloomberg` e `{nome}` = nome da curva de mercado:

| Rota | Uso | Sucesso |
|---|---|---|
| `GET /curvas-mercado/primaria-{p}?de=&ate=&codigo=&nome=` | uma linha por curva e data-base com bruto (`codigo`, `nome`, `situacao`, `dataBase`, `quantidadeVertices`, `tickersProvedor`, `curvaConstruida`); sem `de` e `ate`, só a última data de cada curva; lista simples, sem página | 200 |
| `GET /curvas-mercado/{nome}/primaria-{p}/{dataBase}` | `{ curvaConstruida, vertices }` da curva na data | 200 |
| `POST /curvas-mercado/{nome}/primaria-{p}/{dataBase}/vertices` | incluir um vértice (também numa data sem nenhum) | 201 com o vértice |
| `PUT /curvas-mercado/{nome}/primaria-{p}/{dataBase}/vertices/{id}` | alterar um vértice, com todos os campos | 200 com o vértice |
| `DELETE /curvas-mercado/{nome}/primaria-{p}/{dataBase}/vertices/{id}` | excluir um vértice | 200 vazio |
| `DELETE /curvas-mercado/{nome}/primaria-{p}/{dataBase}` | excluir todos os vértices da curva na data | 200 vazio |

A listagem não esconde curva sem código. Com período, `de` sem `ate` vai até hoje e `ate` sem `de` começa 30 dias antes; início depois do fim ou período maior que 366 dias respondem 400 `PARAMETRO_INVALIDO` com a mensagem explicada. Os vértices SHALL vir ordenados por `diasCorridos` (B3), prazo (ANBIMA) ou `dataVencimentoContrato` (Bloomberg), e por `id`. Curva inexistente, e vértice inexistente ou de outra curva ou data, respondem 404 `NAO_ENCONTRADO`. As respostas MUST NOT trazer avisos.

#### Scenario: Última data de cada curva
- **WHEN** o cliente chama `GET /api/v1/curvas-mercado/primaria-b3` sem período
- **THEN** a resposta traz uma linha por curva com bruto da B3, com a última data-base gravada

#### Scenario: Vértices da DIxPRE
- **WHEN** o cliente chama `GET /api/v1/curvas-mercado/DIxPRE/primaria-b3/2026-09-14`
- **THEN** a resposta traz `curvaConstruida` e os 278 vértices em ordem de dias corridos

### Requirement: Gravação do vértice
O serviço MUST NOT escrever em `tCurvaMercd`, `tCurvaPrvdr`, `tDadoVertcCurva` nem `tDadoCurva`, e MUST NOT disparar o engine: a correção de uma data já construída só vale quando o gestor recalcular. O `cIdtfdUnic` SHALL vir de `MAX + 1` da tabela.

A gravação SHALL ser recusada com 422 `DADOS_INVALIDOS`, sem gravar nada e com a mensagem de cada campo, quando:
- falta um campo obrigatório (B3: `diasCorridos`, `diasUteis`, `valor`; ANBIMA: `prazoDiasCorridos`; Bloomberg: `tickerBloomberg`, `precoUltimo`);
- `prazoDiasCorridos` é menor que 1;
- um decimal não cabe na coluna (`DECIMAL(28,12)` nos valores, taxas e preços, `DECIMAL(28,16)` nos fatores);
- `tickerBloomberg` passa de 50 caracteres, ou `formaLiquidacao` de 20.

Incluir, alterar e excluir um vértice, e excluir todos os vértices da data, SHALL ser aceitos também numa data já construída: só o bruto muda, e a curva construída (`tDadoVertcCurva`, `tDadoCurva`) fica como está até o gestor recalcular ou apagar a construída.

#### Scenario: Valor que não cabe na coluna
- **WHEN** o gestor envia na B3 `valor` `13.1234567890123`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS` no campo `valor`, e nada é gravado

#### Scenario: Correção depois da construção
- **WHEN** o gestor altera um vértice da `DIxPRE` de `2026-09-14`, já construída
- **THEN** o vértice é gravado, a resposta é 200 com o vértice, e `tDadoVertcCurva` não muda

#### Scenario: Apagar o bruto de uma data construída
- **WHEN** o gestor apaga todos os vértices da `DIxPRE` de `2026-09-14`, já construída
- **THEN** a resposta é 200, o bruto da data é apagado, e `tDadoVertcCurva` e `tDadoCurva` não mudam

### Requirement: Log da edição do bruto
Toda gravação pelas rotas do bruto (vértice ou exclusão da data) SHALL publicar o evento `CURVA_PRIMARIA_EDITADA` com provedor, código e nome da curva, data-base, operação (`INCLUSAO`, `ALTERACAO`, `EXCLUSAO`, `EXCLUSAO_DATA`), vértice antes e depois (os vértices da data, na exclusão da data), quantidades antes e depois, instante (Brasília) e `correlationId`, sem auditoria nem Blob.

#### Scenario: Vértice da ANBIMA alterado
- **WHEN** o gestor altera a taxa de um vértice da NTN-B de `2026-09-28`
- **THEN** o evento `CURVA_PRIMARIA_EDITADA` sai com provedor `ANBIMA`, operação `ALTERACAO` e o vértice antes e depois
