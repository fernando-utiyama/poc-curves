## Purpose

Define a API HTTP do engine para construir, consultar, editar, interpolar e simular curvas, identificando a curva pelo código (ou, só para leitura, pelo nome) e pela data-base, e para gerir os scripts Groovy de modelo por tipo e nome. Fixa rotas, parâmetros, corpos, códigos HTTP e códigos de erro.

## ADDED Requirements

### Requirement: Rotas
O engine SHALL expor exatamente estas rotas (prefixo `/api/v1`):

| Método e rota | Uso | Papel exigido |
|---|---|---|
| `POST /cargas` | aviso de carga concluída (spec `curve-load-trigger`) | `Curvas.Processor` |
| `GET /curvas?nome=` | catálogo | `Curvas.Leitura` |
| `POST /curvas/{codigo}/{dataBase}/construcao?forcarRecalculo=` | construir | `Curvas.Operador` |
| `GET /curvas/{codigo}/{dataBase}?formato=` | pontos gravados | `Curvas.Leitura` |
| `PUT /curvas/{codigo}/{dataBase}/pontos` | editar pontos | `Curvas.Operador` |
| `GET /curvas/{codigo}/{dataBase}/interpolacao?du=&data=&formato=` | interpolar | `Curvas.Leitura` |
| `GET /curvas/{codigo}/{dataBase}/simulacao?du=&data=&formato=` | simular construção sem gravar | `Curvas.Leitura` |
| `GET /curvas/{codigo}/{dataBase}/historico` | histórico de auditoria (spec `curve-audit-history`) | `Curvas.Leitura` |
| `GET /curvas/{codigo}/{dataBase}/historico/{idAuditoria}?formato=` | pontos substituídos numa operação | `Curvas.Leitura` |
| `GET /curvas/por-nome/{dataBase}?nome=&formato=` | pontos gravados, pelo nome | `Curvas.Leitura` |
| `GET /curvas/por-nome/{dataBase}/interpolacao?nome=&du=&data=&formato=` | interpolar, pelo nome | `Curvas.Leitura` |
| `GET /curvas/por-nome/{dataBase}/simulacao?nome=&du=&data=&formato=` | simular, pelo nome | `Curvas.Leitura` |
| `POST /modelos/{tipo}/{nome}` | enviar script | `Curvas.ModelosAutor` |
| `POST /modelos/{tipo}/{nome}/versoes/{versao}/validacao` | validar script | `Curvas.ModelosAutor` |
| `POST /modelos/{tipo}/{nome}/versoes/{versao}/ativacao` | ativar script | `Curvas.ModelosAprovador` |
| `POST /modelos/{tipo}/{nome}/desativacao` | desativar script | `Curvas.ModelosAprovador` |
| `GET /modelos/{tipo}/{nome}` | listar versões | `Curvas.Leitura` |
| `POST /calendarios/{nome}/importacao?mercado=&anoInicial=&anoFinal=` | importar planilha de feriados (spec `calendar-management`) | `Curvas.ModelosAutor` |
| `GET /calendarios/{nome}?mercado=&anoInicial=&anoFinal=&versao=&formato=` | exportar feriados | `Curvas.Leitura` |

As rotas antigas (`POST /api/v1/curvas/construir`, `POST /api/v1/calculo`, `POST /api/v1/modelos/upload`) MUST ser removidas. Escrita (construir, editar pontos) MUST existir só pelo código.

#### Scenario: Escrita pelo nome não existe
- **WHEN** o cliente chama `PUT /api/v1/curvas/por-nome/2026-09-14/pontos?nome=DIxPRE`
- **THEN** a resposta é 404

### Requirement: Autenticação e papéis
Toda rota de `/api/v1` MUST exigir um token JWT do Microsoft Entra ID (`Authorization: Bearer`), validado por emissor, audiência, assinatura e validade (propriedades `engine.seguranca.emissor` e `engine.seguranca.audiencia`). Só os endpoints de saúde do Actuator ficam sem autenticação. O acesso SHALL ser decidido pelos papéis de aplicação (`roles`) do token, conforme a tabela de rotas:
- `Curvas.Leitura`: consultas, interpolação, simulação, histórico, catálogo e lista de scripts;
- `Curvas.Operador`: construir, recalcular e editar pontos; inclui `Curvas.Leitura`;
- `Curvas.Processor`: webhook de carga; concedido só à identidade de serviço do processor (client credentials);
- `Curvas.ModelosAutor`: enviar e validar scripts;
- `Curvas.ModelosAprovador`: ativar e desativar scripts.

