## Purpose

No `services/curves`, consultar, gravar, substituir e apagar à mão os vértices de uma curva numa data-base, como operação de contingência. Os vértices são a curva construída, em `tDadoVertcCurva`; a curva interpolada, um valor por dia corrido em `tDadoCurva`, é calculada e gravada só pelo engine. O engine continua sendo quem constrói, recalcula e interpola; o `services/curves` é quem edita os vértices manualmente e, depois de cada edição, pede ao engine a regravação da curva interpolada. Os dois compartilham no banco a mesma trava por curva e a mesma fórmula de `hashPontos`.

## ADDED Requirements

### Requirement: Rotas dos vértices
O serviço SHALL expor (prefixo `/api/v1`), identificando a curva pelo código (`tCurvaMercd.cTickerIdtfdUnic`):

| Rota | Uso |
|---|---|
| `GET /curvas-mercado/{codigo}/vertices?de=AAAA-MM-DD&ate=AAAA-MM-DD` | listar as datas-base com vértices no intervalo, com quantidade de vértices e `hashPontos` de cada uma (intervalo máximo de 366 dias) |
| `GET /curvas-mercado/{codigo}/vertices/{dataBase}` | vértices gravados na data-base (data, valor e dias úteis) e `hashPontos` |
| `PUT /curvas-mercado/{codigo}/vertices/{dataBase}` | gravar a lista completa de vértices da data-base, substituindo a atual |
| `DELETE /curvas-mercado/{codigo}/vertices/{dataBase}` | apagar todos os vértices da data-base |

A consulta devolve só o dado gravado em `tDadoVertcCurva`: data (`dVertcReft`), valor (`vPrecoTx`) e `diasUteis` (`cDiaUtil`, nulo quando não há); fatores, calendário e interpolação são do engine. Os erros, a autenticação, o `X-Correlation-Id` e o horário seguem a spec `cadastro-curva-mercado`, com o código de erro adicional `VERTICES_INVALIDOS` (422).

#### Scenario: Consulta dos vértices
- **WHEN** o cliente chama `GET /api/v1/curvas-mercado/PRE/vertices/2026-09-14` depois da construção pelo engine
- **THEN** a resposta traz os 278 vértices (data, valor e os dias úteis publicados pela B3 que o engine gravou) e o mesmo `hashPontos` informado pelo engine

### Requirement: Gravação da lista completa
`PUT .../vertices/{dataBase}` SHALL receber `{ "vertices": [ { "data": "AAAA-MM-DD", "valor": "13.9000000", "diasUteis": 1 } ] }`, com `valor` como string decimal (sem passar por ponto flutuante) e `diasUteis` opcional (inteiro; ausente ou nulo = sem dias úteis informados), e deixar os vértices da data-base exatamente iguais à lista: o que o gestor enviou é o que fica gravado e o que o engine usa, e vértices gravados ausentes da lista são apagados. Vale também para data-base sem vértices. Numa única transação, o serviço SHALL:
1. travar a linha da curva em `tCurvaMercd` com `UPDLOCK, ROWLOCK` (a mesma trava que o engine usa na construção), esperando o tempo que for preciso dentro do tempo limite da requisição (60 segundos): a construção de uma curva pelo engine segura a trava por poucos segundos;
2. ler os vértices gravados da data-base em `tDadoVertcCurva` (já sob a trava) e comparar com a lista, depois do arredondamento: um vértice muda se o valor ou os dias úteis mudarem;
3. gravar só a diferença em `tDadoVertcCurva`: `DELETE` dos vértices gravados ausentes da lista; e, para cada vértice novo ou alterado, a linha com `dBaseReft` = data-base, `cTickerIndcd` = nome, `dVertcReft` = data, `vPrecoTx` = valor arredondado, `cDiaUtil` = `diasUteis` (nulo quando não informado), `cQtdDiaPer` = data do vértice − data-base em dias corridos, `cQtdDiaReft` = dias da data-base ao vértice em 30/360 (Bond Basis, conta de datas, sem calendário) e fatores nulos (`UPDATE` do vértice existente, `INSERT` do novo). Os vértices que não mudaram mantêm a linha do engine, com os fatores. O engine obedece os dias úteis informados (spec `curve-build-pipeline` do change `engine-construcao-curvas`, requisito "Dias úteis publicados pela fonte ou informados pelo usuário"); sem eles, usa o calendário;
4. reler os vértices da data-base e conferir que o `hashPontos` relido é igual ao `hashPontos` da lista enviada (depois do arredondamento). Se for diferente, MUST desfazer a transação e responder 500 `ERRO_INTERNO`, sem gravar nada.

