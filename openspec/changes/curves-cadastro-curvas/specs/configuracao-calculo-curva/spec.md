## Purpose

No `services/curves`, manter as versões da configuração de cálculo de cada curva (`tConfgCurva`): modelo de construção, interpolador e parâmetros, com vigência. Garante que, para qualquer data, a curva tenha no máximo uma configuração vigente, e que versões já vigentes não mudem, para que o engine reprocesse datas antigas com a configuração da época.

## ADDED Requirements

### Requirement: Campos da configuração
Cada versão SHALL gravar uma linha em `tConfgCurva`:

| Campo da API | Coluna | Regra |
|---|---|---|
| `versao` | `cVrsaoReg` | gerada: 1 na primeira versão da curva, e a maior versão mais 1 nas seguintes; só leitura |
| curva | `cTickerIndcd` | o nome da curva da rota |
| `modeloConstrucao` | `cMotorCalc` | obrigatório; 1 a 100 caracteres |
| `interpolador` | `cRotnaCalc` | obrigatório; 1 a 100 caracteres |
| `parametros` | `cModDado` | obrigatório; objeto JSON validado conforme abaixo |
| `inicioVigencia` | `dInicVgcia` | obrigatório; data |
| `fimVigencia` | `dValidAte` | calculado pelo serviço; só leitura |

As demais colunas de `tConfgCurva` (`cAtivoFincr`, `cPosCalc`, `cPreCalc`, `cLingSist`, `cPosMotorCalc`, `cPreMotorCalc`, `cTpoInstt`) SHALL ficar nulas.

`parametros` SHALL seguir exatamente a tabela de chaves, tipos, valores aceitos e obrigatoriedade da spec `curve-build-pipeline` do change `engine-construcao-curvas` (`BASE_INTERPOLACAO`, `DAY_COUNTER_TEMPO`, `FREQUENCY`, `CALENDARIO`, `MERCADO_CALENDARIO`, `BUSINESS_DAY_CONVENTION`, `EXTRAPOLACAO_INICIO`, `EXTRAPOLACAO_FIM`, `HORIZONTE`, `CASAS_DECIMAIS`, `MODO_ARREDONDAMENTO`, `VERSAO_SCRIPT_*`, `MODELOS_POR_ORIGEM`), com as mesmas regras de combinação: `FREQUENCY` obrigatório com `Compounded` e proibido nos demais, sem `NoFrequency`, `Once` e `OtherFrequency`; `MERCADO_CALENDARIO` igual ao mercado do `CALENDARIO`; `BASE_INTERPOLACAO` = `Price` só para unidade `PRECO` ou `PONTOS`; `FlatForward` só com interpolador `Linear` ou `LogLinear`. Chave desconhecida, tipo errado, valor fora da lista ou item obrigatório ausente MUST resultar em 422 `DADOS_INVALIDOS`, com um item em `detalhes` por problema. O serviço SHALL gravar `cModDado` como JSON compacto, com as chaves na ordem da tabela; se passar de 1.024 caracteres, 422.

#### Scenario: Configuração da DIxPRE
- **WHEN** o cliente cria a versão da `PRE` com `modeloConstrucao` = `PRONTA_TS_B3`, `interpolador` = `LogLinear` e os parâmetros da spec `b3-ready-curve-model` do engine
- **THEN** a resposta é 201 com a versão 1, e `tConfgCurva.cModDado` tem o JSON compacto dos parâmetros

#### Scenario: Chave escrita errada
- **WHEN** os parâmetros têm a chave `EXTRAPOLACAO_FINAL`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, citando a chave, e nada é gravado

### Requirement: Vigência sem sobreposição e sem buraco
As versões de uma curva SHALL formar uma sequência contínua: o `fimVigencia` de cada versão é o `inicioVigencia` da seguinte menos 1 dia, e a última tem `fimVigencia` nulo. Regras:
- a primeira versão MAY ter `inicioVigencia` no passado, desde que maior ou igual ao `inicioVigencia` da curva;
- uma versão nova MUST ter `inicioVigencia` maior que o da última versão **e** maior ou igual a hoje (horário de Brasília); o serviço SHALL fechar a última versão com `fimVigencia` = `inicioVigencia` da nova menos 1 dia, na mesma transação;
- versões com `inicioVigencia` menor ou igual a hoje MUST NOT ser alteradas nem excluídas;
- só a última versão, e só se ainda não começou (`inicioVigencia` depois de hoje), MAY ser excluída; a anterior volta a ter `fimVigencia` nulo.

