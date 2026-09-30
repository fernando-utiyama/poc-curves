## Purpose

No `services/curves`, criar, consultar, alterar, inativar e reativar as curvas de mercado (`tCurvaMercd`), que são a base do cadastro lido pelo engine. Define também as regras comuns a todo o CRUD de cadastro do serviço: identificação, autenticação, erros, concorrência, auditoria (log e arquivo montado na hora) e horário.

## ADDED Requirements

### Requirement: Campos da curva de mercado
O serviço SHALL gravar em `tCurvaMercd`:

| Campo da API | Coluna | Regra |
|---|---|---|
| `codigo` | `cTickerIdtfdUnic` | obrigatório; 1 a 50 caracteres de `A-Z` (só maiúsculas), `0-9` e `_`; único entre todas as curvas. As rotas fixas em minúsculas (`painel`, `valores`, `exportacao`, `importacao`, `pontos`) nunca colidem com um código |
| `nome` | `cTickerIndcd` | obrigatório; 1 a 50 caracteres; único entre todas as curvas depois de normalizado (sem acentos, minúsculo, sem espaços nas pontas); **imutável** depois de criado, porque é a chave de todas as FKs |
| `unidade` | `cTpoVlr` | obrigatório: `TAXA`, `PRECO` ou `PONTOS` |
| `dayCounterCotacao` | `cNormaDia` | obrigatório se `unidade` = `TAXA`, e nulo caso contrário: `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` |
| `compounding` | `cTpoJuro` | obrigatório se `unidade` = `TAXA`, e nulo caso contrário: `Simple`, `Compounded`, `Continuous` |
| `moeda` | `cMoedaNegoc` | obrigatório; código ISO 4217 (ex.: `BRL`, `USD`) |
| `pais` | `cPaisInstt` | obrigatório; código ISO 3166-1 alfa-2 (ex.: `BR`, `US`) |
| `classificacao` | `cClasfInstt` | opcional; até 50 caracteres |
| `classeAtivo` | `cClassAtivo` | opcional; até 50 caracteres |
| `situacao` | `cSitReg` | `ATIVO` ou `INATIVO`; `ATIVO` na criação |
| `inicioVigencia` | `dInicVgcia` | obrigatório; data |
| `fimVigencia` | `dValidAte` | opcional; data maior ou igual a `inicioVigencia` |

O serviço SHALL preencher `cUsuarAtulz` (usuário autenticado), `dCriacReg` (na criação) e `dUltAtulz` (a cada alteração). O serviço MUST NOT gravar `dBaseReft` nem `cUsuarCalc`, que são do engine, nem as colunas sem uso definido (`cPprioDado`, `cConfgIdtfd`, `cCurvaReft`, `cFamlInsttFincr`, `cIndxdAtivo`, `cTpoCotac`, `iPrvdrDados`, `rAtivoIndcd`, `vFatorMultiAtivo`), que ficam nulas nas curvas criadas pelo serviço e intocadas nas demais.

#### Scenario: Criação da DIxPRE
- **WHEN** o cliente cria a curva com `codigo` = `PRE`, `nome` = `DIxPRE`, `unidade` = `TAXA`, `dayCounterCotacao` = `Business252`, `compounding` = `Compounded`, `moeda` = `BRL`, `pais` = `BR` e `inicioVigencia` = `2026-01-01`
- **THEN** a resposta é 201 com a curva, `situacao` = `ATIVO`, e `tCurvaMercd` tem a linha `DIxPRE` com o código `PRE`

#### Scenario: Nome que colide depois de normalizado
- **WHEN** já existe a curva `Cupom limpo de dólar` e o cliente cria outra com o nome `CUPOM LIMPO DE DOLAR`
- **THEN** a resposta é 409 com `NOME_EM_USO`, e nada é gravado

#### Scenario: Unidade de preço com cotação
- **WHEN** o cliente cria uma curva `PRECO` informando `compounding`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, apontando o campo `compounding`

### Requirement: Rotas da curva de mercado
O serviço SHALL expor (prefixo `/api/v1`), identificando a curva pelo código:

| Rota | Uso | Papel |
|---|---|---|
| `GET /curvas-mercado?nome=&codigo=&unidade=&situacao=&pagina=&tamanho=` | listar, com filtros por trecho de nome (normalizado), código exato, unidade e situação; paginado (tamanho padrão 50, máximo 500), ordenado por código | `Curvas.Leitura` |
| `GET /curvas-mercado/{codigo}` | consultar a curva, com as ligações e a configuração vigente hoje | `Curvas.Leitura` |
| `POST /curvas-mercado` | criar | `Curvas.Cadastro` |
| `PUT /curvas-mercado/{codigo}` | alterar os campos, menos `nome` e `situacao` | `Curvas.Cadastro` |
| `POST /curvas-mercado/{codigo}/inativacao` | inativar | `Curvas.Cadastro` |
| `POST /curvas-mercado/{codigo}/reativacao` | reativar | `Curvas.Cadastro` |

