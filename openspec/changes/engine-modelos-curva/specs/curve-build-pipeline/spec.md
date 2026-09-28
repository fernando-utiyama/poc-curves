## Purpose

Constrói e grava os pontos de uma curva numa data a partir do cadastro da curva, e interpola esses pontos sob demanda. Define de forma fechada as unidades, as contagens de tempo, as fórmulas de cotação, interpolação e extrapolação, o domínio, o arredondamento e os erros, para que a mesma entrada produza sempre a mesma saída em qualquer implementação.

## ADDED Requirements

### Requirement: Cadastro da curva e itens obrigatórios
O engine SHALL montar o cadastro de uma curva na data-base a partir das tabelas abaixo, e de nenhuma outra fonte:
- `tCurvaMercd`: `cTickerIdtfdUnic` = **código** (rotas por código); `cTickerIndcd` = **nome de exibição** (chave de todas as FKs, inclusive de `tDadoVertcCurva` e `tDadoCurva`); `cTpoVlr` = **unidade** (`TAXA`, `PRECO` ou `PONTOS`); `cNormaDia` = `DayCounter` da cotação; `cTpoJuro` = `Compounding` da cotação.
- `tCurvaPrvdr`: a linha da curva com o **menor** `cPriorCsumo` é a **origem**: `iPrvdrDados` = fonte (ex.: `B3`), `cPrvdrMercd` = produto (ex.: `TS`), `cTickerPrvdr` = código na fonte (ex.: `PRE`). As demais linhas são as origens secundárias, usadas só quando o usuário pede a construção por uma delas (requisito "Construção por uma origem secundária"): não há troca automática de fonte.
- `tConfgCurva`: a linha **vigente** na data-base, isto é, com `cTickerIndcd` = nome, `dInicVgcia <= dataBase` e (`dValidAte` nulo ou `dValidAte >= dataBase`). `cMotorCalc` = modelo de construção; `cRotnaCalc` = interpolador.
- `tConfgCurva.cModDado` da configuração vigente: um objeto JSON (até 1024 caracteres) com os parâmetros da tabela abaixo, cada um como string, número ou, só em `MODELOS_POR_ORIGEM`, objeto. Ex.: `{"GRANDEZA":"Discount","CASAS_DECIMAIS":7,...}`. `tParmConfgCurva` não é lida nesta fase.

| Chave | Tipo JSON | Obrigatória | Valores aceitos |
|---|---|---|---|
| `GRANDEZA` | string | sim | `Discount`, `CompoundFactor`, `ZeroYield`, `Price` |
| `DAY_COUNTER_TEMPO` | string | sim | `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` |
| `FREQUENCY` | string | só se `cTpoJuro` = `Compounded` | `Annual` (1), `Semiannual` (2), `EveryFourthMonth` (3), `Quarterly` (4), `Bimonthly` (6), `Monthly` (12), `EveryFourthWeek` (13), `Biweekly` (26), `Weekly` (52), `Daily` (365); o número é o `f` da fórmula de cotação |
| `CALENDARIO` | string | sim | `Brazil`, `UnitedStates` ou nome de calendário Groovy |
| `MERCADO_CALENDARIO` | string | sim | o mercado do calendário: `Settlement` para `Brazil`, `FederalReserve` para `UnitedStates`, e o mercado declarado pelo script para calendário Groovy |
| `BUSINESS_DAY_CONVENTION` | string | sim | `Following`, `ModifiedFollowing`, `Preceding`, `ModifiedPreceding`, `Unadjusted`, `HalfMonthModifiedFollowing`, `Nearest` |
| `EXTRAPOLACAO_INICIO` | string | não (padrão `Disabled`) | `Disabled`, `FlatForward`, `FlatValue` |
| `EXTRAPOLACAO_FIM` | string | não (padrão `Disabled`) | `Disabled`, `FlatForward`, `FlatValue` |
| `HORIZONTE` | string | sim | `Period` do QuantLib: inteiro positivo + `D`, `W`, `M` ou `Y` (ex.: `10Y`) |
| `CASAS_DECIMAIS` | número | sim | inteiro de 0 a 12 |
| `MODO_ARREDONDAMENTO` | string | sim | `HALF_UP`, `HALF_EVEN`, `DOWN` (`DOWN` = truncamento) |
| `VERSAO_SCRIPT_CONSTRUCAO`, `VERSAO_SCRIPT_INTERPOLACAO`, `VERSAO_SCRIPT_CALENDARIO` | número | não | versão validada do script Groovy |
| `MODELOS_POR_ORIGEM` | objeto | não | modelo de construção por origem secundária: chave `{fonte}/{produto}` de uma ligação da curva em `tCurvaPrvdr`, valor o nome de um modelo de construção (ex.: `{"B3/TS":"PRONTA_TS_B3"}`); chave fora do formato `{fonte}/{produto}` ou valor que não seja texto não vazio é inválido; entrada de uma origem que a curva não tem, ou da principal, é ignorada, porque só a entrada da origem pedida é usada |

Para unidade `TAXA`, `cNormaDia` e `cTpoJuro` SHALL ser obrigatórios; para `PRECO` e `PONTOS`, SHALL ser ignorados. O cadastro MUST ser rejeitado com erro `CADASTRO_INVALIDO`, informando o código, o item e o motivo, quando:
- faltar um item obrigatório;
- um valor não estiver entre os aceitos;
- `cModDado` não for um objeto JSON válido, ou tiver chave desconhecida ou valor de tipo diferente do da tabela;
- não houver linha vigente em `tConfgCurva`, ou houver mais de uma;
- `GRANDEZA` = `Price` com unidade `TAXA`, ou `GRANDEZA` diferente de `Price` com unidade `PRECO` ou `PONTOS`;
- `cTpoJuro` for `SimpleThenCompounded` ou `CompoundedThenSimple` (não suportados nesta fase);
- a política `FlatForward` for usada com interpolador que não seja `Linear` ou `LogLinear`;
- a fonte ou o produto da origem não forem os esperados pelo modelo de construção cadastrado (para a origem principal, `cMotorCalc`; para uma secundária, o modelo de `MODELOS_POR_ORIGEM` ou, sem ele, `cMotorCalc`);
- `FREQUENCY` for informada sem `cTpoJuro` = `Compounded`, ou for `NoFrequency`, `Once` ou `OtherFrequency`, que não definem `f`;
- `MERCADO_CALENDARIO` não for o mercado do `CALENDARIO`.