Se a lista for igual ao que está gravado, nada SHALL ser escrito nem registrado no log, e a resposta traz o aviso `SEM_MUDANCA`.

Depois do commit, inclusive com `SEM_MUDANCA` (reenviar a lista corrige uma interpolada que ficou desatualizada), o serviço SHALL chamar `POST /api/v1/curvas/{codigo}/{dataBase}/interpolada` do engine (spec `curve-engine-api` do change `engine-construcao-curvas`), com a identidade de serviço dele e tempo limite de 60 segundos, para regravar a curva interpolada a partir dos vértices novos. A edição MUST NOT depender dessa chamada: se o engine não responder ou devolver erro, os vértices continuam gravados, e a resposta traz o aviso `INTERPOLADA_DESATUALIZADA`, com o motivo; a interpolada fica desatualizada até uma nova regravação (reenviando a lista, ou por um operador no engine), e o painel mostra a curva nessa situação.

O valor SHALL ser arredondado por `CASAS_DECIMAIS` e `MODO_ARREDONDAMENTO` da configuração vigente na data-base (spec `configuracao-calculo-curva`), porque é assim que o engine grava e usa os vértices; todo valor que mudar no arredondamento SHALL gerar o aviso `VALOR_ARREDONDADO`, com o valor enviado e o gravado. Sem configuração vigente, o valor SHALL ser gravado como enviado, com o aviso `SEM_CONFIGURACAO`. A resposta SHALL ser 200 com os vértices relidos do banco, em ordem de data, com o valor como string decimal na escala gravada (spec `cadastro-curva-mercado`, contrato de tipos), e o `hashPontos` novo. O serviço MUST NOT alterar `dBaseReft` nem `cUsuarCalc` de `tCurvaMercd`, MUST NOT calcular fatores nem gravar a curva interpolada (`tDadoCurva`), que é do engine. Se a lista for igual à gravada (`SEM_MUDANCA`), nada é apagado.

#### Scenario: Edição de um valor
- **WHEN** o gestor envia os 278 vértices da `PRE` de `2026-09-14` com o valor de `2027-01-04` alterado
- **THEN** só a linha de `2027-01-04` em `tDadoVertcCurva` é atualizada, o engine regrava a curva interpolada da data, a resposta é 200 com os 278 vértices e um `hashPontos` novo, e a interpolação seguinte no engine usa o valor novo

#### Scenario: Engine fora na edição
- **WHEN** o gestor altera um vértice da `PRE` de `2026-09-14` com o engine fora
- **THEN** o vértice é gravado, a resposta é 200 com o aviso `INTERPOLADA_DESATUALIZADA`, e `tDadoCurva` continua com a interpolação anterior até a regravação

#### Scenario: Dias úteis informados pelo gestor
- **WHEN** o gestor envia os vértices da `PRE` de `2026-09-14` com o vértice de `2027-01-04` alterado para `"diasUteis": 76`
- **THEN** a linha desse vértice em `tDadoVertcCurva` passa a ter `cDiaUtil` = 76 e fatores nulos, as linhas dos outros 277 vértices continuam as do engine, e a interpolação seguinte usa 76 dias úteis para esse vértice

#### Scenario: Vértice retirado da lista
- **WHEN** o gestor envia 277 dos 278 vértices da `PRE` de `2026-09-14`, sem o de `2027-01-04`
- **THEN** o vértice de `2027-01-04` é apagado, e os 277 ficam gravados exatamente como enviados

#### Scenario: Valor com casas a mais
- **WHEN** o gestor envia `13.123456789` para um vértice da `PRE`, que tem 7 casas `HALF_UP`
- **THEN** o vértice é gravado com `13.1234568`, e a resposta traz o aviso `VALOR_ARREDONDADO` com os dois valores

#### Scenario: Curva digitada numa data sem construção
- **WHEN** o engine não construiu a `PRE` de `2026-09-15`, e o gestor envia 12 vértices para essa data
- **THEN** os 12 vértices são gravados, e o webhook de carga do engine, se chegar depois, devolve essa curva como `EXISTENTE`, sem sobrescrever os vértices manuais

#### Scenario: Construção em andamento
- **WHEN** o gestor grava vértices da `PRE` de `2026-09-14` enquanto o engine está construindo essa mesma curva e data
- **THEN** a edição espera o fim da transação do engine e grava por cima, e a resposta é 200 com os vértices do gestor