Token ausente ou inválido MUST resultar em 401 `NAO_AUTENTICADO`; token sem o papel exigido, em 403 `SEM_PERMISSAO`. O usuário gravado na auditoria e no log SHALL ser o `preferred_username` do token ou, para identidade de serviço, o `appid`. A autenticação MUST NOT poder ser desligada por configuração no perfil de produção.

#### Scenario: Leitura sem papel
- **WHEN** um usuário autenticado sem `Curvas.Leitura` consulta `GET /api/v1/curvas/PRE/2026-09-14`
- **THEN** a resposta é 403 com `SEM_PERMISSAO`

#### Scenario: Webhook por usuário comum
- **WHEN** um usuário com `Curvas.Operador` chama `POST /api/v1/cargas`
- **THEN** a resposta é 403 com `SEM_PERMISSAO`, porque o webhook exige `Curvas.Processor`

### Requirement: Parâmetros comuns
- `{dataBase}` e `data` SHALL estar no formato `AAAA-MM-DD`.
- `du` SHALL ser inteiro maior ou igual a 1 e representa a data de `du` dias úteis após a data-base, no calendário cadastrado da curva.
- `data` SHALL ser dia útil no calendário cadastrado e posterior à data-base.
- `du` e `data` MAY se repetir e se combinar, até 5.000 prazos por requisição; a resposta segue a ordem recebida.
- `formato` SHALL aceitar `json` (padrão), `xlsx` ou, nas rotas de consulta de pontos, interpolação e simulação, `zip` (spec `curve-calculation-memory`).
- `forcarRecalculo` SHALL aceitar `true` ou `false` (padrão).
- Qualquer outro parâmetro de query MUST resultar em 400.

#### Scenario: Cliente tenta escolher o método
- **WHEN** o cliente envia `metodo=Linear` na interpolação
- **THEN** a resposta é 400 com `PARAMETRO_INVALIDO`, informando que a interpolação vem do cadastro

#### Scenario: Data de prazo não útil
- **WHEN** o cliente pede `data=2026-09-19` (sábado) na interpolação da `PRE`
- **THEN** a resposta é 422 com `PRAZO_FORA_DO_DOMINIO`, informando que a data não é dia útil no calendário `Brazil`/`Settlement`

### Requirement: Erros padronizados
Toda resposta de erro SHALL ter o corpo `{ "codigoErro", "mensagem", "correlationId", "detalhes": [] }`, com a mensagem em português, sem stack trace. Os códigos e status SHALL ser:

| `codigoErro` | HTTP | Quando |
|---|---|---|
| `PARAMETRO_INVALIDO` | 400 | formato de data, `du`, `formato` ou parâmetro desconhecido |
| `NAO_AUTENTICADO` | 401 | token ausente ou inválido |
| `SEM_PERMISSAO` | 403 | token sem o papel exigido |
| `CURVA_NAO_ENCONTRADA` | 404 | código ou nome sem cadastro |
| `CURVA_NAO_CONSTRUIDA` | 404 | consulta ou interpolação sem pontos gravados na data |
| `CODIGO_DUPLICADO` | 409 | código atribuído a mais de uma curva |
| `NOME_AMBIGUO` | 409 | nome normalizado igual ao de mais de uma curva; `detalhes` lista códigos e nomes |
| `CONSTRUCAO_EM_ANDAMENTO` | 409 | trava da curva não obtida em 30 segundos |
| `ESTADO_SCRIPT_CONCORRENTE` | 409 | estado do script alterado por outra requisição entre a leitura e a gravação |
| `CADASTRO_INVALIDO` | 422 | item do cadastro ausente, inválido ou incompatível |
| `CARGA_NAO_CONCLUIDA` | 422 | construção sem carga registrada para a origem e a data |
| `INSUMO_INCOMPLETO` | 422 | quantidade de linhas lidas diferente da avisada na carga |
| `INSUMO_AUSENTE` | 422 | origem sem dados na data |
| `INSUMO_INVALIDO` | 422 | dado da origem viola regra do modelo |
| `PRAZO_FORA_DO_DOMINIO` | 422 | prazo fora do domínio, ou `data` não útil ou não posterior à data-base |
| `PONTOS_INVALIDOS` | 422 | lista da edição de pontos inválida |
| `MODELO_FALHOU` | 422 | modelo não convergiu, estourou o tempo limite ou lançou erro |
| `SCRIPT_INVALIDO` | 422 | script Groovy reprovado ou versão não validada |
| `ERRO_INTERNO` | 500 | qualquer outro erro |
| `BLOB_INDISPONIVEL` | 503 | Blob Storage inacessível em operação que só existe para o Blob: gestão de scripts, consulta de histórico, importação de calendário |