Não há alteração de versão: para mudar a configuração, cria-se uma versão nova.

#### Scenario: Troca de interpolador a partir de amanhã
- **WHEN** a `PRE` tem só a versão 1 (desde `2026-01-01`), e em `2026-09-25` é criada a versão 2 com `inicioVigencia` = `2026-09-26`
- **THEN** a versão 1 passa a ter `fimVigencia` = `2026-09-25`, a versão 2 fica aberta, e a construção de `2026-09-25` continua usando a versão 1

#### Scenario: Correção retroativa
- **WHEN** em `2026-09-25` o cliente tenta criar uma versão com `inicioVigencia` = `2026-09-20`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, informando que a vigência não pode começar no passado

#### Scenario: Desistência de uma versão futura
- **WHEN** a versão 2, que começaria em `2026-10-01`, é excluída em `2026-09-25`
- **THEN** a versão 1 volta a ficar aberta, e a exclusão é auditada

### Requirement: Rotas da configuração
O serviço SHALL expor (prefixo `/api/v1`):

| Rota | Uso | Papel |
|---|---|---|
| `GET /curvas-mercado/{codigo}/configuracoes` | listar as versões, da mais nova para a mais antiga | `Curvas.Leitura` |
| `GET /curvas-mercado/{codigo}/configuracoes/vigente?data=AAAA-MM-DD` | versão vigente na data (padrão: hoje) | `Curvas.Leitura` |
| `POST /curvas-mercado/{codigo}/configuracoes/validacao` | validar uma versão sem gravar, devolvendo erros e avisos | `Curvas.Leitura` |
| `POST /curvas-mercado/{codigo}/configuracoes` | criar versão | `Curvas.Cadastro` |
| `DELETE /curvas-mercado/{codigo}/configuracoes/{versao}` | excluir a última versão, se ainda não começou | `Curvas.Cadastro` |

As alterações seguem a concorrência otimista da curva (`If-Match` com o `ETag` da curva).

#### Scenario: Configuração vigente numa data antiga
- **WHEN** o cliente consulta a vigente da `PRE` em `2026-09-25`, depois da criação da versão 2 que começa em `2026-09-26`
- **THEN** a resposta traz a versão 1

### Requirement: Valores aceitos para o front, o Swagger e a planilha
O engine é a fonte dos valores aceitos no cálculo (`GET /api/v1/valores-cadastro`, spec `curve-engine-api` do change `engine-construcao-curvas`). O serviço SHALL:
- expor `GET /api/v1/curvas-mercado/valores` (papel `Curvas.Leitura`), com a resposta do engine acrescida dos provedores e produtos de `tPrvdrDadoMercd` e dos enums e catálogos de avisos e erros do próprio serviço (spec `cadastro-curva-mercado`), com `rotulo` e `descricao` em pt-BR de cada valor. O front SHALL montar as listas dos formulários de curva, ligação e configuração só a partir desta rota, sem valores fixos no front. O serviço consulta o engine com token de serviço e tempo limite de 10 segundos, e guarda a resposta em cache por 5 minutos;
- nunca falhar por causa do engine: se ele não responder, SHALL devolver a cópia embutida no serviço (a mesma tabela que valida `parametros`), só com os modelos nativos, e o aviso `VALORES_SEM_ENGINE`;
- declarar no OpenAPI (Swagger) como `enum` os campos `unidade`, `dayCounterCotacao`, `compounding` e `situacao`, e cada chave de `parametros` (objeto sem propriedades adicionais), com a descrição de cada valor. `modeloConstrucao`, `interpolador` e `CALENDARIO` SHALL ser `string`, com os nativos na descrição e referência a `/valores`, porque scripts Groovy podem acrescentar nomes;
- gerar a aba `Valores` da planilha (spec `cadastro-curvas-planilha`) a partir da mesma resposta.