A situação (`cSitReg`) e a vigência da curva em `tCurvaMercd` (`dInicVgcia` a `dValidAte`) MUST NOT gerar `CADASTRO_INVALIDO`: elas só decidem se a construção automática (carga ou construção da data pelo orquestrador) constrói a curva (spec `curve-load-trigger`). A construção pedida pelo usuário em `POST .../construcao` e a simulação SHALL construir mesmo com a curva `INATIVO` ou com a data-base fora da vigência, com o aviso `CURVA_INATIVA` ou `FORA_DA_VIGENCIA_CURVA` na resposta, no log (inclusive em `CURVA_GRAVADA`) e na memória de cálculo. Consultar e interpolar pontos gravados não dependem da situação nem da vigência.

Nenhum valor padrão SHALL ser usado além dos dois marcados na tabela.

Os valores de enum SHALL ser comparados exatamente como escritos nesta spec, com diferença entre maiúsculas e minúsculas (`business252` é inválido). As colunas `CHAR` de `tCurvaMercd` (`cNormaDia`, `cTpoJuro`, `cSitReg`, `cTpoVlr`, `cPaisInstt`) devolvem o valor completado com espaços à direita; o engine SHALL aparar os espaços à direita de toda coluna de texto do cadastro antes de interpretá-la. `cSitReg` diferente de `ATIVO`, inclusive nulo, SHALL ser tratado como curva inativa.

#### Scenario: Cadastro completo da PRE
- **WHEN** a curva `PRE` tem origem `B3`/`TS`/`PRE`, construção `PRONTA_TS_B3`, interpolador `LogLinear`, unidade `TAXA`, `cNormaDia` = `Business252`, `cTpoJuro` = `Compounded` e os parâmetros `GRANDEZA` = `Discount`, `DAY_COUNTER_TEMPO` = `Business252`, `FREQUENCY` = `Annual`, `CALENDARIO` = `Brazil`, `MERCADO_CALENDARIO` = `Settlement`, `BUSINESS_DAY_CONVENTION` = `Following`, `EXTRAPOLACAO_FIM` = `FlatForward`, `HORIZONTE` = `10Y`, `CASAS_DECIMAIS` = 7, `MODO_ARREDONDAMENTO` = `HALF_UP`
- **THEN** a curva pode ser construída e interpolada sem nenhum parâmetro vindo do chamador, com extrapolação de início `Disabled`

#### Scenario: Item obrigatório ausente
- **WHEN** a `DCL` não tem `cRotnaCalc` na configuração vigente
- **THEN** a construção falha com `CADASTRO_INVALIDO` informando `DCL` e o item interpolador, e nada é gravado

#### Scenario: Chave desconhecida
- **WHEN** a configuração vigente da `PRE` tem a chave `EXTRAPOLACAO_FINAL`
- **THEN** a construção falha com `CADASTRO_INVALIDO` informando a chave `EXTRAPOLACAO_FINAL`

### Requirement: Curva derivada de outras curvas
Uma curva SHALL poder ser derivada de outras curvas de mercado já construídas (ex.: inflação implícita = PRE sobre a NTN-B bootstrapada), sem mudança de schema. Uma curva é **derivada** quando a sua ligação de menor `cPriorCsumo` em `tCurvaPrvdr` tem `iPrvdrDados` = `TCEN`, um provedor interno. Nesse caso, **todas** as ligações da curva com `iPrvdrDados` = `TCEN` são as **curvas mães**: `cTickerPrvdr` = nome da curva mãe (`tCurvaMercd.cTickerIndcd`) e `cPrvdrMercd` = papel da mãe no cálculo (ex.: `NUMERADOR`, `DENOMINADOR`), definido pelo modelo de construção. Ligações de outros provedores na mesma curva são ignoradas nesta fase.

O cadastro de uma curva derivada MUST ser rejeitado com `CADASTRO_INVALIDO` quando:
- uma curva mãe não existir;
- a curva for mãe dela mesma, direta ou indiretamente (ciclo);
- o modelo de construção não aceitar a fonte `TCEN`, ou os papéis cadastrados não forem exatamente os que o modelo declara.

Nenhum modelo de construção nativo desta fase aceita a fonte `TCEN`: a estrutura existe para que um modelo derivado (Java numa mudança futura ou script Groovy) seja incluído só com cadastro e o modelo. O modelo derivado lê as mães pelo contexto de construção (spec `curve-extension-models`), nunca pelas tabelas brutas. A disparada em cadeia e a exigência de mães construídas estão na spec `curve-load-trigger`. A proveniência da construção de uma curva derivada SHALL trazer, para cada mãe, nome, papel e `hashPontos` dos pontos usados.

#### Scenario: Inflação implícita cadastrada sem modelo
- **WHEN** a curva `IPCA_IMPLICITA` é cadastrada com as ligações (`TCEN`, `NUMERADOR`, `DIxPRE`, 1) e (`TCEN`, `DENOMINADOR`, `NTN-B`, 2) e um modelo de construção que ainda não existe
- **THEN** a construção falha com `CADASTRO_INVALIDO` informando o modelo, e nenhuma outra curva é afetada

#### Scenario: Ciclo entre curvas
- **WHEN** a curva `A` tem `B` como mãe, e `B` é cadastrada com `A` como mãe
- **THEN** a construção de qualquer das duas falha com `CADASTRO_INVALIDO`, citando o ciclo `A` → `B` → `A`

### Requirement: Construção por uma origem secundária
Uma curva MAY ter mais de uma ligação de provedor em `tCurvaPrvdr`: a de menor `cPriorCsumo` é a origem principal, e as demais são **origens secundárias**, cujos dados brutos também são gravados pelos feeders (o processor grava os vértices para toda curva ligada ao código, principal ou não). A construção automática (carga e construção da data pelo orquestrador) SHALL usar sempre a origem principal. O usuário SHALL poder construir ou recalcular a curva a partir de uma origem secundária, informando `fonte` e `produto` em `POST .../construcao` (spec `curve-engine-api`), e simular por ela da mesma forma.

