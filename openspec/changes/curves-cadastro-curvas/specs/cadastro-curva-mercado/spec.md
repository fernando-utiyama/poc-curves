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
Toda rota MUST exigir token JWT do Entra ID, no mesmo registro de aplicação do engine. Leitura exige `Curvas.Leitura`; escrita, o papel novo `Curvas.Cadastro`. Token ausente ou inválido: 401 `NAO_AUTENTICADO`; sem papel: 403 `SEM_PERMISSAO`. Toda resposta de erro SHALL ter o corpo `{ "codigoErro", "mensagem", "correlationId", "detalhes": [ { "campo", "linha", "motivo" } ] }`, em português, sem stack trace, com os códigos:

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

### Requirement: Horário e log
Todo instante (respostas, auditoria, `dCriacReg`, `dUltAtulz`, logs) SHALL usar o fuso `America/Sao_Paulo`, sem depender do fuso do servidor. Toda requisição SHALL gerar log JSON com `correlationId`, usuário, rota, status, código de erro e duração.

#### Scenario: Servidor em UTC
- **WHEN** uma curva é alterada às 22h30 de Brasília com a JVM em UTC
- **THEN** `dUltAtulz` e o evento `CADASTRO_ALTERADO` têm 22h30 de Brasília
