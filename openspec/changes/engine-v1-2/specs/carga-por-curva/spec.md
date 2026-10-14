## ADDED Requirements

### Requirement: Webhook de carga recebe as linhas por nome da curva
O corpo de `POST /api/v1/cargas` SHALL trazer `verticesPorCurva` no lugar de `verticesPorCodigo`: um mapa do **nome da curva** (`cTickerIndcd`, a identidade da curva no projeto) para a quantidade de linhas gravadas na tabela bruta daquela curva na data-base, inclusive as que o modelo vier a descartar. O engine SHALL construir as curvas cujo nome está no mapa e cuja origem principal tem a fonte e o produto da carga. O código da fonte (código da B3, `Titulo` da ANBIMA, membro do ticker da Bloomberg) MUST NOT ser usado para selecionar curvas. Campo ausente, mapa vazio, quantidade negativa ou data inválida MUST resultar em 400 `PARAMETRO_INVALIDO`. Nome sem curva cadastrada SHALL gerar um evento `AVISO` no log, sem erro.

#### Scenario: Carga da B3 pelo nome
- **WHEN** chega a carga B3/`TS` de `2026-09-14` com `verticesPorCurva` = `{ "PRE": 278, "DCL": 278 }`
- **THEN** o engine constrói a `PRE` e a `DCL`, cada uma na sua transação

#### Scenario: Duas curvas no mesmo código da fonte
- **WHEN** as curvas `PRE` e `PRE_252` usam o mesmo código da B3, e o aviso traz `{ "PRE": 278, "PRE_252": 278 }`
- **THEN** as duas são construídas, cada uma com o seu resultado na resposta

#### Scenario: Nome sem curva
- **WHEN** o aviso traz `{ "XYZ": 10 }` e não há curva `XYZ`
- **THEN** o log tem um evento `AVISO` e a resposta é 200 sem a curva

### Requirement: Conferência da quantidade avisada
Para cada curva da carga, o engine SHALL contar as linhas que lê da tabela bruta (curva e data-base) e comparar com `verticesPorCurva` para o nome da curva. Se forem diferentes, a curva MUST falhar com `INSUMO_INVALIDO`, sem construir, e a mensagem SHALL trazer a quantidade avisada e a lida. A falha de uma curva MUST NOT impedir as outras da carga. Se forem iguais, a construção segue como em `curve-load-trigger`.

#### Scenario: Leitura truncada
- **WHEN** o aviso diz `"PRE": 278` e o engine lê 277 linhas
- **THEN** a `PRE` volta com `INSUMO_INVALIDO` e a mensagem `avisadas 278, lidas 277`, nada é gravado, e as outras curvas seguem

#### Scenario: Quantidade igual
- **WHEN** o aviso diz `"PRE": 278` e o engine lê 278 linhas
- **THEN** a `PRE` é construída normalmente