Com `fonte` e `produto` informados:
- a origem usada SHALL ser a ligação da curva em `tCurvaPrvdr` com `iPrvdrDados` = `fonte` e `cPrvdrMercd` = `produto`, e `cTickerPrvdr` dela é o código na fonte. Sem nenhuma ligação assim, MUST falhar com `CADASTRO_INVALIDO`, listando as origens cadastradas da curva; com mais de uma, também `CADASTRO_INVALIDO`. A fonte `TCEN` MUST NOT ser informada: as mães de uma curva derivada não são uma origem selecionável;
- o modelo de construção SHALL ser `MODELOS_POR_ORIGEM["{fonte}/{produto}"]` do `cModDado` quando a chave existir, e `cMotorCalc` quando não existir. O modelo escolhido SHALL aceitar a fonte e o produto informados, senão `CADASTRO_INVALIDO`;
- todo o resto do cadastro (unidade, cotação, grandeza, interpolador, calendário, extrapolação, horizonte e arredondamento) SHALL ser o da curva, o mesmo da origem principal: a curva é uma só, e muda só de onde vêm os pontos;
- as regras de gravação, trava, recálculo e situação são as da construção pela API. Com pontos gravados e sem `forcarRecalculo=true`, a resposta é `EXISTENTE`, e a comparação SHALL ser feita contra o que a origem informada produz.

A proveniência, a resposta, a memória de cálculo e o `CURVA_GRAVADA` SHALL informar a origem usada (fonte, produto, código na fonte e prioridade) e o aviso `ORIGEM_SECUNDARIA`. O banco não guarda de qual origem vieram os pontos gravados (o schema não muda): a construção automática, a rota de situação e o arquivo de auditoria comparam sempre com a origem principal, e uma data construída pela secundária aparece como diferente da fonte até ser recalculada pela principal. Quem construiu pela secundária, e quando, está no `CURVA_GRAVADA`.

#### Scenario: Curva reconstruída pela fonte secundária
- **WHEN** a curva `DI_BACKUP` tem origem principal `ANBIMA`/`CZ` (prioridade 1) e secundária `B3`/`TS`/`PRE` (prioridade 2), com `MODELOS_POR_ORIGEM` = `{"B3/TS":"PRONTA_TS_B3"}`, a carga ANBIMA de `2026-09-14` não veio, e o operador chama `POST /api/v1/curvas/DI_BACKUP/2026-09-14/construcao?fonte=B3&produto=TS`
- **THEN** a curva é construída com `PRONTA_TS_B3` a partir das 278 linhas de `tBtrsCurvaPrimr` gravadas sob `DI_BACKUP`, com o interpolador e o arredondamento do cadastro da curva, o aviso `ORIGEM_SECUNDARIA`, e o `CURVA_GRAVADA` traz a origem `B3`/`TS`/`PRE`, prioridade 2

#### Scenario: Volta para a origem principal
- **WHEN** a carga ANBIMA de `2026-09-14` chega depois disso
- **THEN** a carga não reconstrói a `DI_BACKUP`, que vem como `EXISTENTE` com o aviso `PONTOS_DIFERENTES_DA_FONTE`, e o operador recalcula pela principal com `forcarRecalculo=true`, sem `fonte` e `produto`

#### Scenario: Origem não cadastrada
- **WHEN** o operador pede a construção da `PRE` com `fonte=BLOOMBERG&produto=BLC2`, e a `PRE` só tem a origem `B3`/`TS`
- **THEN** a resposta é 422 com `CADASTRO_INVALIDO`, listando as origens cadastradas da `PRE`

#### Scenario: Modelo que não aceita a origem
- **WHEN** a `DI_BACKUP` não tem `MODELOS_POR_ORIGEM`, o `cMotorCalc` é um modelo da ANBIMA, e o operador pede a construção por `B3`/`TS`
- **THEN** a resposta é 422 com `CADASTRO_INVALIDO`, informando que o modelo não aceita `B3`/`TS` e que falta `MODELOS_POR_ORIGEM` para essa origem

### Requirement: Regras de curva no cadastro, regras de metodologia no modelo
Todo comportamento que muda entre curvas que usam o mesmo modelo SHALL vir do cadastro. Uma regra que faz parte da metodologia de um modelo de construção e vale para toda curva que o usa (ex.: o cupom da NTN-B) SHALL ficar no próprio modelo. O pipeline MUST NOT conter regra específica de uma curva. Incluir uma nova curva que usa modelos existentes MUST exigir apenas cadastro.

#### Scenario: Nova curva pronta só por cadastro
- **WHEN** a curva `SLP` é cadastrada com origem `B3`/`TS`/`SLP`, construção `PRONTA_TS_B3` e os demais itens obrigatórios
- **THEN** a curva `SLP` pode ser construída e interpolada sem alteração de código

### Requirement: Unidades dos valores
Os valores de curvas de unidade `TAXA` SHALL estar em percentual ao ano: `13,9` significa 13,9% a.a. Toda conversão para fator SHALL usar a taxa decimal `r = valor / 100`. Curvas `PRECO` SHALL guardar o preço na unidade da fonte (a `PTX` em R$ por US$), e curvas `PONTOS`, os pontos de índice. Essa convenção vale para todas as fontes e para os pontos editados por API.

#### Scenario: Conversão de uma taxa
- **WHEN** um ponto da `PRE` tem valor 13,9 e 252 dias úteis
- **THEN** o fator acumulado é `1,139` (`(1 + 0,139)^(252/252)`)

### Requirement: Contagem de tempo
Para uma data-base `B` e uma data `d`, com o calendário cadastrado da curva:
- `DU(d)` SHALL ser a quantidade de dias úteis no intervalo `(B, d]`: exclui `B`, inclui `d`;
- `DC(d)` SHALL ser `d − B` em dias corridos.

A fração de ano de cada `DayCounter` SHALL ser: `Business252` = `DU/252`, com os dias úteis do ponto ou do prazo definidos no requisito "Dias úteis publicados pela fonte ou informados pelo usuário"; `Actual360` = `DC/360`; `Actual365Fixed` = `DC/365`; `Thirty360` = convenção 30/360 USA (Bond Basis) do QuantLib. O `Business252` SHALL contar dias úteis pelo calendário cadastrado da curva. O mesmo `DayCounter` MAY ser usado de forma diferente como eixo de tempo da interpolação e como convenção da cotação.

