## Purpose

Define a API HTTP do engine para construir, consultar, interpolar e simular curvas, identificando a curva pelo código (ou, só para leitura, pelo nome) e pela data-base, e para gerir os scripts Groovy de modelo por tipo e nome. Fixa rotas, parâmetros, corpos, códigos HTTP e códigos de erro.

## ADDED Requirements

### Requirement: Rotas
O engine SHALL expor exatamente estas rotas (prefixo `/api/v1`):

| Método e rota | Uso | Papel exigido |
|---|---|---|
| `POST /cargas` | aviso de carga concluída (spec `curve-load-trigger`) | `Curvas.Processor` |
| `GET /curvas?nome=` | catálogo | `Curvas.Leitura` |
| `GET /curvas/situacao?dataBase=` | conferência de todas as curvas numa data-base contra a fonte atual, para o painel do `services/curves` | `Curvas.Leitura` |
| `GET /valores-cadastro` | valores aceitos no cadastro, modelos ativos e regras de combinação | `Curvas.Leitura` |
| `POST /curvas/{codigo}/{dataBase}/construcao?forcarRecalculo=` | construir | `Curvas.Operador` |
| `GET /curvas/{codigo}/{dataBase}?formato=` | pontos gravados | `Curvas.Leitura` |
| `GET /curvas/{codigo}/{dataBase}/interpolacao?du=&data=&formato=` | interpolar | `Curvas.Leitura` |
| `GET /curvas/{codigo}/{dataBase}/simulacao?du=&data=&formato=` | simular construção sem gravar | `Curvas.Leitura` |
| `GET /curvas/{codigo}/{dataBase}/auditoria?formato=` | arquivo de auditoria montado na hora (spec `curve-audit-history`) | `Curvas.Leitura` |
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

As rotas antigas (`POST /api/v1/curvas/construir`, `POST /api/v1/calculo`, `POST /api/v1/modelos/upload`) MUST ser removidas. Construir MUST existir só pelo código. O engine não edita pontos: a edição manual é do `services/curves` (change `curves-cadastro-curvas`).

#### Scenario: Escrita pelo nome não existe
- **WHEN** o cliente chama `POST /api/v1/curvas/por-nome/2026-09-14/construcao?nome=DIxPRE`
- **THEN** a resposta é 404

### Requirement: Autenticação e papéis
Toda rota de `/api/v1` MUST exigir um token JWT do Microsoft Entra ID (`Authorization: Bearer`), validado por emissor, audiência, assinatura e validade (propriedades `engine.seguranca.emissor` e `engine.seguranca.audiencia`). Só os endpoints de saúde do Actuator ficam sem autenticação. O acesso SHALL ser decidido pelos papéis de aplicação (`roles`) do token, conforme a tabela de rotas:
- `Curvas.Leitura`: consultas, interpolação, simulação, auditoria, situação, valores aceitos, catálogo e lista de scripts;
- `Curvas.Operador`: construir e recalcular; inclui `Curvas.Leitura`;
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
Toda resposta de erro SHALL ter o corpo `{ "codigoErro", "mensagem", "correlationId", "detalhes": [ { "campo", "linha", "valor", "motivo" } ] }`, o mesmo formato do `services/curves`, com a mensagem em português, sem stack trace; em cada item de `detalhes`, o que não se aplica vem nulo. Os códigos e status SHALL ser:

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
| `CURVA_MAE_NAO_CONSTRUIDA` | 422 | curva derivada com alguma mãe sem pontos gravados na data; `detalhes` lista as mães |
| `INSUMO_INCOMPLETO` | 422 | quantidade de linhas lidas diferente da avisada na carga |
| `INSUMO_AUSENTE` | 422 | origem sem dados na data |
| `INSUMO_INVALIDO` | 422 | dado da origem viola regra do modelo |
| `PONTOS_NAO_INTERPOLAVEIS` | 422 | pontos gravados que o interpolador não aceita (ex.: `y` não positivo no `LogLinear`); `detalhes` lista os pontos |
| `PRAZO_FORA_DO_DOMINIO` | 422 | prazo fora do domínio, ou `data` não útil ou não posterior à data-base |
| `MODELO_FALHOU` | 422 | modelo não convergiu, estourou o tempo limite ou lançou erro |
| `SCRIPT_INVALIDO` | 422 | script Groovy reprovado ou versão não validada |
| `ERRO_INTERNO` | 500 | qualquer outro erro |
| `BLOB_INDISPONIVEL` | 503 | Blob Storage inacessível em operação que só existe para os scripts Groovy: gestão de scripts e importação de calendário |