#### Scenario: Código desconhecido
- **WHEN** o cliente chama `GET /api/v1/curvas/XYZ/2026-09-14`
- **THEN** a resposta é 404 com `CURVA_NAO_ENCONTRADA` informando `XYZ`

### Requirement: Correlação de requisições
Toda resposta, inclusive de erro e de arquivo `xlsx`, SHALL trazer o cabeçalho `X-Correlation-Id`: o recebido do cliente ou, se ausente, um UUID gerado. O mesmo valor SHALL estar em todos os eventos de log da requisição e no corpo de erro.

#### Scenario: Cliente sem correlação
- **WHEN** o cliente chama a API sem `X-Correlation-Id`
- **THEN** a resposta traz um `X-Correlation-Id` gerado, e os logs da requisição usam o mesmo valor

### Requirement: Resolução por código e por nome
A rota por código SHALL buscar `tCurvaMercd.cTickerIdtfdUnic` igual ao código, com comparação exata. A rota por nome SHALL comparar o nome normalizado (sem acentos, minúsculo, sem espaços nas pontas) com `tCurvaMercd.cTickerIndcd` normalizado. Linhas de `tCurvaMercd` com `cTickerIdtfdUnic` nulo MUST NOT ser encontradas por nenhuma das rotas. Toda resposta por nome SHALL trazer também o código.

#### Scenario: Consulta pelo nome
- **WHEN** a `DCL` tem o nome `Cupom limpo de dólar` e o cliente chama `GET /api/v1/curvas/por-nome/2026-09-14?nome=CUPOM LIMPO DE DOLAR`
- **THEN** a resposta traz os pontos da `DCL` em `2026-09-14` e o código `DCL`

### Requirement: Catálogo de curvas
`GET /api/v1/curvas` SHALL listar as curvas com código não nulo, com código, nome, unidade e `ultimaDataBase` (`tCurvaMercd.dBaseReft`), ordenadas pelo código. O parâmetro opcional `nome` SHALL filtrar por trecho do nome, com a mesma normalização.

#### Scenario: Busca por trecho do nome
- **WHEN** o cliente chama `GET /api/v1/curvas?nome=cupom`
- **THEN** a resposta lista `DCL` e `DPL`, com código, nome e unidade

### Requirement: Construir curva
`POST .../construcao` SHALL executar a construção descrita na spec `curve-build-pipeline`. Com `forcarRecalculo=true`, o corpo MUST trazer `{ "motivo": "..." }` (spec `curve-audit-history`). A resposta de sucesso SHALL ser 200 com: código, nome, data-base, situação (`CONSTRUIDA`, `RECONSTRUIDA` ou `EXISTENTE`), a proveniência completa da spec `curve-build-pipeline` (modelos com origem, versão e hash, versão do engine, `estadoScript` e avisos), quantidade de pontos, `hashPontos` e duração em milissegundos.

#### Scenario: Construção bem-sucedida
- **WHEN** o cliente chama `POST /api/v1/curvas/PRE/2026-09-14/construcao`
- **THEN** a resposta é 200 com situação `CONSTRUIDA`, modelo `PRONTA_TS_B3` de origem `JAVA`, calendário `Brazil`/`Settlement` e 278 pontos

#### Scenario: Sem insumo na data
- **WHEN** o cliente pede a construção de `DPL` numa data sem insumo
- **THEN** a resposta é 422 com `INSUMO_AUSENTE` informando `DPL` e a data

### Requirement: Consultar pontos gravados
`GET /curvas/{codigo}/{dataBase}` SHALL devolver código, nome, data-base, unidade, interpolador e calendário com origem e versão, `estadoScript`, `hashPontos` e a lista de pontos gravados em ordem de data. Cada ponto SHALL trazer data, `DU`, `DC`, valor e, só para `TAXA`, fator acumulado e fator diário médio. Sem pontos gravados na data, a resposta MUST ser 404 com `CURVA_NAO_CONSTRUIDA`.

#### Scenario: Curva ainda não construída
- **WHEN** o cliente consulta `GET /api/v1/curvas/DCL/2026-09-14` antes de qualquer construção dessa data
- **THEN** a resposta é 404 com `CURVA_NAO_CONSTRUIDA`