#### Scenario: Primeiro dia útil
- **WHEN** a data-base é `2026-09-14` (segunda-feira) e `d` é `2026-09-15`
- **THEN** `DU(d)` = 1 e `DC(d)` = 1

### Requirement: Dias úteis publicados pela fonte ou informados pelo usuário
Os dias úteis de um ponto que vierem da fonte ou do usuário SHALL ser obedecidos sem discussão; o calendário só conta o que ninguém informou. Os dias corridos nunca dependem do calendário: são `d − B`.

**Na construção,** cada modelo de construção SHALL declarar, por ponto, os dias úteis publicados pela fonte, quando ela os publica (campo `diasUteisPublicados` de `PontoConstruido`, spec `curve-extension-models`):

| Modelo | O que a fonte publica por ponto | Data do ponto | Dias úteis do ponto |
|---|---|---|---|
| `PRONTA_TS_B3` | dias corridos (`cDiaCorri`) e dias úteis (`cDiaUtil`) | `B + cDiaCorri` | `cDiaUtil`, obedecido |
| `NTNB_BOOTSTRAP_ANBIMA` | prazo em dias úteis (`vVertcCurva`) | dia 15 do mês a que o prazo leva, ajustado para dia útil (spec do modelo) | `vVertcCurva`, obedecido |
| `SOFR_ZERO_BLOOMBERG` | tenor | pelo tenor, calendário e convenção (spec do modelo) | calendário, na construção |

Quando o modelo publica os dias úteis de um ponto e eles diferem de `DU(d)` pelo calendário cadastrado, ou quando a data do ponto não é dia útil no calendário, a construção MUST NOT falhar por isso: SHALL gravar os dias úteis publicados e trazer o aviso `CALENDARIO_DIVERGENTE`, listando cada ponto com a data, os dias úteis publicados e os calculados, na resposta, no log e na memória de cálculo. O aviso serve para corrigir o calendário; a curva segue a fonte.

**No uso dos pontos gravados** (consulta, interpolação, simulação, comparação com a fonte e curvas mães), os dias úteis de cada ponto, `DUp`, SHALL ser o `cDiaUtil` do ponto em `tDadoVertcCurva`, quando não for nulo (gravado pelo engine na construção ou informado pelo usuário no `services/curves`), e `DU(d)` pelo calendário quando for nulo. Para um prazo que não é ponto, com os pontos mantidos (requisito "Pontos no mesmo prazo do eixo") em ordem de data, os dias úteis SHALL ser ancorados no ponto anterior, e o calendário só conta dentro do trecho:
- entre os pontos `i` e `i+1` (`d_i < d < d_{i+1}`): `DUp_i` + dias úteis do calendário em `(d_i, d]`, no máximo `DUp_{i+1}`;
- antes do primeiro ponto: dias úteis do calendário em `(B, d]`, no máximo `DUp_1`;
- depois do último ponto `n`: `DUp_n` + dias úteis do calendário em `(d_n, d]`.

Um prazo pedido em dias úteis (`du` = `N`) SHALL usar `N` como os dias úteis do prazo. A data correspondente SHALL ser a de um ponto com `DUp` = `N`, se existir; senão, o ponto anterior (ou `B`, antes do primeiro ponto) avançado `N − DUp_i` dias úteis pelo calendário, limitado ao dia anterior ao ponto seguinte. Essa data dá os dias corridos do prazo.

Com eixo e cotação de dias corridos (`Actual360`, `Actual365Fixed`, `Thirty360`), nada disso se aplica: as datas são exatas.

#### Scenario: Feriado que falta no calendário
- **WHEN** a B3 considerou um feriado que não está no calendário `Brazil`, e a `PRE` é construída
- **THEN** a curva é gravada com os dias úteis publicados em `tDadoVertcCurva.cDiaUtil`, os fatores calculados com eles, e a resposta traz `CALENDARIO_DIVERGENTE` com os vértices afetados e as duas contagens

#### Scenario: Vértice consultado pelos dias úteis publicados
- **WHEN** um vértice da `PRE` foi publicado com 100 dias úteis, o calendário conta 101, e o cliente pede `du=100`
- **THEN** a resposta é o valor desse vértice, com classificação `PONTO`

#### Scenario: Data entre vértices com calendário divergente
- **WHEN** entre dois vértices da `PRE` com 100 e 105 dias úteis publicados o calendário conta 2 dias úteis do primeiro até a data pedida
- **THEN** a data pedida tem 102 dias úteis, e o valor é interpolado entre os dois vértices com esse prazo

#### Scenario: Ponto manual com dias úteis informados
- **WHEN** o gestor gravou no `services/curves` um ponto da `PRE` com 40 dias úteis informados, e o calendário conta 41 para a data
- **THEN** a interpolação usa 40 dias úteis para esse ponto

### Requirement: Cotação da taxa
Para taxa decimal `r` e fração de ano `τ` do `DayCounter` da cotação, o fator acumulado `FA` SHALL ser:
- `Simple`: `FA = 1 + r·τ`;
- `Compounded` com frequência `f` (1 para `Annual`, 2 para `Semiannual`...): `FA = (1 + r/f)^(f·τ)`;
- `Continuous`: `FA = e^(r·τ)`.

O fator de desconto SHALL ser `DF = 1/FA`, e a taxa implícita de um fator SHALL ser obtida pela inversa exata da fórmula. Um `FA` menor ou igual a zero MUST resultar em erro, informando o ponto ou o prazo.

#### Scenario: Cotação simples 360 (DCL)
- **WHEN** a `DCL` tem valor 5,000 num prazo de 90 dias corridos, com cotação `Actual360`/`Simple`
- **THEN** `FA` = `1 + 0,05 × 90/360` = 1,0125

### Requirement: Grandeza interpolada
Cada ponto `(d, valor)` SHALL ser convertido em `x = fração de ano de d pelo DayCounter do eixo` e `y` pela grandeza cadastrada:
- `Discount`: `y = DF(d)`, pela cotação cadastrada;
- `CompoundFactor`: `y = FA(d)`, pela cotação cadastrada;
- `ZeroYield`: `y = r`;
- `Price`: `y = valor`.