#### Scenario: Código desconhecido
- **WHEN** o cliente chama `GET /api/v1/curvas/XYZ/2026-09-14`
- **THEN** a resposta é 404 com `CURVA_NAO_ENCONTRADA` informando `XYZ`

### Requirement: Contrato de tipos das respostas
Para o front receber os valores sem perda nem ambiguidade, toda resposta JSON do engine SHALL seguir:
- **decimais** (valor da curva, fatores, taxas, `X`, `Y`, `W`, diferenças) como **string**, em notação simples, com ponto decimal e sem expoente, na escala em que foram calculados ou arredondados (ex.: `"13.9000000"`, `"1.1390000000000000"`). Números JSON perdem precisão em JavaScript acima de 15 dígitos significativos. Inteiros (`DU`, `DC`, quantidades, versões) como número;
- **datas** como `AAAA-MM-DD`; **instantes** em ISO-8601 com o deslocamento de Brasília (ex.: `2026-09-14T21:30:00.000-03:00`);
- **enums** como string, exatamente com os valores abaixo, com diferença entre maiúsculas e minúsculas;
- **avisos** em `avisos`, lista de `{ "codigo", "mensagem", "detalhes" }`, com `detalhes` no formato dos erros; lista vazia quando não há aviso, nunca omitida;
- **idioma pt-BR:** o front é em português do Brasil. A API troca valores em formato de máquina (ponto decimal, datas `AAAA-MM-DD`, códigos de enum), e o front formata para pt-BR na tela (vírgula decimal, ponto de milhar, `dd/mm/aaaa`, horário de Brasília). Todo texto para o usuário (mensagem de erro, mensagem de aviso, rótulo e descrição de catálogo) SHALL estar em pt-BR, com acentuação, em UTF-8. Os códigos (enums, `codigoErro`, códigos de aviso) não são traduzidos: o front mostra o rótulo do catálogo.

Enums das respostas:

| Campo | Valores |
|---|---|
| situação da construção | `CONSTRUIDA`, `RECONSTRUIDA`, `EXISTENTE`, `IGNORADA` (só na carga) |
| motivo de `IGNORADA` | `CURVA_INATIVA`, `FORA_DA_VIGENCIA_CURVA` |
| classificação do prazo | `PONTO`, `INTERPOLADO`, `EXTRAPOLADO_INICIO`, `EXTRAPOLADO_FIM`, `FORA_DO_DOMINIO` (só na simulação) |
| situação do ponto na comparação (simulação e auditoria) | `IGUAL`, `DIFERENTE`, `SO_SIMULADO`, `SO_GRAVADO`, `DESCARTADO_MESMO_PRAZO`, `DESCARTADO_PRAZO_NAO_POSITIVO` |
| `status` da simulação e da conferência | `OK`, `ERRO` |
| `estadoScript` | `ATUAL`, `DESATUALIZADO`, `DESCONHECIDO` |
| origem do modelo | `JAVA`, `GROOVY` |
| tipo de modelo | `construcao`, `interpolacao`, `calendario` (os mesmos da rota `/modelos/{tipo}`) |
| status do script | `RASCUNHO`, `VALIDADA`, `REPROVADA`, `ATIVA`, `INATIVA` |
| operação na auditoria | `CONSTRUCAO`, `RECONSTRUCAO` |
| `acionadoPor` | `CARGA`, `API` |
| fonte da planilha | `GRAVADA`, `SIMULACAO` |

Avisos do engine:

| `codigo` | Onde | Significado |
|---|---|---|
| `CURVA_INATIVA` | construção pela API, simulação | curva inativa construída por pedido do usuário |
| `FORA_DA_VIGENCIA_CURVA` | construção pela API, simulação | data-base fora da vigência da curva |
| `PONTOS_DIFERENTES_DA_FONTE` | carga, construção pela API | pontos gravados diferentes do que a fonte atual produz; `detalhes` traz a quantidade |
| `PONTO_DESCARTADO_MESMO_PRAZO` | consulta, interpolação, construção, simulação | ponto no mesmo prazo de outro, fora da interpolação |
| `PONTO_DESCARTADO_PRAZO_NAO_POSITIVO` | consulta, interpolação, construção, simulação | ponto na data-base ou antes, fora da interpolação |
| `CALCULO_GRAVADO_DIVERGENTE` | consulta, auditoria | dias ou fatores gravados em `tDadoVertcCurva` diferentes dos recalculados agora |
| `SEM_CALCULO_GRAVADO` | consulta, auditoria | pontos sem detalhe gravado em `tDadoVertcCurva` (editados à mão, ainda sem recálculo) |
| `ESTADO_SCRIPT_DESATUALIZADO` | toda resposta com proveniência | Blob fora: modelos pelo último estado conhecido (`estadoScript` = `DESATUALIZADO`) |
| `ESTADO_SCRIPT_DESCONHECIDO` | toda resposta com proveniência | Blob fora sem estado conhecido: modelos nativos (`estadoScript` = `DESCONHECIDO`) |

