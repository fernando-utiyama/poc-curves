## ADDED Requirements

### Requirement: Um aviso por carga, depois do commit
Para cada carga gravada com sucesso, o processor SHALL chamar `POST {engine}/api/v1/cargas` **uma vez** (com repetições em caso de falha), **depois do commit** da gravação. O aviso MUST NOT segurar a resposta ao orquestrador: a resposta 200 do download sai logo depois do commit, e o aviso roda em segundo plano. A chamada SHALL ir sem `Authorization` e com o `X-Correlation-Id` da chamada original. O aviso SHALL valer para as três fontes (`B3`, `ANBIMA` e `BLOOMBERG`) e para o download, o upload e o reprocessamento.

#### Scenario: Resposta antes do aviso
- **WHEN** a carga ANBIMA é gravada e o engine demora 40 segundos para responder ao aviso
- **THEN** o orquestrador já recebeu o 200 do download, e o aviso termina em segundo plano

#### Scenario: Reprocessamento também avisa
- **WHEN** o operador reprocessa a carga B3 de `2026-09-14`
- **THEN** o processor relê o original, grava e avisa o engine com o `idCarga` do original lido

### Requirement: Corpo do aviso
O corpo SHALL ser:

```json
{
  "idCarga": "B3-TS-20260914-46a249c60bec",
  "fonte": "B3",
  "produto": "TS",
  "dataBase": "2026-09-14",
  "verticesPorCurva": { "PRE": 278, "DCL": 278, "DPL": 278, "INP": 278, "PTX": 278 }
}
```

`idCarga` SHALL ser o da carga (até 100 caracteres), `fonte` e `produto` os da origem (`B3`/`TS`, `ANBIMA`/`MS`, `BLOOMBERG`/`BLC2`) e `dataBase` a **do conteúdo** do arquivo. `verticesPorCurva` MUST NOT ser vazio nem ter quantidade negativa.

#### Scenario: Aviso da B3
- **WHEN** a carga B3 de `2026-09-14` grava as cinco curvas mapeadas
- **THEN** o corpo traz `fonte` = `B3`, `produto` = `TS`, `dataBase` = `2026-09-14` e 278 vértices em cada um dos cinco códigos

### Requirement: `verticesPorCurva`, pelo nome da curva
`verticesPorCurva` SHALL trazer, para cada **curva** que gravou ao menos uma linha, o **nome da curva** (`cTickerIndcd`, a identidade da curva em todo o projeto) e a quantidade de linhas **gravadas** na tabela bruta daquela curva para a data-base, **inclusive as que o modelo vier a descartar**. O processor SHALL achar as curvas pelo cadastro de provedor (`tCurvaPrvdr`): cada curva ligada à origem da carga e ao código da fonte que veio no arquivo (código da B3, `Titulo` da ANBIMA, membro do ticker da Bloomberg). O código da fonte MUST NOT ir no aviso. Curva sem linha no arquivo MUST NOT aparecer.

| Fonte | Chave do mapa | Quantidade |
|---|---|---|
| B3 (`TS`) | nome da curva (`PRE`, `DCL`, `DPL`, `INP`, `PTX`) | vértices gravados da curva (278 na carga de `2026-09-14`) |
| ANBIMA (`MS`) | nome da curva (`NTN-B`) | títulos gravados na curva (14 nas cargas de outubro de 2026) |
| Bloomberg (`BLC2`) | nome da curva (`SOFR`) | nós gravados (20) |

Duas curvas sobre o mesmo código da fonte SHALL aparecer as duas, cada uma com as suas linhas.

#### Scenario: ANBIMA com só a NTN-B ligada
- **WHEN** o arquivo `ms` traz LTN, NTN-F, LFT, NTN-C e 14 NTN-B, e só a curva `NTN-B` está ligada em `tCurvaPrvdr`
- **THEN** `verticesPorCurva` = `{ "NTN-B": 14 }`

#### Scenario: Bloomberg completa
- **WHEN** a carga da SOFR grava os 20 nós
- **THEN** `verticesPorCurva` = `{ "SOFR": 20 }`