O valor de um prazo SHALL ser obtido pela conversão inversa do `y` calculado, usando a fração de ano da cotação no próprio prazo. `ForwardRate` não é suportada nesta fase.

#### Scenario: Eixo e cotação diferentes (DCL)
- **WHEN** a `DCL` usa grandeza `Discount`, eixo `Business252` e cotação `Actual360`/`Simple`
- **THEN** `x` de cada ponto é `DU/252`, e `y` é `1/(1 + r·DC/360)`

### Requirement: Interpoladores
Entre dois pontos consecutivos `(x_i, y_i)` e `(x_{i+1}, y_{i+1})`, com `w = (x − x_i)/(x_{i+1} − x_i)`, cada interpolador SHALL calcular:
- `Linear`: `y = y_i + w·(y_{i+1} − y_i)`;
- `LogLinear`: `y = y_i · (y_{i+1}/y_i)^w`; todos os `y` da curva MUST ser positivos, ou a interpolação falha com `PONTOS_NAO_INTERPOLAVEIS`, citando os pontos (pode acontecer com preço ou pontos não positivos gravados à mão no `services/curves`); a consulta dos pontos gravados continua funcionando;
- `BackwardFlat`: `y = y_{i+1}`;
- `ForwardFlat`: `y = y_i`;
- `Cubic`: spline cúbica natural (segunda derivada nula no primeiro e no último ponto) sobre todos os pontos.

Os pontos SHALL ser ordenados por data, e dois pontos com a mesma data MUST NOT existir. Os interpoladores SHALL receber `x` estritamente crescentes (requisito "Pontos no mesmo prazo do eixo"). As funções do Manual de Curvas B3 SHALL ser obtidas só por configuração:

| Função B3 | Grandeza + interpolador | Eixo | Cotação |
|---|---|---|---|
| 1.4.2 Flat Forward 252 | `Discount` + `LogLinear` | `Business252` | `Business252`/`Compounded`/`Annual` |
| 1.4.3 Flat Forward 252 com convenção linear | `Discount` + `LogLinear` | `Business252` | `Actual360`/`Simple` |
| 1.4.4 Interpolação 360 | `Discount` + `LogLinear` | `Actual360` | `Actual360`/`Compounded`/`Annual` |
| 1.4.5 Interpolação de preços | `Price` + `LogLinear` | `Business252` | — |
| 1.4.11 Interpolação 360 linear | `CompoundFactor` + `Linear` | `Actual360` | `Actual360`/`Simple` |

#### Scenario: Flat forward 252 (PRE)
- **WHEN** a `PRE` é interpolada entre dois pontos
- **THEN** o resultado é igual ao da fórmula 1.4.2 do manual, calculada em aritmética decimal

#### Scenario: Linear 360
- **WHEN** uma curva `CompoundFactor` + `Linear`, eixo `Actual360`, cotação `Actual360`/`Simple`, é interpolada entre dois pontos
- **THEN** o resultado é igual ao da fórmula 1.4.11 do manual, que é linear em taxa × prazo e não na taxa

### Requirement: Políticas de extrapolação
A política de início SHALL valer para prazos antes do primeiro ponto, e a de fim, para prazos depois do último:
- `Disabled`: o prazo MUST resultar em erro `PRAZO_FORA_DO_DOMINIO`.
- `FlatForward`: aplica a fórmula do interpolador ao segmento adjacente com `w` fora de `[0, 1]`. No fim, usa o penúltimo e o último ponto (manual 1.4.6 e 1.4.10); no início, o primeiro e o segundo (manual 1.4.7). Exige pelo menos 2 pontos.
- `FlatValue`: repete o **valor** do ponto adjacente (taxa, preço ou pontos, não a grandeza) e converte esse valor na grandeza usando a fração de ano do próprio prazo (manual 1.4.8 e 1.4.9).

#### Scenario: Flat forward no fim com convenção linear (DCL)
- **WHEN** a `DCL` é extrapolada no fim com `FlatForward`
- **THEN** o resultado é igual ao da fórmula 1.4.10 do manual

#### Scenario: Flat value no fim (INP)
- **WHEN** a `INP` é extrapolada no fim com `FlatValue`
- **THEN** todo prazo após o último ponto tem os pontos de índice do último ponto

#### Scenario: Flat value de taxa
- **WHEN** uma curva de taxa com último ponto em 12,000 é extrapolada no fim com `FlatValue`
- **THEN** o valor devolvido é 12,000 em todo prazo extrapolado, e os fatores são calculados com 12,000 no prazo pedido

### Requirement: Domínio da interpolação
Para a data-base `B`, o domínio SHALL ir de `B + 1 dia útil` até `fim = max(data do último ponto, B + HORIZONTE)`. `B + HORIZONTE` SHALL ser somado em datas corridas, sem ajuste de dia útil (`M` e `Y` somam meses e anos, levando ao último dia do mês quando o dia não existe). Dentro do domínio:
- prazo igual à data de um ponto: devolve o valor do ponto (classificação `PONTO`);
- prazo entre o primeiro e o último ponto: interpolado (`INTERPOLADO`);
- prazo antes do primeiro ponto: política de início (`EXTRAPOLADO_INICIO`);
- prazo depois do último ponto e até `fim`: política de fim (`EXTRAPOLADO_FIM`).

Prazo fora do domínio MUST resultar em `PRAZO_FORA_DO_DOMINIO`, informando o prazo e os limites do domínio. Uma curva com um único ponto SHALL aceitar só o prazo do ponto e prazos cobertos por `FlatValue`.

#### Scenario: Horizonte menor que o último ponto
- **WHEN** a `PRE` de `2026-09-14` tem o último ponto em `2060-08-16`, o horizonte é `10Y`, e é pedido o prazo de `2045-01-02`
- **THEN** o valor é interpolado, porque o domínio vai até o último ponto

#### Scenario: Prazo além do domínio
- **WHEN** uma curva tem o último ponto a 5 anos da data-base, horizonte `10Y`, extrapolação de fim `FlatForward`, e é pedido um prazo a 12 anos
- **THEN** a consulta falha com `PRAZO_FORA_DO_DOMINIO`, informando o prazo e o fim do domínio