### Requirement: Editar os pontos da curva
`PUT .../pontos` SHALL receber `{ "motivo": "...", "pontos": [ { "data": "AAAA-MM-DD", "valor": número } ] }` com o motivo obrigatório (spec `curve-audit-history`) e a lista completa da data, e substituir numa única transação (a mesma trava da construção) todos os pontos gravados pela lista. A operação SHALL valer também para data sem pontos. A lista MUST ser rejeitada com 422 `PONTOS_INVALIDOS`, sem alterar nada, quando:
- estiver vazia;
- algum ponto não tiver data ou valor;
- houver datas repetidas;
- alguma data for igual ou anterior à data-base;
- alguma data não for dia útil no calendário cadastrado;
- algum valor não puder ser convertido na grandeza cadastrada (ex.: `FA <= 0`, ou `y <= 0` com `LogLinear`).

`detalhes` SHALL listar cada ponto inválido com o motivo. A resposta de sucesso SHALL ser 200 com o mesmo corpo da consulta de pontos gravados, relido do banco. A operação SHALL gerar a auditoria `EDICAO` (spec `curve-audit-history`) e o evento de log `PONTOS_EDITADOS` com o usuário autenticado. Uma construção posterior com recálculo substitui os pontos editados.

#### Scenario: Edição bem-sucedida
- **WHEN** o cliente envia para `PUT /api/v1/curvas/PRE/2026-09-14/pontos` os 278 pontos com o valor de `2027-01-04` alterado
- **THEN** a resposta é 200 com os 278 pontos gravados, e a interpolação seguinte usa o valor novo

#### Scenario: Data não útil
- **WHEN** a lista enviada contém um ponto em `2026-09-19` (sábado)
- **THEN** a resposta é 422 com `PONTOS_INVALIDOS` citando `2026-09-19`, e os pontos gravados continuam os anteriores

#### Scenario: Sem autenticação
- **WHEN** um cliente sem token chama o endpoint de edição
- **THEN** a resposta é 401 com `NAO_AUTENTICADO` e nada é alterado

### Requirement: Interpolar prazos
`GET .../interpolacao` SHALL exigir ao menos um `du` ou `data` e devolver código, nome, data-base, interpolador, políticas de extrapolação e calendário com origem, `hashPontos` e, para cada prazo, na ordem recebida: prazo pedido, data, `DU`, `DC`, valor, classificação (`PONTO`, `INTERPOLADO`, `EXTRAPOLADO_INICIO`, `EXTRAPOLADO_FIM`) e, só para `TAXA`, os fatores. Se algum prazo estiver fora do domínio, a resposta inteira MUST ser 422 com `PRAZO_FORA_DO_DOMINIO`, listando em `detalhes` todos os prazos rejeitados.

#### Scenario: Interpolação por dias úteis
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14/interpolacao?du=21&du=252`
- **THEN** a resposta traz os valores de 21 e 252 dias úteis, calculados com `Discount` + `LogLinear` em `Business252`, cada um com sua classificação

### Requirement: Saída em planilha nas rotas de leitura
Com `formato=xlsx`, as rotas de consulta de pontos, de interpolação e de simulação SHALL responder com a planilha de memória de cálculo definida na spec `curve-calculation-memory`, em vez do JSON, com `Content-Type` `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` e `Content-Disposition: attachment; filename="{codigo}_{dataBase}_{FONTE}_{AAAAMMDDHHmmss}.xlsx"`, onde `FONTE` é `GRAVADA` ou `SIMULACAO` e o carimbo está no horário de Brasília. Erros continuam respondendo em JSON.

#### Scenario: Download da consulta
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14?formato=xlsx`
- **THEN** a resposta é um arquivo `PRE_2026-09-14_GRAVADA_<horário>.xlsx` com a memória de cálculo dos pontos gravados

### Requirement: Gestão de scripts de modelo
`{tipo}` SHALL ser `construcao`, `interpolacao` ou `calendario`. O envio SHALL receber o código do script como texto no corpo (`text/plain`) e criar uma versão em `RASCUNHO`. As respostas SHALL trazer tipo, nome, versão, status e hash. As mensagens de falha de validação SHALL trazer o motivo e a linha do script quando houver, sem stack trace.

#### Scenario: Ativação de versão não validada
- **WHEN** o cliente pede a ativação de uma versão que não passou na validação
- **THEN** a resposta é 422 com `SCRIPT_INVALIDO`, informando que a versão precisa ser validada antes