#### Scenario: Vértice que o modelo descarta
- **WHEN** a `PRE` tem 278 linhas gravadas e o modelo descarta 2 por prazo repetido
- **THEN** o aviso traz `"PRE": 278`, não 276

### Requirement: Quando avisa e quando não avisa
O processor SHALL avisar o engine só depois de uma carga com **alguma curva gravada**. Uma carga rejeitada (422), com data de outro dia (503) ou sem nenhuma curva ligada (200 com `verticesPorCurva` vazio) MUST NOT gerar aviso. A transação da gravação SHALL ser **única** por carga: ou todas as curvas da carga são gravadas e avisadas, ou nenhuma.

#### Scenario: Carga sem curva ligada
- **WHEN** a carga ANBIMA é gravada, mas nenhum `Titulo` do arquivo tem curva ligada
- **THEN** a resposta é 200 com `verticesPorCurva` vazio, e nenhum aviso é enviado

### Requirement: Repetição, prazos e logs
O aviso SHALL ser repetido com o mesmo `idCarga` em erro de rede, 5xx e 409 (construção em andamento no engine), com espera inicial de 1 segundo dobrando até 60 segundos e variação de até 20%, por até 10 minutos, com tempo limite de 150 segundos por chamada. Aos 2 minutos sem 2xx, o log SHALL ter `AVISO_ATRASADO` (uma vez). Esgotada a janela, ou com 4xx diferente de 409, o log SHALL ter `CARGA_FALHOU` (nível de erro, com `idCarga`, fonte e data-base), e o contador `processor.carga.falhou` com o rótulo da fonte SHALL ser incrementado. Com 2xx, o log SHALL ter o corpo devolvido (o resultado por curva). A recuperação é o reprocessamento da data.

#### Scenario: Engine fora por 3 minutos
- **WHEN** a carga é gravada e o engine fica fora por 3 minutos
- **THEN** o orquestrador já recebeu o 200, o log tem `AVISO_ATRASADO` aos 2 minutos, e o aviso é aceito quando o engine volta

#### Scenario: Janela esgotada
- **WHEN** o engine não responde 2xx em 10 minutos
- **THEN** o log tem `CARGA_FALHOU` com o `idCarga`, a fonte e a data-base, e o contador da fonte sobe; o operador reprocessa a data depois

#### Scenario: 400 do engine
- **WHEN** o engine responde 400 `PARAMETRO_INVALIDO` ao aviso
- **THEN** o aviso não é repetido, e o log tem `CARGA_FALHOU`

### Requirement: Engine confere a quantidade avisada
Para cada curva da carga, o engine SHALL contar as linhas que lê da tabela bruta (curva e data-base) e comparar com a quantidade de `verticesPorCurva` para o nome da curva. Se forem **diferentes**, a curva MUST falhar com `INSUMO_INVALIDO`, sem construir, e a mensagem SHALL trazer a quantidade avisada e a lida. A falha de uma curva MUST NOT impedir as outras da carga. Se forem iguais, a construção segue como na spec `curve-load-trigger`.

#### Scenario: Leitura truncada
- **WHEN** o aviso diz `"PRE": 278` e o engine lê 277 linhas da `PRE`
- **THEN** a `PRE` volta com `INSUMO_INVALIDO` e a mensagem `avisadas 278, lidas 277`, nada é gravado, e as outras curvas da carga seguem

#### Scenario: Quantidade igual
- **WHEN** o aviso diz `"PRE": 278` e o engine lê 278 linhas
- **THEN** a `PRE` é construída normalmente

### Requirement: Fontes independentes
O aviso de uma fonte MUST NOT depender do de outra. A falha do aviso da ANBIMA MUST NOT atrasar nem impedir o da B3 ou o da Bloomberg, e cada fonte SHALL ter o seu contador de falhas.

#### Scenario: B3 avisada com a ANBIMA falhando
- **WHEN** o aviso da ANBIMA está em repetição e chega a carga da B3
- **THEN** o aviso da B3 sai na hora, sem esperar o da ANBIMA