### Requirement: Arredondamento e precisão
Todo cálculo MUST usar `BigDecimal` com `MathContext.DECIMAL128`, sem passar por `double`, e sem arredondamento intermediário. O arredondamento cadastrado (`CASAS_DECIMAIS` + `MODO_ARREDONDAMENTO`) SHALL ser aplicado apenas ao valor da curva (taxa, preço ou pontos), em dois momentos: ao gravar um ponto e ao devolver um valor. A interpolação SHALL usar como entrada os valores gravados (já arredondados). Os fatores SHALL ser calculados a partir do valor já arredondado devolvido e SHALL ser devolvidos com 16 casas decimais, `HALF_UP`, sem usar o arredondamento cadastrado.

#### Scenario: Curva truncada
- **WHEN** a `PTX` tem 7 casas com `DOWN` e o valor calculado é 5,43219876
- **THEN** o valor devolvido é 5,4321987

#### Scenario: Ponto gravado arredondado
- **WHEN** o modelo da `NTN-B` calcula a taxa zero 5,123456789 para um título, com 8 casas `HALF_UP`
- **THEN** o ponto gravado vale 5,12345679

### Requirement: Fatores só para curvas de taxa
Para unidade `TAXA`, cada valor devolvido SHALL trazer o fator acumulado `FA` (cotação cadastrada, de `B` até o prazo) e o fator diário médio `FA^(1/DU)`. Curvas `PRECO` e `PONTOS` MUST NOT trazer fatores.

#### Scenario: Curva de pontos
- **WHEN** a `INP` é consultada
- **THEN** os valores trazem só os pontos de índice, sem fatores

### Requirement: Datas e horários de Brasília
Datas-base, datas de ponto e prazos SHALL ser datas puras, sem hora nem fuso. Todo "hoje" e todo instante SHALL usar explicitamente o fuso `America/Sao_Paulo`, sem depender do fuso padrão da JVM ou do servidor. Instantes em respostas, planilhas, auditoria, `estado.json` e logs SHALL ser gravados em ISO-8601 com o deslocamento (ex.: `2026-09-14T21:30:00.000-03:00`), e nomes de arquivo com carimbo de tempo SHALL usar o horário de Brasília.

#### Scenario: Servidor em UTC perto da meia-noite
- **WHEN** o engine roda com a JVM em UTC e uma construção é concluída às 22h30 de Brasília (01h30 UTC do dia seguinte)
- **THEN** o instante registrado é `...T22:30:00...-03:00`, com a data de Brasília

### Requirement: Construção grava a curva construída e a curva interpolada
A curva tem duas formas gravadas, na mesma data-base:
- **curva construída**, em `tDadoVertcCurva`: os **pontos** (vértices) que o modelo de construção gera, com o detalhe do cálculo;
- **curva interpolada**, em `tDadoCurva`: um valor por dia corrido, calculado a partir dos pontos pelo interpolador e pelas extrapolações cadastrados.

Construir uma curva numa data-base SHALL ler os dados brutos gravados da origem (na construção pela carga, conferindo a quantidade lida contra a avisada, spec `curve-load-trigger`), executar o modelo de construção cadastrado, arredondar cada ponto e gravar, na mesma transação:
- em `tDadoVertcCurva`, uma linha por ponto: `dBaseReft` = data-base, `cTickerIndcd` = nome da curva, `dVertcReft` = data do ponto, `vPrecoTx` = valor arredondado; `cDiaUtil` = dias úteis do ponto: os publicados pela fonte, quando o modelo os declara, senão `DU(d)` pelo calendário; `cQtdDiaPer` = `DC(d)`, dias corridos da data-base ao ponto; `cQtdDiaReft` = dias da data-base ao ponto na convenção 30/360 (`Thirty360`); e, só para unidade `TAXA`, `vFatorAcum` = fator acumulado até o ponto e `vFatorDia` = fator diário médio `FA^(1/DU)`, com 16 casas `HALF_UP` (nulos para `PRECO` e `PONTOS`);
- em `tDadoCurva`, a curva interpolada, conforme o requisito "Curva interpolada gravada".

Os pontos, os dias úteis de cada ponto e o `hashPontos` MUST vir de `tDadoVertcCurva`; `tDadoCurva` é saída para quem consome a curva e MUST NOT ser lida para construir, consultar pontos ou interpolar. Além disso, SHALL ser gravadas só as colunas `dBaseReft` e `cUsuarCalc` de `tCurvaMercd`, e emitido o log `CURVA_GRAVADA`, conforme a spec `curve-audit-history`; nada é gravado em `tMtrizCurva`, e o schema não é alterado. No recálculo, as linhas da data nas duas tabelas SHALL ser apagadas e regravadas juntas. A leitura do cadastro, a execução do modelo, a remoção das linhas anteriores (no recálculo) e a gravação SHALL ocorrer numa única transação, que trava a linha da curva em `tCurvaMercd` até o fim. Uma construção da mesma curva que não obtiver a trava em 30 segundos MUST falhar com `CONSTRUCAO_EM_ANDAMENTO`. A edição manual de pontos no `services/curves` usa a mesma trava (change `curves-cadastro-curvas`), de modo que construção e edição nunca se misturam.

#### Scenario: Construção da PRE
- **WHEN** a `PRE` de `2026-09-14` é construída
- **THEN** `tDadoVertcCurva` tem 278 linhas para `DIxPRE` em `2026-09-14`, a primeira com `cDiaUtil` = 1, `cQtdDiaPer` = 1, o fator acumulado e o diário; e `tDadoCurva` tem 12.390 linhas, uma por dia corrido de `2026-09-15` a `2060-08-16`

#### Scenario: Ponto sem dias úteis gravados
- **WHEN** a `PRE` de uma data é interpolada, e um dos pontos foi incluído à mão sem dias úteis informados
- **THEN** os dias úteis desse ponto vêm do calendário, e os dos demais, de `tDadoVertcCurva.cDiaUtil`

### Requirement: Curva interpolada gravada
A curva interpolada SHALL ter, em `tDadoCurva`, uma linha para cada dia corrido `d` da grade: `dBaseReft` = data-base, `cTickerIndcd` = nome da curva, `dVertcReft` = `d`, `vPrecoTx` = valor da curva em `d`, arredondado pelo cadastro. A grade SHALL ir:
- do início: `B + 1 dia útil` (início do domínio), ou a data do primeiro ponto quando a extrapolação de início é `Disabled`;
- até o fim: o fim do domínio (requisito "Domínio da interpolação"), ou a data do último ponto quando a extrapolação de fim é `Disabled`.

