## Purpose

No `services/curves`, criar, consultar, alterar, inativar e reativar as curvas de mercado (`tCurvaMercd`), que são a base do cadastro lido pelo engine. Define também as regras comuns a todo o CRUD de cadastro do serviço: identificação, autenticação, erros, última gravação vence, auditoria (log e arquivo montado na hora) e horário.

## ADDED Requirements

### Requirement: Campos da curva de mercado
O serviço SHALL gravar em `tCurvaMercd`:

| Campo da API | Coluna | Regra |
|---|---|---|
| `codigo` | `cTickerIdtfdUnic` | obrigatório; 1 a 50 caracteres de `A-Z` (só maiúsculas), `0-9` e `_`; único entre todas as curvas. As rotas fixas em minúsculas (`painel`, `valores`, `exportacao`, `importacao`, `vertices`) nunca colidem com um código |
| `nome` | `cTickerIndcd` | obrigatório; 1 a 50 caracteres; único entre todas as curvas, inclusive as sem código (`cTickerIdtfdUnic` nulo), depois de normalizado (sem acentos, minúsculo, sem espaços nas pontas); a colisão responde 409 `NOME_EM_USO` antes de qualquer gravação; **imutável** depois de criado, porque é a chave de todas as FKs |
| `unidade` | `cTpoVlr` | obrigatório: `TAXA`, `PRECO` ou `PONTOS` |
| `dayCounterCotacao` | `cNormaDia` | obrigatório se `unidade` = `TAXA`, e nulo caso contrário: `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` |
| `compounding` | `cTpoJuro` | obrigatório se `unidade` = `TAXA`, e nulo caso contrário: `Simple`, `Compounded`, `Continuous` |
| `moeda` | `cMoedaNegoc` | obrigatório; código ISO 4217 (ex.: `BRL`, `USD`) |
| `pais` | `cPaisInstt` | obrigatório; código ISO 3166-1 alfa-2 (ex.: `BR`, `US`) |
| `classificacao` | `cClasfInstt` | opcional; até 50 caracteres |
| `classeAtivo` | `cClassAtivo` | opcional; até 50 caracteres |
| `dono` | `cPprioDado` | opcional; até 50 caracteres; a área ou pessoa responsável pela curva (texto livre) |
| `situacao` | `cSitReg` | `ATIVO` ou `INATIVO`; `ATIVO` na criação |
| `inicioVigencia` | `dInicVgcia` | obrigatório; data |
| `fimVigencia` | `dValidAte` | opcional; data maior ou igual a `inicioVigencia` |

Campo obrigatório ausente (inclusive `inicioVigencia`) MUST ser recusado com 422 `DADOS_INVALIDOS`, com um item em `detalhes` por campo, antes de qualquer comparação de datas (como `fimVigencia` maior ou igual a `inicioVigencia`).

O serviço SHALL preencher `cUsuarAtulz` (o cabeçalho opcional `X-Usuario`; nulo quando não vier, porque não há autenticação na v0 e na v1), `dCriacReg` (na criação) e `dUltAtulz` (a cada alteração). O serviço MUST NOT gravar `dBaseReft` nem `cUsuarCalc`, que são do engine, nem as colunas sem uso definido (`cConfgIdtfd`, `cCurvaReft`, `cFamlInsttFincr`, `cIndxdAtivo`, `cTpoCotac`, `iPrvdrDados`, `rAtivoIndcd`, `vFatorMultiAtivo`), que ficam nulas nas curvas criadas pelo serviço e intocadas nas demais.

#### Scenario: Criação da DIxPRE
- **WHEN** o cliente cria a curva com `codigo` = `PRE`, `nome` = `DIxPRE`, `unidade` = `TAXA`, `dayCounterCotacao` = `Business252`, `compounding` = `Compounded`, `moeda` = `BRL`, `pais` = `BR` e `inicioVigencia` = `2026-01-01`
- **THEN** a resposta é 201 com a curva, `situacao` = `ATIVO`, e `tCurvaMercd` tem a linha `DIxPRE` com o código `PRE`

#### Scenario: Nome que colide depois de normalizado
- **WHEN** já existe a curva `Cupom limpo de dólar` e o cliente cria outra com o nome `CUPOM LIMPO DE DOLAR`
- **THEN** a resposta é 409 com `NOME_EM_USO`, e nada é gravado