Não há exclusão física: a curva pode ter pontos, dados brutos e configurações que dependem dela. Inativar a curva, ou deixar a data-base fora de `inicioVigencia`..`fimVigencia`, faz a carga deixar de construí-la automaticamente (spec `curve-load-trigger` do change `engine-modelos-curva`); o usuário ainda pode construí-la pelo engine, com aviso, e os pontos já gravados continuam consultáveis. Linhas de `tCurvaMercd` sem código (`cTickerIdtfdUnic` nulo) MUST NOT aparecer nas rotas. Alterar `codigo` SHALL ser permitido, desde que o novo seja único; o nome não muda.

#### Scenario: Tentativa de renomear
- **WHEN** o cliente envia no `PUT` um `nome` diferente do atual
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, informando que o nome é imutável

#### Scenario: Inativação
- **WHEN** a curva `SLP` é inativada
- **THEN** `cSitReg` passa a `INATIVO`, a curva continua consultável, as ligações e configurações são mantidas, e a carga deixa de construí-la automaticamente

### Requirement: Concorrência otimista
Toda resposta de consulta de uma curva SHALL trazer o cabeçalho `ETag` = SHA-256, em hexadecimal minúsculo, do JSON canônico (chaves em ordem alfabética, sem espaços) formado pelos campos da API da curva, pelas suas ligações (ordenadas por `idLigacao`) e pelas suas versões de configuração (ordenadas por `versao`). Os campos gravados pelo engine (`dBaseReft`, `cUsuarCalc`) e os de controle (`cUsuarAtulz`, `dCriacReg`, `dUltAtulz`) MUST NOT entrar no cálculo, para que uma construção do engine não invalide a edição de ninguém. Toda alteração (`PUT`, inativação, reativação e as alterações de ligações e configurações da curva) MUST exigir o cabeçalho `If-Match` com esse valor. Ausente, a resposta MUST ser 428; diferente do atual, 412 com `ALTERADO_POR_OUTRO`, sem gravar nada.

#### Scenario: Duas pessoas editando a mesma curva
- **WHEN** duas pessoas leem a curva `PRE` e as duas enviam alterações com o mesmo `ETag`
- **THEN** a primeira é gravada, e a segunda recebe 412 com `ALTERADO_POR_OUTRO`

### Requirement: Autenticação, papéis e erros
Toda rota MUST exigir token JWT do Entra ID, no mesmo registro de aplicação do engine. Leitura exige `Curvas.Leitura`; escrita, o papel novo `Curvas.Cadastro`. Token ausente ou inválido: 401 `NAO_AUTENTICADO`; sem papel: 403 `SEM_PERMISSAO`. Toda resposta de erro SHALL seguir o padrão do projeto, o mesmo do engine: Problem Details (RFC 9457, `application/problem+json`) pelo tratador de exceções padrão (`ApplicationExceptionHandler`), com `type`, `title`, `status`, `detail` (em português, do `MessageSource`), `instance`, e as propriedades `code` (o código abaixo), `correlationId` e, quando houver, `detalhes` (`campo`, `linha`, `valor`, `motivo`, com nulo no que não se aplica); sem stack trace. Os códigos:

| `codigoErro` | HTTP | Quando |
|---|---|---|
| `PARAMETRO_INVALIDO` | 400 | parâmetro ou JSON malformado |
| `NAO_AUTENTICADO` | 401 | token ausente ou inválido |
| `SEM_PERMISSAO` | 403 | sem o papel exigido |
| `NAO_ENCONTRADO` | 404 | curva, ligação, versão ou provedor inexistente |
| `CODIGO_EM_USO`, `NOME_EM_USO`, `LIGACAO_DUPLICADA`, `PRIORIDADE_EM_USO` | 409 | unicidade violada |
| `ALTERADO_POR_OUTRO` | 412 | `If-Match` diferente do estado atual |
| `DADOS_INVALIDOS` | 422 | regra de campo violada; `detalhes` lista cada campo |
| `IF_MATCH_AUSENTE` | 428 | alteração sem `If-Match` |
| `ERRO_INTERNO` | 500 | qualquer outro erro |

Toda resposta SHALL trazer `X-Correlation-Id` (o recebido ou um UUID gerado).

#### Scenario: Escrita sem papel de cadastro
- **WHEN** um usuário só com `Curvas.Leitura` tenta criar uma curva
- **THEN** a resposta é 403 com `SEM_PERMISSAO`