### Requirement: Validação dos vértices
A edição manual só MUST ser recusada quando o dado não pode ser gravado de forma consistente no banco. Regra de negócio nunca recusa: vira aviso, e o vértice é gravado. A lista MUST ser rejeitada com 422 `VERTICES_INVALIDOS`, sem alterar nada e com um item em `detalhes` por vértice, só quando:
- estiver vazia (para apagar, usa-se o `DELETE`);
- algum vértice não tiver data ou valor, a data não for uma data válida, ou o valor não for decimal;
- houver datas repetidas (violaria a PK de `tDadoVertcCurva`);
- o valor, depois do arredondamento, não couber em `vPrecoTx` (`DECIMAL(28,12)`: até 16 dígitos inteiros e 12 casas); sem configuração vigente, um valor com mais de 12 casas também é recusado, porque o banco o alteraria em silêncio;
- `diasUteis` informado não for um inteiro que caiba em `INT`.

A curva inexistente responde 404 `NAO_ENCONTRADO`. Todas as regras de negócio SHALL gerar avisos na resposta (`avisos`, com código, data do vértice e motivo), e os vértices SHALL ser gravados:

| Aviso | Quando | Efeito no engine |
|---|---|---|
| `PONTO_ANTES_DA_DATA_BASE` | data igual ou anterior à data-base | o vértice fica fora da interpolação, com `PONTO_DESCARTADO_PRAZO_NAO_POSITIVO` |
| `PONTO_EM_FIM_DE_SEMANA` | data em sábado ou domingo | sem `diasUteis` informado, tratado como vértice no mesmo prazo do dia útil anterior |
| `PONTO_EM_FERIADO` | data é feriado no calendário da configuração vigente; o erro pode estar no cadastro de feriados, e não no vértice | sem `diasUteis` informado, tratado como vértice no mesmo prazo do dia útil anterior |
| `VALOR_NAO_POSITIVO` | unidade `PRECO` ou `PONTOS` com valor menor ou igual a zero | a interpolação `FlatForward` falha no engine com `PONTOS_NAO_INTERPOLAVEIS` até o vértice ser corrigido |
| `VALOR_ARREDONDADO` | valor com mais casas que `CASAS_DECIMAIS` da configuração vigente | o valor usado é o arredondado, mostrado no aviso |
| `SEM_CONFIGURACAO` | sem configuração vigente na data-base | valor gravado como enviado, sem arredondar |
| `CALENDARIO_NAO_VERIFICADO` | engine fora, ou sem configuração vigente (portanto sem calendário) | feriados e dias úteis não conferidos |
| `DIAS_UTEIS_DIFERENTES_DO_CALENDARIO` | `diasUteis` informado diferente da contagem do calendário da configuração vigente | o engine usa os dias informados, com `CALENDARIO_DIVERGENTE` |
| `DIAS_UTEIS_INCOERENTES` | `diasUteis` menor que 1, ou maior que os dias corridos da data-base ao vértice | o engine usa os dias informados; menor que 1 fica fora da interpolação (`PONTO_DESCARTADO_PRAZO_NAO_POSITIVO`) |
| `DIAS_UTEIS_FORA_DE_ORDEM` | em ordem de data, `diasUteis` igual ou menor que o de um vértice anterior | o engine descarta o vértice da interpolação (`PONTO_DESCARTADO_MESMO_PRAZO`) |

O tratamento no engine é o do requisito "Vértices no mesmo prazo do eixo" da spec `curve-build-pipeline` do change `engine-construcao-curvas`. Nenhuma dependência externa MUST impedir a gravação. Os feriados SHALL ser obtidos do engine, por `GET /api/v1/calendarios/{CALENDARIO}?mercado={MERCADO_CALENDARIO}&anoInicial=&anoFinal=` (spec `calendar-management`), com token de serviço e tempo limite de 10 segundos, cobrindo os anos das datas enviadas; se o engine não responder, a gravação segue com `CALENDARIO_NAO_VERIFICADO`. Taxas podem ser negativas, sem aviso.

#### Scenario: Vértice no sábado
- **WHEN** a lista da `PRE` tem um vértice em `2026-12-26` (sábado)
- **THEN** os vértices são gravados, e a resposta é 200 com o aviso `PONTO_EM_FIM_DE_SEMANA` citando `2026-12-26`