Todo dia corrido da grade SHALL ter valor, inclusive sábado, domingo e feriado: com eixo em dias úteis, o dia não útil tem os dias úteis do último dia útil anterior (contagem `(B, d]` ancorada no ponto anterior) e, por isso, repete o valor dele; com eixo em dias corridos, cada dia tem o seu valor interpolado. O valor de cada dia SHALL ser exatamente o que a rota de interpolação devolve para a mesma data, com os mesmos descartes do requisito "Pontos no mesmo prazo do eixo". Fatores não são gravados na curva interpolada.

A curva interpolada SHALL ser regravada inteira, na mesma transação travada, sempre que os pontos mudarem: na construção, no recálculo e, depois de uma edição manual no `services/curves`, pela rota de regravação da interpolada (spec `curve-engine-api`). Se a interpolação de algum dia da grade falhar (ex.: `PONTOS_NAO_INTERPOLAVEIS`), a gravação inteira MUST ser desfeita, com o erro.

#### Scenario: Fim de semana na curva interpolada
- **WHEN** a `PRE` de `2026-09-14` é gravada
- **THEN** `tDadoCurva` tem linhas em `2026-09-19` e `2026-09-20` (sábado e domingo), com o mesmo valor de `2026-09-18` (sexta-feira)

#### Scenario: Extrapolação de fim desligada
- **WHEN** uma curva com extrapolação de fim `Disabled`, último ponto a 5 anos da data-base e horizonte `10Y` é gravada
- **THEN** a curva interpolada termina na data do último ponto, e não em data-base + 10 anos

#### Scenario: Mesmo valor pela API e pela tabela
- **WHEN** o cliente interpola a `DCL` de `2026-09-14` em `data=2027-03-10`
- **THEN** o valor devolvido é igual ao `vPrecoTx` da linha de `2027-03-10` em `tDadoCurva`

### Requirement: Leitura consistente durante gravações
Todas as leituras SHALL usar o nível `READ COMMITTED` do SQL Server; `READ UNCOMMITTED`, `NOLOCK` e equivalentes MUST NOT ser usados. Uma consulta, interpolação ou simulação feita durante uma construção, reconstrução ou edição da mesma curva e data SHALL ver os pontos anteriores inteiros ou os novos inteiros, nunca a data vazia ou parcial. Sem `READ_COMMITTED_SNAPSHOT` no banco, a leitura pode esperar o commit da gravação, limitada ao tempo limite de comando da spec `curve-engine-resilience`.

#### Scenario: Consulta durante a reconstrução
- **WHEN** a `PRE` de `2026-09-14` está sendo reconstruída e, entre o apagar e o inserir, chega uma consulta da mesma curva e data
- **THEN** a consulta espera o commit e devolve os 278 pontos novos, nunca uma lista vazia ou parcial

### Requirement: Insumo ausente ou inválido interrompe a construção
Quando a origem não tiver dados para a data, a construção MUST falhar com `INSUMO_AUSENTE`, informando o código da curva, a fonte, o código na fonte e a data. Quando um dado lido violar uma regra do modelo de construção, a construção MUST falhar com `INSUMO_INVALIDO`, informando a linha e a regra. Cada arquivo de origem tem as suas regras: a spec de cada modelo SHALL trazer a tabela "Regras do arquivo", com cada situação do dado lido e o resultado (constrói, constrói com aviso, descarta a linha com aviso, ou falha), e o pipeline MUST NOT ter regra de insumo própria além de `INSUMO_AUSENTE`. Um modelo novo, nativo ou Groovy, só entra com a sua tabela. O engine MUST NOT estimar, repetir dados de outra data ou gravar curva parcial. Um descarte de linha só é permitido quando a spec do modelo o prevê, e SHALL ser registrado no log e na memória de cálculo.

#### Scenario: Data sem insumo
- **WHEN** a construção da `DCL` é pedida para uma data sem linhas do código `DCL` em `tBtrsCurvaPrimr`
- **THEN** a construção falha com `INSUMO_AUSENTE` informando `DCL`, `B3`, `DCL` e a data, e nada é gravado

### Requirement: Reconstrução da mesma data
Construir uma curva e data que já tem pontos gravados, sem recálculo, SHALL devolver a situação `EXISTENTE` sem gravar nada: o modelo roda só como na simulação, para comparar os pontos gravados com os que a fonte atual produz e trazer o aviso `PONTOS_DIFERENTES_DA_FONTE` quando diferirem (spec `curve-load-trigger`). Com recálculo, SHALL apagar e regravar os pontos da data na mesma transação, com situação `RECONSTRUIDA`. Uma primeira construção tem situação `CONSTRUIDA`. Nenhuma versão anterior é mantida.

#### Scenario: Pedido repetido sem recálculo
- **WHEN** a construção de `PRE` em `2026-09-14` é pedida de novo sem recálculo
- **THEN** a resposta tem situação `EXISTENTE`, nenhuma linha é apagada ou gravada, e não há aviso se a fonte atual produz os mesmos pontos

### Requirement: Pontos no mesmo prazo do eixo
Com o eixo `Business252`, um ponto gravado em dia não útil tem o mesmo `DU` do dia útil anterior (contagem `(B, d]`), dias úteis informados à mão podem repetir ou até diminuir de um ponto para o seguinte, e dois pontos com o mesmo `x` levariam a uma divisão por zero em `w`. Pontos assim, e pontos na data-base ou antes dela, só entram por edição manual no `services/curves`, que os grava com aviso. A base comum de interpolação, por onde passam todos os interpoladores (nativos e Groovy, locais e `Cubic`), SHALL tratar isso antes de chamar o interpolador:
- um ponto com `x` menor ou igual a zero (data igual ou anterior à data-base, ou dia não útil logo depois dela) SHALL ser descartado da interpolação, com o aviso `PONTO_DESCARTADO_PRAZO_NAO_POSITIVO`;
- um ponto em dia não útil sem outro ponto no mesmo `x` SHALL ser usado normalmente, no `x` do seu `DU`;
- quando dois ou mais pontos têm o mesmo `x`, SHALL ficar só o de menor data, que é o dia útil quando ele existe; os demais SHALL ser descartados da interpolação;
- em ordem de data, um ponto com `x` menor que o de um ponto de data anterior já mantido SHALL ser descartado, com o mesmo aviso, porque o eixo tem de crescer com a data;
- cada ponto descartado por mesmo prazo SHALL gerar o aviso `PONTO_DESCARTADO_MESMO_PRAZO` (data descartada, data mantida, `x`) na resposta da consulta, da interpolação, da construção e da simulação, no log e na memória de cálculo, onde o ponto aparece marcado como descartado.