Um código de aviso ou de erro novo SHALL entrar nestas tabelas e em `GET /valores-cadastro` antes de ser usado.

#### Scenario: Valor com 16 casas
- **WHEN** o front consulta a `PRE` e um fator acumulado tem 16 casas decimais
- **THEN** o fator chega como string com as 16 casas, sem arredondamento do JavaScript

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
`POST .../construcao` SHALL executar a construção descrita na spec `curve-build-pipeline`. `forcarRecalculo=true` é acionado pelo usuário no front e não exige motivo. A resposta de sucesso SHALL ser 200 com: código, nome, data-base, situação (`CONSTRUIDA`, `RECONSTRUIDA` ou `EXISTENTE`), a proveniência completa da spec `curve-build-pipeline` (modelos com origem, versão e hash, versão do engine, `estadoScript` e avisos), quantidade de pontos, `hashPontos` e duração em milissegundos. Na situação `EXISTENTE`, o engine SHALL comparar os pontos gravados com os que a fonte atual produz, como na carga (spec `curve-load-trigger`), e trazer o aviso `PONTOS_DIFERENTES_DA_FONTE` quando diferirem. A curva `INATIVO`, ou a data-base fora da vigência da curva, não impede a construção por esta rota: a resposta traz o aviso `CURVA_INATIVA` ou `FORA_DA_VIGENCIA_CURVA`.

#### Scenario: Construção bem-sucedida
- **WHEN** o cliente chama `POST /api/v1/curvas/PRE/2026-09-14/construcao`
- **THEN** a resposta é 200 com situação `CONSTRUIDA`, modelo `PRONTA_TS_B3` de origem `JAVA`, calendário `Brazil`/`Settlement` e 278 pontos

#### Scenario: Sem insumo na data
- **WHEN** o cliente pede a construção de `DPL` numa data sem insumo
- **THEN** a resposta é 422 com `INSUMO_AUSENTE` informando `DPL` e a data

### Requirement: Consultar pontos gravados
`GET /curvas/{codigo}/{dataBase}` SHALL devolver código, nome, data-base, unidade, interpolador e calendário com origem e versão, `estadoScript`, `hashPontos` e a lista de pontos gravados em ordem de data. Cada ponto SHALL trazer data, `DU`, `DC`, dias 30/360, valor e, só para `TAXA`, fator acumulado e fator diário médio, todos recalculados agora. Sem pontos gravados na data, a resposta MUST ser 404 com `CURVA_NAO_CONSTRUIDA`. Os avisos de descarte `PONTO_DESCARTADO_MESMO_PRAZO` e `PONTO_DESCARTADO_PRAZO_NAO_POSITIVO` da spec `curve-build-pipeline` SHALL vir em `avisos`, aqui e na interpolação. Cada ponto SHALL trazer também o **detalhe gravado** em `tDadoVertcCurva` (dias úteis, dias corridos, dias 30/360 e fatores), ao lado dos valores recalculados agora, para o usuário conferir na tela o que foi calculado e entregue. Se algum valor gravado diferir do recalculado (por exemplo, porque o calendário mudou depois da construção), a resposta SHALL trazer o aviso `CALCULO_GRAVADO_DIVERGENTE` com os pontos; se não houver detalhe gravado (pontos editados à mão), o aviso `SEM_CALCULO_GRAVADO`.

#### Scenario: Curva ainda não construída
- **WHEN** o cliente consulta `GET /api/v1/curvas/DCL/2026-09-14` antes de qualquer construção dessa data
- **THEN** a resposta é 404 com `CURVA_NAO_CONSTRUIDA`


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

### Requirement: Situação das curvas numa data-base
`GET /curvas/situacao?dataBase=` SHALL devolver, para cada curva com código não nulo, o que o engine calcula na hora e o `services/curves` não consegue calcular, sem ler nem gravar nenhum registro próprio:
- código, nome e origem;
- `insumo`: para curva com origem de provedor, a quantidade de linhas brutas da origem na data (`linhasBrutas`); para curva derivada, cada mãe com nome, papel e se tem pontos gravados na data;
- `pontosGravados`: quantidade e `hashPontos` em `tDadoCurva`;
- `conferencia`: quando há insumo (linhas brutas, ou todas as mães com pontos), o resultado de executar o modelo como a simulação, sem gravar: `status` (`OK` ou `ERRO`), `codigoErro` e mensagem, `hashPontosFonte` e, se houver pontos gravados, `pontosDiferentes` (quantidade de pontos que diferem, que só existem de um lado ou do outro); nula sem insumo.