### Requirement: Auditoria do cadastro
Nada do cadastro SHALL ser gravado no Blob Storage, que guarda só os arquivos originais dos feeders e os scripts Groovy. Toda alteração do cadastro (curva, ligação ou configuração, pela API ou pela planilha) SHALL emitir, depois do commit, o evento de log `CADASTRO_ALTERADO` com nível `AVISO`: `idAuditoria`, código, nome, tipo (`CURVA`, `LIGACAO` ou `CONFIGURACAO`), operação (`CRIACAO`, `ALTERACAO`, `INATIVACAO`, `REATIVACAO`, `EXCLUSAO`), usuário, instante (horário de Brasília), `correlationId`, `idLote` (quando vier da planilha), estado anterior e estado novo completos. O evento traz sempre o nome, que é imutável, para o histórico sobreviver a uma troca de código. O destino dos logs SHALL ter retenção definida pela área de risco.

`GET /api/v1/curvas-mercado/{codigo}/auditoria?formato=xlsx|json` (papel `Curvas.Leitura`), pedido pelo front, SHALL montar na hora, sem guardar nada, o arquivo de auditoria do cadastro da curva: a curva com todos os campos, inclusive `cUsuarAtulz`, `dCriacReg`, `dUltAtulz`, `dBaseReft` e `cUsuarCalc`; todas as ligações; todas as versões de configuração, com vigência e parâmetros; e o `ETag` atual. O nome do arquivo SHALL ser `{codigo}_CADASTRO_AUDITORIA_{AAAAMMDDHHmmss}.xlsx`, no horário de Brasília. Quem alterou o quê antes está nos eventos `CADASTRO_ALTERADO` do log.

#### Scenario: Quem mudou a unidade
- **WHEN** a unidade de uma curva é alterada
- **THEN** o log tem um `CADASTRO_ALTERADO` com o usuário, a unidade anterior e a nova, e nada é gravado no Blob

#### Scenario: Arquivo de auditoria pedido pelo front
- **WHEN** o gestor pede a auditoria do cadastro da `PRE`
- **THEN** o arquivo é montado na hora, com a curva, as ligações e todas as versões de configuração, e `cUsuarAtulz` e `dUltAtulz` mostram quem fez a última alteração e quando

### Requirement: Contrato de tipos para o front
Toda resposta JSON do serviço SHALL seguir o mesmo contrato de tipos do engine (spec `curve-engine-api` do change `engine-modelos-curva`): decimais como string em notação simples, datas `AAAA-MM-DD`, instantes em ISO-8601 com o deslocamento de Brasília, enums como string exatamente como nas specs, com diferença entre maiúsculas e minúsculas, e `avisos` como lista de `{ "codigo", "mensagem", "detalhes" }`, vazia quando não há aviso. O front é pt-BR: a API troca valores em formato de máquina e o front formata para pt-BR na tela (vírgula decimal, `dd/mm/aaaa`, horário de Brasília); mensagens de erro e de aviso, rótulos e descrições SHALL estar em pt-BR, com acentuação, em UTF-8; os códigos não são traduzidos. Na entrada, enum com caixa diferente (`taxa`, `business252`) MUST ser recusado com 422 `DADOS_INVALIDOS`, e decimal SHALL ser aceito como string.

As colunas `CHAR` de `tCurvaMercd` (`cNormaDia`, `cTpoJuro`, `cSitReg`, `cTpoVlr`, `cPaisInstt`) são completadas com espaços pelo banco. O serviço SHALL gravar os valores sem espaços e SHALL aparar os espaços à direita de toda coluna de texto lida, antes de devolver, comparar ou calcular o `ETag`.

Enums do serviço:

| Campo | Valores |
|---|---|
| `unidade` | `TAXA`, `PRECO`, `PONTOS` |
| `dayCounterCotacao` | `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` |
| `compounding` | `Simple`, `Compounded`, `Continuous` |
| `situacao` da curva | `ATIVO`, `INATIVO` |
| `provedor` da ligação | os de `tPrvdrDadoMercd`; o engine e o processor reconhecem `B3`, `ANBIMA`, `BLOOMBERG` e o interno `TCEN` |
| `produto` da ligação | `TS` (B3, arquivo Taxas de Mercado para Swaps), `MS` (ANBIMA, arquivo de Mercado Secundário de títulos públicos, `ms{AAMMDD}.txt`), `BLC2` (Bloomberg, fonte de preço do curve member no ticker); para `TCEN`, o papel da curva componente declarado pelo modelo derivado (ex.: `NUMERADOR`, `DENOMINADOR`) |
| situação no painel | `NAO_E_DIA_UTIL`, `IGNORADA`, `SITUACAO_INDISPONIVEL`, `INTERPOLADA_DESATUALIZADA`, `CONSTRUIDA`, `DIVERGENTE_DA_FONTE`, `AGUARDANDO_COMPONENTES`, `AGUARDANDO_CARGA`, `COM_ERRO`, `NAO_CONSTRUIDA` |
| `motivo` no painel | `PONTOS_DIFERENTES`, `FONTE_COM_ERRO`, `SEM_INSUMO` |
| tipo no `CADASTRO_ALTERADO` | `CURVA`, `LIGACAO`, `CONFIGURACAO` |
| operação no `CADASTRO_ALTERADO` | `CRIACAO`, `ALTERACAO`, `INATIVACAO`, `REATIVACAO`, `EXCLUSAO` |
| operação no `PONTOS_EDITADOS` | `SUBSTITUICAO`, `EXCLUSAO` |
| `origem` no `PONTOS_EDITADOS` | `API`, `PLANILHA` |
| `modo` da importação | `SIMULACAO`, `APLICACAO` |
| `Resultado` na planilha | `SEM_MUDANCA`, `INCLUSAO`, `ALTERACAO`, `EXCLUSAO` ou o código do erro |

