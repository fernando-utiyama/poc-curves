## Purpose

Define a API HTTP do engine para construir, consultar e interpolar curvas, identificando a curva sempre pelo código e pela data-base, e para gerir os scripts Groovy de modelo por tipo e nome, sem ids técnicos.

## ADDED Requirements

### Requirement: Curva identificada por código e data-base
Toda operação sobre curva SHALL identificar a curva pelo código (ex.: `PRE`, `DCL`) e pela data-base (formato `AAAA-MM-DD`) na rota. As operações de leitura também SHALL existir pelo nome de exibição (requisito "Visualização pelo nome da curva"). A API MUST NOT exigir id técnico de curva, de versão ou de cadastro para construir, consultar ou interpolar. Código sem cadastro MUST resultar em 404; data em formato inválido MUST resultar em 400.

#### Scenario: Código desconhecido
- **WHEN** o cliente chama `GET /api/v1/curvas/XYZ/2026-09-14`
- **THEN** a resposta é 404 informando que `XYZ` não tem cadastro

#### Scenario: Data inválida
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/14-09-2026`
- **THEN** a resposta é 400 informando o formato esperado da data

### Requirement: Visualização pelo nome da curva
Para usuários que não conhecem o código, a API SHALL oferecer as operações de **leitura** também pelo nome de exibição cadastrado, informado como parâmetro de query:
- `GET /api/v1/curvas/por-nome/{dataBase}?nome=...`: mesmo conteúdo da consulta de pontos por código;
- `GET /api/v1/curvas/por-nome/{dataBase}/interpolacao?nome=...&du=...`: mesmo conteúdo da interpolação por código.

A comparação do nome SHALL ignorar maiúsculas e minúsculas, acentos e espaços nas pontas. Toda resposta por nome SHALL trazer também o código da curva. Nome sem correspondência MUST resultar em 404. Nome que corresponda a mais de uma curva após essa normalização MUST resultar em 409, listando os códigos e nomes encontrados. Nas rotas por código, código sem cadastro MUST resultar em 404, e código atribuído a mais de uma curva MUST resultar em 409. Operações de escrita (construir e editar pontos) MUST existir só pelo código.

#### Scenario: Consulta pelo nome
- **WHEN** a curva `DCL` está cadastrada com o nome `Cupom limpo de dólar` e o cliente chama `GET /api/v1/curvas/por-nome/2026-09-14?nome=CUPOM LIMPO DE DOLAR`
- **THEN** a resposta traz os pontos da `DCL` em `2026-09-14` e o código `DCL`

#### Scenario: Nome ambíguo
- **WHEN** duas curvas estão cadastradas com nomes que diferem só em acento ou maiúsculas, e o cliente consulta por esse nome
- **THEN** a resposta é 409 listando os códigos e nomes das duas curvas

#### Scenario: Escrita pelo nome não existe
- **WHEN** o cliente tenta `PUT /api/v1/curvas/por-nome/2026-09-14/pontos?nome=DIxPRE`
- **THEN** a resposta é 404, porque a edição só existe pelo código

### Requirement: Catálogo de curvas
`GET /api/v1/curvas` SHALL listar as curvas cadastradas com código, nome de exibição e unidade. O parâmetro opcional `nome` SHALL filtrar por trecho do nome, com a mesma comparação sem maiúsculas e sem acentos.

#### Scenario: Busca por trecho do nome
- **WHEN** o cliente chama `GET /api/v1/curvas?nome=cupom`
- **THEN** a resposta lista as curvas cujo nome contém "cupom", como `DCL` e `DPL`, com código, nome e unidade

### Requirement: Construir curva
`POST /api/v1/curvas/{codigo}/{dataBase}/construcao` SHALL disparar a construção da curva, e o parâmetro opcional `forcarRecalculo` SHALL disparar a reconstrução. A resposta de sucesso SHALL trazer: código, data-base, os modelos usados com sua origem, a quantidade de pontos gravados e o tempo de execução. A falha por insumo ausente MUST resultar em 422, informando o código e a data.

#### Scenario: Construção bem-sucedida
- **WHEN** o cliente chama `POST /api/v1/curvas/PRE/2026-09-14/construcao`
- **THEN** a resposta traz `PRE`, `2026-09-14`, o modelo de construção `PRONTA_TS_B3` e o calendário `Brazil`/`Settlement` com suas origens, e a quantidade de pontos gravados (278)

#### Scenario: Sem insumo na data
- **WHEN** o cliente pede a construção de `DPL` numa data sem insumo
- **THEN** a resposta é 422 informando `DPL` e a data

### Requirement: Consultar curva gravada
`GET /api/v1/curvas/{codigo}/{dataBase}` SHALL devolver os pontos gravados da curva na data: data do ponto, dias úteis, dias corridos, valor e, quando a curva é de taxa, fatores calculados na consulta. Se a curva não tiver sido construída naquela data, a resposta MUST ser 404.

#### Scenario: Curva ainda não construída
- **WHEN** o cliente consulta `GET /api/v1/curvas/DCL/2026-09-14` antes de qualquer construção dessa data
- **THEN** a resposta é 404 informando que não há curva gravada para `DCL` em `2026-09-14`

### Requirement: Editar os pontos da curva
`PUT /api/v1/curvas/{codigo}/{dataBase}/pontos` SHALL receber a **lista completa** de pontos da curva na data (data do ponto e valor) e substituir, numa única transação, todos os pontos gravados daquela data pela lista recebida. Pontos gravados que não estiverem na lista são removidos. A operação SHALL valer também para uma data sem pontos gravados, criando-os.

Antes de gravar, a lista MUST ser validada, e qualquer violação resulta em 422 sem alterar nada:
- lista vazia;
- ponto sem data ou sem valor;
- datas repetidas;
- data igual ou anterior à data-base;
- data que não é dia útil no calendário cadastrado da curva.

A resposta de sucesso SHALL trazer o código, a data-base e os pontos como ficaram gravados, ordenados por data: dias úteis e dias corridos recalculados pelo calendário, e valor arredondado pelo cadastro. A operação MUST exigir autenticação e SHALL registrar no log estruturado o usuário e a origem manual. Interpolações feitas depois da edição SHALL usar os pontos editados. Uma construção posterior com recálculo substitui os pontos editados pelos da fonte.

#### Scenario: Edição bem-sucedida
- **WHEN** o cliente envia para `PUT /api/v1/curvas/PRE/2026-09-14/pontos` a lista de 278 pontos com o valor de `2027-01-04` alterado
- **THEN** a resposta é 200 com os 278 pontos gravados, incluindo o valor novo de `2027-01-04`, e a interpolação seguinte usa esse valor

#### Scenario: Lista menor remove pontos
- **WHEN** o cliente envia uma lista de 270 pontos para uma data que tinha 278
- **THEN** a data passa a ter exatamente os 270 pontos enviados

#### Scenario: Data não útil
- **WHEN** a lista enviada contém um ponto em `2026-09-19` (sábado)
- **THEN** a resposta é 422 informando o ponto inválido, e os pontos gravados continuam os anteriores

#### Scenario: Sem autenticação
- **WHEN** um cliente sem credencial chama o endpoint de edição
- **THEN** a resposta é 401 e nada é alterado

### Requirement: Interpolar prazos arbitrários
`GET /api/v1/curvas/{codigo}/{dataBase}/interpolacao` SHALL receber os prazos desejados, em dias úteis (`du`) ou em datas de vencimento (`data`), repetidos quantas vezes o cliente precisar. A resposta SHALL trazer o valor de cada prazo, calculado no momento da consulta a partir dos pontos gravados, com o modelo de interpolação e as políticas de extrapolação do cadastro da curva. Cada valor SHALL indicar se foi interpolado, extrapolado ou é um ponto gravado, e a resposta SHALL identificar os modelos usados. Se a curva não tiver pontos gravados na data, a resposta MUST ser 404. A API MUST NOT aceitar método de interpolação ou política de extrapolação vindos do cliente. Um prazo fora do domínio sob política estrita MUST resultar em 422, informando o prazo.

#### Scenario: Interpolação por dias úteis
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14/interpolacao?du=21&du=252`
- **THEN** a resposta traz os valores de 21 e 252 dias úteis calculados com a interpolação cadastrada da `PRE` (`Discount` + `LogLinear` em `Business252`)