#### Scenario: Data repetida
- **WHEN** a lista da `PRE` tem dois vértices em `2027-01-04`
- **THEN** a resposta é 422 com `VERTICES_INVALIDOS` citando `2027-01-04`, e nada é gravado

#### Scenario: Vértice num feriado
- **WHEN** a lista da `PRE` tem um vértice em `2026-12-25`
- **THEN** os vértices são gravados, e a resposta é 200 com o aviso `PONTO_EM_FERIADO` citando `2026-12-25` e o `Brazil`/`Settlement`

#### Scenario: Engine fora durante a edição
- **WHEN** o engine está fora e o gestor grava os vértices da `DCL`
- **THEN** os vértices são gravados com as demais validações, e a resposta traz o aviso `CALENDARIO_NAO_VERIFICADO`

### Requirement: Exclusão dos vértices de uma data
`DELETE .../vertices/{dataBase}` SHALL apagar todos os vértices da curva na data-base em `tDadoVertcCurva` e a curva interpolada da data em `tDadoCurva`, na mesma transação travada, para que ninguém leia uma interpolada de vértices que não existem mais. É o único caso em que o serviço escreve em `tDadoCurva`, e só para apagar. Depois, a curva fica "não construída" naquela data para o engine, que pode construí-la de novo pela carga ou pela construção manual.

#### Scenario: Desfazer uma curva digitada
- **WHEN** o gestor apaga os vértices manuais da `PRE` de `2026-09-15`
- **THEN** a consulta do engine responde `CURVA_NAO_CONSTRUIDA` para essa data, e uma construção posterior grava os vértices da fonte

### Requirement: Preferência da edição manual
A edição manual é feita pelo gestor da curva, no front, e MUST NOT ser recusada por concorrência: não há conferência de versão, e a gravação sempre se aplica sobre os vértices atuais, registrando no log o `hashPontos` anterior. A preferência sobre o engine SHALL resultar de três regras, sem coordenação por API:
- se o engine estiver construindo a mesma curva, a edição espera a transação dele e grava por cima;
- se a edição acontecer antes, a construção automática do engine (webhook de carga ou construção sem recálculo) encontra vértices gravados e devolve `EXISTENTE`, sem sobrescrever, com o aviso `PONTOS_DIFERENTES_DA_FONTE` quando os vértices manuais diferem do que a fonte produz;
- só um recálculo forçado por um usuário (`forcarRecalculo=true` no engine) substitui vértices existentes, inclusive manuais.

O `hashPontos` SHALL ser calculado exatamente como na spec `curve-build-pipeline` do engine: SHA-256, em hexadecimal minúsculo, das linhas `AAAA-MM-DD;valor`, em ordem de data, separadas por `\n`, com o valor na forma canônica (sem zeros à direita, sem expoente, ponto decimal; `13.900000000000` lido do banco vira `13.9`), usando o mesmo vetor de teste do engine.

#### Scenario: Engine recalculou depois da leitura
- **WHEN** o gestor abre os vértices da `PRE` no front, um usuário recalcula a data no engine, e o gestor salva a edição
- **THEN** a edição do gestor é gravada por cima dos vértices recalculados, e o log `VERTICES_EDITADOS` traz o `hashPontos` do recálculo como anterior

#### Scenario: Carga chega depois da edição
- **WHEN** o gestor gravou vértices da `PRE` de `2026-09-15`, e depois chega o webhook de carga dessa data no engine
- **THEN** o engine devolve a `PRE` como `EXISTENTE`, com o aviso `PONTOS_DIFERENTES_DA_FONTE` se os vértices do gestor diferem da fonte, e os vértices do gestor continuam gravados

### Requirement: Sem auditoria, com log
A edição manual é contingência e MUST NOT gerar registro de auditoria. Cada `PUT` e `DELETE` bem-sucedido que altere algum vértice SHALL registrar o evento de log `VERTICES_EDITADOS` (nível `AVISO`, para ser visível), com `correlationId`, usuário, código, nome, data-base, operação (`SUBSTITUICAO` ou `EXCLUSAO`), origem (`API` ou `PLANILHA`), `idLote` (quando vier da planilha), quantidade de vértices antes e depois e `hashPontos` antes e depois, no horário de Brasília.

#### Scenario: Rastro de uma edição
- **WHEN** os vértices da `PRE` de `2026-09-14` são editados
- **THEN** o log tem `VERTICES_EDITADOS` com o usuário e os dois `hashPontos`, e nenhum registro de auditoria é gravado