Os pontos gravados em `tDadoVertcCurva` MUST NOT ser alterados por esse tratamento: consultar os pontos devolve todos os gravados, com o aviso. Os avisos de descarte SHALL aparecer nas mesmas saídas. Com eixo de dias corridos (`Actual360`, `Actual365Fixed`, `Thirty360`), datas diferentes têm `x` diferentes e só o descarte de prazo não positivo se aplica. Se, depois dos descartes, não sobrar nenhum ponto, a consulta MUST falhar com `CURVA_NAO_CONSTRUIDA`.

#### Scenario: Ponto manual em feriado junto do dia útil anterior
- **WHEN** a `PRE` de `2026-12-21` tem pontos gravados em `2026-12-24` (quinta-feira) e em `2026-12-25` (feriado), ambos com `DU` = 3
- **THEN** a interpolação usa o ponto de `2026-12-24`, descarta o de `2026-12-25` e traz o aviso `PONTO_DESCARTADO_MESMO_PRAZO` com as duas datas

#### Scenario: Ponto manual em feriado sozinho
- **WHEN** a `PRE` de `2026-12-21` tem ponto gravado em `2026-12-25` e nenhum em `2026-12-24`
- **THEN** o ponto é usado com `DU` = 3, sem descarte e sem aviso

### Requirement: Interpolação sob demanda a partir dos pontos gravados
Consultar e interpolar SHALL ler os pontos gravados em `tDadoVertcCurva` e montar a curva a cada chamada, com o cadastro vigente na data-base. O engine MUST NOT manter cache de curva nesta fase. Se não houver pontos gravados na data, a consulta MUST falhar com `CURVA_NAO_CONSTRUIDA`. Os pontos podem ter sido gravados à mão pelo `services/curves`, inclusive em dia não útil; o tratamento é o do requisito "Pontos no mesmo prazo do eixo".

#### Scenario: Curva não construída
- **WHEN** a interpolação da `DPL` é pedida para uma data sem pontos gravados
- **THEN** a consulta falha com `CURVA_NAO_CONSTRUIDA` informando `DPL` e a data

### Requirement: Proveniência e hash dos pontos
Toda resposta de construção SHALL informar o modelo de construção, o interpolador e o calendário usados, cada um com nome, origem (`JAVA` ou `GROOVY`) e, para Groovy, versão e hash do script, a versão do engine (versão do artefato e commit, fixada no build), o `estadoScript` (`ATUAL`, `DESATUALIZADO` ou `DESCONHECIDO`, spec `curve-engine-resilience`), os avisos da construção (ex.: `PONTOS_DIFERENTES_DA_FONTE`, `CURVA_INATIVA`) e o `hashPontos`: SHA-256, em hexadecimal minúsculo, do texto formado pelas linhas `AAAA-MM-DD;valor` de cada ponto gravado em `tDadoVertcCurva` (`dVertcReft` e `vPrecoTx`), em ordem de data, separadas por `\n`, sem `\n` no fim. O valor SHALL ser escrito na forma canônica, independente da escala com que foi lido do banco (`DECIMAL(28,12)` devolve 12 casas): sem zeros à direita, sem expoente, com ponto decimal, sem ponto quando inteiro e `0` para zero (em Java, `stripTrailingZeros().toPlainString()`, com `0` para zero). Ex.: `13.9000000` e `13.900000000000` são escritos `13.9`; `-117.9600000`, `-117.96`. O mesmo vetor de teste SHALL ser usado pelo engine e pelo `services/curves`: os pontos `2026-09-15` = `13.9000000` e `2026-09-16` = `-117.9600000` formam o texto `2026-09-15;13.9\n2026-09-16;-117.96`. As respostas de consulta e de interpolação SHALL informar o interpolador e o calendário da mesma forma, e o `hashPontos` dos pontos lidos.

#### Scenario: Interpolador sobrescrito por Groovy
- **WHEN** a interpolação é pedida enquanto um script Groovy ativo sobrescreve `LogLinear`
- **THEN** a resposta informa `LogLinear` com origem `GROOVY`, a versão e o hash do script

#### Scenario: Pontos iguais, hash igual
- **WHEN** a mesma curva e data é reconstruída sem mudança de insumo, cadastro ou modelo
- **THEN** o `hashPontos` da reconstrução é igual ao da construção anterior

### Requirement: Log estruturado da construção
O engine SHALL registrar em log estruturado (JSON), com `correlationId`, código, nome e data-base, os eventos:
- `CONSTRUCAO_CONCLUIDA`: situação, modelos com origem, versão e hash, cadastro vigente (todos os itens), quantidade de pontos, `hashPontos`, duração em milissegundos;
- `CONSTRUCAO_FALHOU`: código de erro e mensagem;
- `INSUMO_DESCARTADO`: linha e motivo;
- `SIMULACAO_EXECUTADA`: status e `hashPontos`.

#### Scenario: Construção rastreável
- **WHEN** a `PRE` de `2026-09-14` é construída
- **THEN** o log tem um evento `CONSTRUCAO_CONCLUIDA` com os modelos, o cadastro vigente e o `hashPontos`, que permite distinguir os pontos construídos de pontos editados depois no `services/curves`

### Requirement: Determinismo
Com os mesmos insumos, o mesmo cadastro e as mesmas versões de modelo, a construção MUST gravar exatamente os mesmos pontos, e a interpolação MUST devolver exatamente os mesmos valores.

#### Scenario: Duas interpolações idênticas
- **WHEN** o mesmo prazo da mesma curva e data é interpolado duas vezes, sem mudança de pontos, cadastro ou modelo
- **THEN** os dois valores devolvidos são idênticos