#### Scenario: Cliente tenta escolher o método
- **WHEN** o cliente envia o parâmetro `metodo=Linear` na interpolação
- **THEN** a resposta é 400 informando que a interpolação vem do cadastro da curva

### Requirement: Gestão de scripts de modelo por tipo e nome
A gestão de scripts Groovy SHALL ser feita por tipo (`construcao`, `interpolacao`, `calendario`) e nome, também sem id técnico:
- `POST /api/v1/modelos/{tipo}/{nome}` envia uma nova versão em rascunho;
- `POST /api/v1/modelos/{tipo}/{nome}/versoes/{versao}/validacao` valida a versão;
- `POST /api/v1/modelos/{tipo}/{nome}/versoes/{versao}/ativacao` ativa a versão;
- `POST /api/v1/modelos/{tipo}/{nome}/desativacao` desativa o script ativo;
- `GET /api/v1/modelos/{tipo}/{nome}` lista as versões com status e hash.

As operações que alteram scripts MUST exigir autenticação. A resposta de erro MUST NOT expor o stack trace do compilador ou da execução, apenas a mensagem de falha.

#### Scenario: Envio sem autenticação
- **WHEN** um cliente sem credencial chama `POST /api/v1/modelos/interpolacao/LogLinear`
- **THEN** a resposta é 401 e nenhuma versão é criada

#### Scenario: Ativação de versão não validada
- **WHEN** o cliente pede a ativação de uma versão que não passou na validação
- **THEN** a resposta é 422 informando que a versão precisa ser validada antes

### Requirement: Correlação de requisições
Toda resposta SHALL trazer o identificador de correlação: o recebido no cabeçalho `X-Correlation-Id`, ou um gerado quando o cliente não enviar.

#### Scenario: Cliente sem correlação
- **WHEN** o cliente chama a API sem `X-Correlation-Id`
- **THEN** a resposta traz um identificador de correlação gerado pelo engine