Os parâmetros de cálculo (`parametros`) seguem os valores da spec `curve-build-pipeline` do engine, repassados por `GET /api/v1/curvas-mercado/valores`.

Avisos do serviço:

| `codigo` | Onde | Significado |
|---|---|---|
| `CURVA_SEM_ORIGEM` | ligações, planilha | curva sem ligação: o engine não a constrói |
| `ORIGEM_INCOMPATIVEL_COM_MODELO` | ligações, configuração | provedor ou produto diferente do esperado pelo modelo nativo |
| `MODELO_NAO_NATIVO` | configuração | modelo, interpolador ou calendário que depende de script Groovy |
| `MODELO_POR_ORIGEM_SEM_LIGACAO` | ligações, configuração, planilha | chave de `MODELOS_POR_ORIGEM` sem ligação correspondente, ou da origem principal: ignorada pelo engine |
| `CURVA_COM_FILHAS` | inativação | curva é componente de curva derivada ativa |
| `VALORES_SEM_ENGINE` | valores aceitos | engine fora: valores da cópia embutida, só com modelos nativos |
| `ENGINE_INDISPONIVEL` | painel | engine fora: situação sem conferência |
| `INTERPOLADA_DESATUALIZADA` | pontos, planilha de pontos | pontos gravados, mas a regravação da curva interpolada no engine falhou; ela fica com a interpolação anterior até ser regravada |
| `PONTO_ANTES_DA_DATA_BASE` | pontos | data do ponto igual ou anterior à data-base |
| `PONTO_EM_FIM_DE_SEMANA` | pontos | ponto em sábado ou domingo |
| `PONTO_EM_FERIADO` | pontos | ponto em feriado do calendário da curva |
| `DIAS_UTEIS_DIFERENTES_DO_CALENDARIO` | pontos | dias úteis informados diferentes do calendário da curva; o engine usa os informados |
| `DIAS_UTEIS_INCOERENTES` | pontos | dias úteis menores que 1 ou maiores que os dias corridos |
| `DIAS_UTEIS_FORA_DE_ORDEM` | pontos | dias úteis iguais ou menores que os de um ponto de data anterior |
| `VALOR_NAO_POSITIVO` | pontos | preço ou pontos menor ou igual a zero |
| `VALOR_ARREDONDADO` | pontos | valor arredondado pela configuração; `detalhes` traz o enviado e o gravado |
| `SEM_CONFIGURACAO` | pontos | sem configuração vigente: gravado sem arredondar |
| `CALENDARIO_NAO_VERIFICADO` | pontos | engine fora ou sem configuração: feriados não conferidos |
| `SEM_MUDANCA` | pontos | lista igual à gravada: nada foi escrito |

Um código de aviso ou de erro novo SHALL entrar nestas tabelas e na resposta de `GET /api/v1/curvas-mercado/valores` antes de ser usado.

#### Scenario: Situação lida de coluna CHAR
- **WHEN** a curva `PRE` tem `cSitReg` gravado como `ATIVO` numa coluna `CHAR(20)`
- **THEN** a resposta traz `"situacao": "ATIVO"`, sem espaços, e o `ETag` é o mesmo de antes da leitura

#### Scenario: Enum em caixa errada
- **WHEN** o cliente cria uma curva com `unidade` = `taxa`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS` apontando o campo `unidade` e os valores aceitos

### Requirement: Horário e log
Todo instante (respostas, auditoria, `dCriacReg`, `dUltAtulz`, logs) SHALL usar o fuso `America/Sao_Paulo`, sem depender do fuso do servidor. Toda requisição SHALL gerar log JSON com `correlationId`, usuário, rota, status, código de erro e duração.

#### Scenario: Servidor em UTC
- **WHEN** uma curva é alterada às 22h30 de Brasília com a JVM em UTC
- **THEN** `dUltAtulz` e o evento `CADASTRO_ALTERADO` têm 22h30 de Brasília