Um teste de contrato SHALL comparar a tabela embutida no serviço com a parte fixa de `GET /api/v1/valores-cadastro` do engine: qualquer diferença falha o build.

#### Scenario: Formulário de configuração
- **WHEN** o front abre o formulário de nova versão da `PRE`
- **THEN** as listas de base de interpolação, eixo, calendário, convenção, extrapolação e arredondamento vêm de `GET /api/v1/curvas-mercado/valores`, com as descrições, e o interpolador lista os nativos e os scripts Groovy ativos

#### Scenario: Engine fora
- **WHEN** o engine não responde e o front pede os valores
- **THEN** a resposta é 200 com os valores fixos e os modelos nativos, e o aviso `VALORES_SEM_ENGINE`

### Requirement: Modelo de construção por origem secundária
`parametros` MAY ter `MODELOS_POR_ORIGEM`: um objeto em que cada chave é `{provedor}/{produto}` de uma ligação da curva (spec `ligacao-curva-provedor`) e o valor é o modelo de construção que lê aquela origem quando o usuário constrói a curva por ela no engine (spec `curve-build-pipeline` do change `engine-modelos-curva`, requisito "Construção por uma origem secundária"). O serviço SHALL recusar com 422 `DADOS_INVALIDOS` a chave fora do formato `{provedor}/{produto}` e o valor que não seja texto de 1 a 100 caracteres. Sem bloquear, SHALL trazer os avisos:
- `MODELO_POR_ORIGEM_SEM_LIGACAO`: a chave não corresponde a nenhuma ligação atual da curva, ou corresponde à origem principal (a entrada é ignorada pelo engine);
- `ORIGEM_INCOMPATIVEL_COM_MODELO`: o modelo nativo informado não aceita aquela origem;
- `MODELO_NAO_NATIVO`: o modelo informado não é nativo.

A ausência de `MODELOS_POR_ORIGEM` não é aviso: sem ela, o engine usa `modeloConstrucao` também para a origem secundária, se ele aceitar a origem.

#### Scenario: Reserva da B3 para uma curva ANBIMA
- **WHEN** a curva `DI_BACKUP` tem as ligações `ANBIMA`/`CZ` (prioridade 1) e `B3`/`TS` (prioridade 2), e a versão nova traz `MODELOS_POR_ORIGEM` = `{"B3/TS":"PRONTA_TS_B3"}`
- **THEN** a versão é criada sem aviso, e `cModDado` guarda o objeto no JSON compacto

#### Scenario: Ligação excluída depois
- **WHEN** a ligação `B3`/`TS` da `DI_BACKUP` é excluída, e a versão vigente ainda tem a chave `B3/TS`
- **THEN** a exclusão é feita com o aviso `MODELO_POR_ORIGEM_SEM_LIGACAO`, e a construção da `DI_BACKUP` pela origem principal continua funcionando

### Requirement: Coerência entre curva e configuração
Uma alteração da curva (`unidade`, `dayCounterCotacao`, `compounding`) que torne inválida a versão vigente ou uma versão futura, pelas regras de combinação dos parâmetros, MUST ser rejeitada com 422 `DADOS_INVALIDOS`, citando a versão. Sem bloquear, a resposta de criação ou validação de versão SHALL trazer o aviso `MODELO_NAO_NATIVO` para `modeloConstrucao`, `interpolador` ou `CALENDARIO` fora dos modelos nativos do engine (construção `PRONTA_TS_B3`, `NTNB_BOOTSTRAP_ANBIMA`, `SOFR_ZERO_BLOOMBERG`; interpoladores `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`; calendários `Brazil`, `UnitedStates`): o engine só constrói se houver script Groovy ativo com esse nome. Também SHALL trazer o aviso `ORIGEM_INCOMPATIVEL_COM_MODELO` da spec `ligacao-curva-provedor`, quando aplicável.

#### Scenario: Mudança de unidade que invalida a configuração
- **WHEN** a curva `PRE`, com versão vigente de `BASE_INTERPOLACAO` = `Discount`, é alterada para unidade `PRECO`
- **THEN** a resposta é 422 com `DADOS_INVALIDOS`, citando a versão vigente, e nada é gravado