#### Scenario: Nome em uso por curva sem código
- **WHEN** existe uma linha de `tCurvaMercd` sem código com o nome `DIxPRE`, e o cliente cria uma curva com o nome `dixpre`
- **THEN** a resposta é 409 com `NOME_EM_USO`, e nada é gravado

#### Scenario: Início de vigência ausente
- **WHEN** o cliente cria uma curva sem `inicioVigencia` e com `fimVigencia`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS` no campo `inicioVigencia`, sem comparar as datas, e nada é gravado

#### Scenario: Unidade de preço com cotação
- **WHEN** o cliente cria uma curva `PRECO` informando `compounding`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, apontando o campo `compounding`

### Requirement: Rotas da curva de mercado
O serviço SHALL expor (prefixo `/api/v1`), identificando a curva pelo código:

| Rota | Uso |
|---|---|
| `GET /curvas-mercado?nome=&codigo=&unidade=&situacao=&provedor=&dono=&pagina=&tamanho=` | listar, com filtros por trecho de nome (normalizado), código exato, unidade, situação, provedor (`B3`, `ANBIMA`, `BLOOMBERG` ou outro de `tPrvdrDadoMercd`: curvas com esse provedor em `tCurvaPrvdr`) e trecho do dono; paginado (tamanho padrão 50, máximo 500), ordenado por código; corpo no requisito "Resposta da listagem" |
| `GET /curvas-mercado/{codigo}` | consultar a curva, com os provedores da curva e a configuração vigente hoje |
| `POST /curvas-mercado` | criar |
| `PUT /curvas-mercado/{codigo}` | alterar os campos, menos `nome` e `situacao` |
| `POST /curvas-mercado/{codigo}/inativacao` | inativar |
| `POST /curvas-mercado/{codigo}/reativacao` | reativar |

Não há exclusão física: a curva pode ter vértices, dados brutos e configurações que dependem dela. Inativar a curva, ou deixar a data-base fora de `inicioVigencia`..`fimVigencia`, faz a carga deixar de construí-la automaticamente (spec `curve-load-trigger` do change `engine-construcao-curvas`); o usuário ainda pode construí-la pelo engine, com aviso, e os vértices já gravados continuam consultáveis. Linhas de `tCurvaMercd` sem código (`cTickerIdtfdUnic` nulo) MUST NOT aparecer nas rotas. Alterar `codigo` SHALL ser permitido, desde que o novo seja único; o nome não muda.

#### Scenario: Tentativa de renomear
- **WHEN** o cliente envia no `PUT` um `nome` diferente do atual
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, informando que o nome é imutável

#### Scenario: Inativação
- **WHEN** a curva `SLP` é inativada
- **THEN** `cSitReg` passa a `INATIVO`, a curva continua consultável, os provedores da curva e as configurações são mantidos, e a carga deixa de construí-la automaticamente

### Requirement: Última gravação vence
Nenhuma rota do serviço SHALL fazer controle de versão (cabeçalho condicional ou comparação de estado): o cadastro é mantido por poucas pessoas, e quem salva por último vence. O estado anterior de toda alteração fica no evento de log `CADASTRO_ALTERADO` (requisito "Auditoria do cadastro"), de onde pode ser recuperado. A trava da curva em `tCurvaMercd` continua valendo só para serializar gravações concorrentes (como `idCurvaProvedor` por `MAX + 1`), sem recusar ninguém.

#### Scenario: Duas pessoas editando a mesma curva
- **WHEN** duas pessoas leem a curva `PRE` e as duas enviam alterações
- **THEN** as duas são gravadas, uma depois da outra, a última vence, e o log tem um `CADASTRO_ALTERADO` de cada uma, com o estado anterior

### Requirement: Resposta da listagem
`GET /curvas-mercado` SHALL responder 200 com `{ "itens": [...], "pagina", "tamanho", "total" }`, `total` sendo a quantidade de curvas que atendem aos filtros. Cada item SHALL trazer:

| Campo | Origem |
|---|---|
| `codigo`, `nome`, `unidade`, `situacao`, `moeda`, `inicioVigencia`, `fimVigencia`, `dono` | `tCurvaMercd` |
| `provedores` | os provedores da curva em `tCurvaPrvdr` (`iPrvdrDados`), sem repetição, na ordem de prioridade; o primeiro é o provedor principal; lista vazia quando não há |
| `ultimaExecucao` | `{ "dataBase", "usuario" }` da última construção gravada pelo engine (`dBaseReft` e `cUsuarCalc` de `tCurvaMercd`); nulo quando a curva nunca foi construída |

#### Scenario: Curva da B3 já construída
- **WHEN** o front lista com `provedor` = `B3`, e a `PRE` tem provedores `B3` (prioridade 1) e `BLOOMBERG` (prioridade 2), `cPprioDado` = `Tesouraria` e `dBaseReft` = `2026-09-14`
- **THEN** o item da `PRE` traz `provedores` = `["B3", "BLOOMBERG"]`, `dono` = `Tesouraria` e `ultimaExecucao` = `{ "dataBase": "2026-09-14", "usuario": null }`

#### Scenario: Curva nunca construída
- **WHEN** a curva `DCL` não tem `dBaseReft`
- **THEN** o item traz `ultimaExecucao` = `null`

### Requirement: Sem autenticação e erros
Na v0 e na v1, nenhuma rota SHALL exigir token nem papel (sem JWT, sem 401 e sem 403); a autenticação entra numa change própria, depois. Toda resposta de erro SHALL seguir o formato de erro único do serviço (o mesmo do CRUD de provedores), trazendo o código abaixo, a mensagem em português, a rota, o `correlationId` e, quando houver, `detalhes` (`campo`, `linha`, `valor`, `motivo`, com nulo no que não se aplica); sem stack trace. Os códigos:

| `codigoErro` | HTTP | Quando |
|---|---|---|
| `PARAMETRO_INVALIDO` | 400 | parâmetro ou JSON malformado |
| `NAO_ENCONTRADO` | 404 | curva, provedor da curva, versão ou provedor inexistente |
| `CODIGO_EM_USO`, `NOME_EM_USO`, `PROVEDOR_DUPLICADO`, `PRIORIDADE_EM_USO` | 409 | unicidade violada |
| `DADOS_INVALIDOS` | 422 | regra de campo violada; `detalhes` lista cada campo |
| `VERTICES_INVALIDOS` | 422 | lista de vértices que não pode ser gravada de forma consistente (spec `vertices-curva-manual`) |
| `ERRO_INTERNO` | 500 | qualquer outro erro, inclusive a espera de 60 segundos pela trava de uma consulta esgotada |

Toda resposta SHALL trazer `X-Correlation-Id` (o recebido ou um UUID gerado).

#### Scenario: Escrita sem token
- **WHEN** o front cria uma curva sem cabeçalho `Authorization` nem `X-Usuario`
- **THEN** a curva é criada, com `cUsuarAtulz` nulo

### Requirement: Auditoria do cadastro
Nada do cadastro SHALL ser gravado no Blob Storage, que guarda só os arquivos originais dos feeders e os scripts Groovy. Toda alteração do cadastro (curva, provedor da curva ou configuração, pela API ou pela planilha) SHALL emitir, depois do commit, o evento de log `CADASTRO_ALTERADO` com nível `AVISO`: `idAuditoria`, código, nome, tipo (`CURVA`, `PROVEDOR` ou `CONFIGURACAO`), operação (`CRIACAO`, `ALTERACAO`, `INATIVACAO`, `REATIVACAO`, `EXCLUSAO`), usuário, instante (horário de Brasília), `correlationId`, `idLote` (quando vier da planilha), estado anterior e estado novo completos. O evento traz sempre o nome, que é imutável, para o histórico sobreviver a uma troca de código. O destino dos logs SHALL ter retenção definida pela área de risco.

`GET /api/v1/curvas-mercado/{codigo}/auditoria?formato=xlsx|json`, pedido pelo front, SHALL montar na hora, sem guardar nada, o arquivo de auditoria do cadastro da curva: a curva com todos os campos, inclusive `cUsuarAtulz`, `dCriacReg`, `dUltAtulz`, `dBaseReft` e `cUsuarCalc`; todos os provedores da curva; todas as versões de configuração, com vigência e parâmetros. O nome do arquivo SHALL ser `{codigo}_CADASTRO_AUDITORIA_{AAAAMMDDHHmmss}.xlsx`, no horário de Brasília. Quem alterou o quê antes está nos eventos `CADASTRO_ALTERADO` do log.

#### Scenario: Quem mudou a unidade
- **WHEN** a unidade de uma curva é alterada
- **THEN** o log tem um `CADASTRO_ALTERADO` com o usuário, a unidade anterior e a nova, e nada é gravado no Blob

#### Scenario: Arquivo de auditoria pedido pelo front
- **WHEN** o gestor pede a auditoria do cadastro da `PRE`
- **THEN** o arquivo é montado na hora, com a curva, os provedores da curva e todas as versões de configuração, e `cUsuarAtulz` e `dUltAtulz` mostram quem fez a última alteração e quando

### Requirement: Contrato de tipos para o front
Toda resposta JSON do serviço SHALL seguir o mesmo contrato de tipos do engine (spec `curve-engine-api` do change `engine-construcao-curvas`): decimais como string em notação simples, datas `AAAA-MM-DD`, instantes em ISO-8601 com o deslocamento de Brasília, enums como string exatamente como nas specs, com diferença entre maiúsculas e minúsculas, e `avisos` como lista de `{ "codigo", "mensagem", "detalhes" }`, vazia quando não há aviso. O front é pt-BR: a API troca valores em formato de máquina e o front formata para pt-BR na tela (vírgula decimal, `dd/mm/aaaa`, horário de Brasília); mensagens de erro e de aviso, rótulos e descrições SHALL estar em pt-BR, com acentuação, em UTF-8; os códigos não são traduzidos. Na entrada, enum com caixa diferente (`taxa`, `business252`) MUST ser recusado com 422 `DADOS_INVALIDOS`, e decimal SHALL ser aceito como string.

As colunas `CHAR` de `tCurvaMercd` (`cNormaDia`, `cTpoJuro`, `cSitReg`, `cTpoVlr`, `cPaisInstt`) são completadas com espaços pelo banco. O serviço SHALL gravar os valores sem espaços e SHALL aparar os espaços à direita de toda coluna de texto lida, antes de devolver ou comparar.

Enums do serviço:

| Campo | Valores |
|---|---|
| `unidade` | `TAXA`, `PRECO`, `PONTOS` |
| `dayCounterCotacao` | `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` |
| `compounding` | `Simple`, `Compounded`, `Continuous` |
| `situacao` da curva | `ATIVO`, `INATIVO` |
| `provedor` do provedor da curva | os de `tPrvdrDadoMercd`; o engine e o processor reconhecem `B3`, `ANBIMA`, `BLOOMBERG` e o interno `TCEN` |
| `produto` do provedor da curva | `TS` (B3, arquivo Taxas de Mercado para Swaps), `MS` (ANBIMA, arquivo de Mercado Secundário de títulos públicos, `ms{AAMMDD}.txt`), `BLC2` (Bloomberg, fonte de preço do curve member no ticker); para `TCEN`, o papel da curva componente declarado pelo modelo derivado (ex.: `NUMERADOR`, `DENOMINADOR`) |
| situação no painel | `NAO_E_DIA_UTIL`, `IGNORADA`, `SITUACAO_INDISPONIVEL`, `INTERPOLADA_DESATUALIZADA`, `CONSTRUIDA`, `DIVERGENTE_DA_FONTE`, `AGUARDANDO_COMPONENTES`, `AGUARDANDO_CARGA`, `COM_ERRO`, `NAO_CONSTRUIDA` |
| `motivo` no painel | `VERTICES_DIFERENTES`, `FONTE_COM_ERRO`, `SEM_INSUMO` |
| tipo no `CADASTRO_ALTERADO` | `CURVA`, `PROVEDOR`, `CONFIGURACAO` |
| operação no `CADASTRO_ALTERADO` | `CRIACAO`, `ALTERACAO`, `INATIVACAO`, `REATIVACAO`, `EXCLUSAO` |
| operação no `VERTICES_EDITADOS` | `SUBSTITUICAO`, `EXCLUSAO` |
| operação no `CURVA_PRIMARIA_EDITADA` | `INCLUSAO`, `ALTERACAO`, `EXCLUSAO`, `EXCLUSAO_DATA`, `PLANILHA` |
| `origem` no `VERTICES_EDITADOS` | `API`, `PLANILHA` |
| `modo` da importação | `SIMULACAO`, `APLICACAO` |
| `Resultado` na planilha | `SEM_MUDANCA`, `INCLUSAO`, `ALTERACAO`, `EXCLUSAO` ou o código do erro |

Os parâmetros de cálculo (`parametros`) seguem os valores da spec `curve-build-pipeline` do engine, servidos por `GET /api/v1/curvas-mercado/valores` a partir da tabela embutida no serviço (sem chamada ao engine nesta fase).

Avisos do serviço:

| `codigo` | Onde | Significado |
|---|---|---|
| `CURVA_SEM_ORIGEM` | provedores da curva, planilha | curva sem provedor: o engine não a constrói |
| `ORIGEM_INCOMPATIVEL_COM_MODELO` | provedores da curva, configuração | provedor ou produto diferente do esperado pelo modelo nativo |
| `MODELO_NAO_NATIVO` | configuração | modelo, interpolador ou calendário que depende de script Groovy |
| `MODELO_POR_ORIGEM_SEM_PROVEDOR` | provedores da curva, configuração, planilha | chave de `MODELOS_POR_ORIGEM` sem provedor correspondente, ou da origem principal: ignorada pelo engine |
| `CURVA_COM_FILHAS` | inativação | curva é componente de curva derivada ativa |
| `VALORES_SEM_ENGINE` | valores aceitos (change `curves-operacao-curvas`) | engine fora: valores da cópia embutida, só com modelos nativos; não ocorre na primeira parte, que não chama o engine |
| `ENGINE_INDISPONIVEL` | painel (change `curves-operacao-curvas`) | engine fora: situação sem conferência |
| `INTERPOLADA_DESATUALIZADA` | vértices, planilha de vértices | vértices gravados, mas a regravação da curva interpolada no engine falhou; ela fica com a interpolação anterior até ser regravada |
| `PONTO_ANTES_DA_DATA_BASE` | vértices | data do vértice igual ou anterior à data-base |
| `PONTO_EM_FIM_DE_SEMANA` | vértices | vértice em sábado ou domingo |
| `PONTO_EM_FERIADO` | vértices | vértice em feriado do calendário da curva |
| `DIAS_UTEIS_DIFERENTES_DO_CALENDARIO` | vértices | dias úteis informados diferentes do calendário da curva; o engine usa os informados |
| `DIAS_UTEIS_INCOERENTES` | vértices, primária B3 | dias úteis menores que 1 ou maiores que os dias corridos |
| `DIAS_UTEIS_FORA_DE_ORDEM` | vértices | dias úteis iguais ou menores que os de um vértice de data anterior |
| `VALOR_NAO_POSITIVO` | vértices | preço ou vértices menor ou igual a zero |
| `VALOR_ARREDONDADO` | vértices | valor arredondado pela configuração; `detalhes` traz o enviado e o gravado |
| `SEM_CONFIGURACAO` | vértices | sem configuração vigente: gravado sem arredondar |
| `CALENDARIO_NAO_VERIFICADO` | vértices | engine fora ou sem configuração: feriados não conferidos |
| `SEM_MUDANCA` | vértices | lista igual à gravada: nada foi escrito |
| `DIAS_CORRIDOS_NAO_POSITIVO` | primária B3 | dias corridos menores que 1: a construção falha com `INSUMO_INVALIDO` até a correção |
| `DIAS_CORRIDOS_REPETIDOS` | primária B3 | duas linhas da data com os mesmos dias corridos: a construção falha com `INSUMO_INVALIDO` até a correção |
| `CURVA_SEM_PROVEDOR_B3` | primária B3 | a curva não tem provedor `B3`/`TS`: o processor não grava nem substitui essa curva |
| `CURVA_JA_CONSTRUIDA` | primária B3 | a curva já tem vértices na data-base: só um recálculo forçado pelo engine usa a correção |

Um código de aviso ou de erro novo SHALL entrar nestas tabelas e na resposta de `GET /api/v1/curvas-mercado/valores` antes de ser usado.

#### Scenario: Situação lida de coluna CHAR
- **WHEN** a curva `PRE` tem `cSitReg` gravado como `ATIVO` numa coluna `CHAR(20)`
- **THEN** a resposta traz `"situacao": "ATIVO"`, sem espaços

#### Scenario: Enum em caixa errada
- **WHEN** o cliente cria uma curva com `unidade` = `taxa`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS` apontando o campo `unidade` e os valores aceitos

### Requirement: Horário e log
Todo instante (respostas, auditoria, `dCriacReg`, `dUltAtulz`, logs) SHALL usar o fuso `America/Sao_Paulo`, sem depender do fuso do servidor. Toda requisição SHALL gerar log JSON com `correlationId`, usuário, rota, status, código de erro e duração.

#### Scenario: Servidor em UTC
- **WHEN** uma curva é alterada às 22h30 de Brasília com a JVM em UTC
- **THEN** `dUltAtulz` e o evento `CADASTRO_ALTERADO` têm 22h30 de Brasília