A rota MUST NOT construir nem gravar nada. As curvas SHALL ser conferidas em paralelo, com até `engine.situacao.paralelismo` (padrão 8) ao mesmo tempo, e a falha ou o tempo esgotado de uma MUST NOT impedir as outras: a curva sai com `conferencia.status` = `ERRO` e o código correspondente. Quem monta o painel, com o cadastro e as regras de situação, é o `services/curves` (spec `painel-curvas` do change `curves-cadastro-curvas`).

#### Scenario: Situação depois da carga B3
- **WHEN** a carga B3 de `2026-09-14` construiu `PRE`, `DCL`, `INP` e `PTX`, a `DPL` falhou por `INSUMO_INVALIDO`, e a ANBIMA e a Bloomberg ainda não carregaram
- **THEN** as quatro vêm com linhas brutas, pontos gravados e `conferencia` `OK` com 0 pontos diferentes; a `DPL`, com linhas brutas, sem pontos e `conferencia` `ERRO` com `INSUMO_INVALIDO`; `NTNB` e `SOFR`, com 0 linhas brutas e `conferencia` nula

#### Scenario: Ponto editado à mão
- **WHEN** um ponto da `PRE` de `2026-09-14` foi alterado no `services/curves`
- **THEN** a `PRE` vem com `conferencia` `OK` e `pontosDiferentes` = 1

### Requirement: Valores aceitos no cadastro
`GET /valores-cadastro` SHALL devolver tudo o que o engine aceita no cadastro de uma curva, gerado dos mesmos enums e da mesma tabela de parâmetros que o validador de `CADASTRO_INVALIDO` usa, e nunca de uma lista mantida à parte:
- os campos de `tCurvaMercd` lidos pelo engine (`cTpoVlr`, `cNormaDia`, `cTpoJuro`), com os valores aceitos e em que unidade são obrigatórios;
- cada chave de `cModDado`: tipo, obrigatoriedade (com a condição, ex.: `FREQUENCY` só com `Compounded`), valor padrão, e os valores aceitos ou o formato (ex.: `HORIZONTE` pela expressão do `Period`, `CASAS_DECIMAIS` de 0 a 12);
- para cada valor, um `rotulo` curto em pt-BR, para listas e telas, e uma `descricao` em pt-BR (ex.: `DOWN`: rótulo "Truncar", descrição "Corta as casas excedentes, sem arredondar"; `FlatForward`: rótulo "Taxa a termo constante");
- as regras de combinação, cada uma com um código e o texto;
- os modelos por tipo (construção, interpolação, calendário): nome, origem (`JAVA` ou `GROOVY`), versão `ATIVA` do script quando houver; para calendário, os mercados aceitos; para os modelos de construção, a fonte e o produto de origem esperados e, nos modelos derivados (fonte `TCEN`), os papéis das mães;
- os enums das respostas e os catálogos de avisos e de erros do engine, com `rotulo` e `descricao` em pt-BR de cada valor, para o front exibir rótulos sem manter lista própria;
- `versaoValores`: SHA-256 do conteúdo, para o cliente saber quando atualizar o cache.

O OpenAPI (Swagger) do engine SHALL declarar como `enum` todo campo de valor fechado nos corpos e respostas. Um teste SHALL garantir que todo valor aceito pelo validador aparece nesta rota, e vice-versa.

#### Scenario: Interpolador Groovy ativado
- **WHEN** um script de interpolação `LogCubicB3` é ativado
- **THEN** a próxima chamada de `GET /api/v1/valores-cadastro` lista `LogCubicB3` com origem `GROOVY` e a versão ativa, e `versaoValores` muda

### Requirement: Gestão de scripts de modelo
`{tipo}` SHALL ser `construcao`, `interpolacao` ou `calendario`. O envio SHALL receber o código do script como texto no corpo (`text/plain`) e criar uma versão em `RASCUNHO`. As respostas SHALL trazer tipo, nome, versão, status e hash. As mensagens de falha de validação SHALL trazer o motivo e a linha do script quando houver, sem stack trace.

#### Scenario: Ativação de versão não validada
- **WHEN** o cliente pede a ativação de uma versão que não passou na validação
- **THEN** a resposta é 422 com `SCRIPT_INVALIDO`, informando que a versão precisa ser validada antes
