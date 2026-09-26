## Purpose

Constrói e grava os pontos de uma curva numa data a partir do cadastro da curva, e interpola esses pontos sob demanda. Define de forma fechada as unidades, as contagens de tempo, as fórmulas de cotação, interpolação e extrapolação, o domínio, o arredondamento e os erros, para que a mesma entrada produza sempre a mesma saída em qualquer implementação.

## ADDED Requirements

### Requirement: Cadastro da curva e itens obrigatórios
O engine SHALL montar o cadastro de uma curva na data-base a partir das tabelas abaixo, e de nenhuma outra fonte:
- `tCurvaMercd`: `cTickerIdtfdUnic` = **código** (rotas por código); `cTickerIndcd` = **nome de exibição** (chave de todas as FKs, inclusive de `tDadoCurva`); `cTpoVlr` = **unidade** (`TAXA`, `PRECO` ou `PONTOS`); `cNormaDia` = `DayCounter` da cotação; `cTpoJuro` = `Compounding` da cotação.
- `tCurvaPrvdr`: a linha da curva com o **menor** `cPriorCsumo` é a **origem**: `iPrvdrDados` = fonte (ex.: `B3`), `cPrvdrMercd` = produto (ex.: `TS`), `cTickerPrvdr` = código na fonte (ex.: `PRE`). As demais linhas são ignoradas nesta fase: não há troca automática de fonte.
- `tConfgCurva`: a linha **vigente** na data-base, isto é, com `cTickerIndcd` = nome, `dInicVgcia <= dataBase` e (`dValidAte` nulo ou `dValidAte >= dataBase`). `cMotorCalc` = modelo de construção; `cRotnaCalc` = interpolador.
- `tConfgCurva.cModDado` da configuração vigente: um objeto JSON (até 1024 caracteres) com os parâmetros da tabela abaixo, cada um como string ou número. Ex.: `{"GRANDEZA":"Discount","CASAS_DECIMAIS":7,...}`. `tParmConfgCurva` não é lida nesta fase.

| Chave | Tipo JSON | Obrigatória | Valores aceitos |
|---|---|---|---|
| `GRANDEZA` | string | sim | `Discount`, `CompoundFactor`, `ZeroYield`, `Price` |
| `DAY_COUNTER_TEMPO` | string | sim | `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` |
| `FREQUENCY` | string | só se `cTpoJuro` = `Compounded` | nomes de `Frequency` do QuantLib (ex.: `Annual`) |
| `CALENDARIO` | string | sim | `Brazil`, `UnitedStates` ou nome de calendário Groovy |
| `MERCADO_CALENDARIO` | string | sim | ex.: `Settlement`, `FederalReserve` |
| `BUSINESS_DAY_CONVENTION` | string | sim | nomes de `BusinessDayConvention` do QuantLib |
| `EXTRAPOLACAO_INICIO` | string | não (padrão `Disabled`) | `Disabled`, `FlatForward`, `FlatValue` |
| `EXTRAPOLACAO_FIM` | string | não (padrão `Disabled`) | `Disabled`, `FlatForward`, `FlatValue` |
| `HORIZONTE` | string | sim | `Period` do QuantLib: inteiro positivo + `D`, `W`, `M` ou `Y` (ex.: `10Y`) |
| `CASAS_DECIMAIS` | número | sim | inteiro de 0 a 12 |
| `MODO_ARREDONDAMENTO` | string | sim | `HALF_UP`, `HALF_EVEN`, `DOWN` (`DOWN` = truncamento) |
| `VERSAO_SCRIPT_CONSTRUCAO`, `VERSAO_SCRIPT_INTERPOLACAO`, `VERSAO_SCRIPT_CALENDARIO` | número | não | versão validada do script Groovy |

Para unidade `TAXA`, `cNormaDia` e `cTpoJuro` SHALL ser obrigatórios; para `PRECO` e `PONTOS`, SHALL ser ignorados. O cadastro MUST ser rejeitado com erro `CADASTRO_INVALIDO`, informando o código, o item e o motivo, quando:
- faltar um item obrigatório;
- um valor não estiver entre os aceitos;
- `cModDado` não for um objeto JSON válido, ou tiver chave desconhecida ou valor de tipo diferente do da tabela;
- não houver linha vigente em `tConfgCurva`, ou houver mais de uma;
- `GRANDEZA` = `Price` com unidade `TAXA`, ou `GRANDEZA` diferente de `Price` com unidade `PRECO` ou `PONTOS`;
- `cTpoJuro` for `SimpleThenCompounded` ou `CompoundedThenSimple` (não suportados nesta fase);
- a política `FlatForward` for usada com interpolador que não seja `Linear` ou `LogLinear`;
- a fonte ou o produto da origem não forem os esperados pelo modelo de construção cadastrado.

Nenhum valor padrão SHALL ser usado além dos dois marcados na tabela.

#### Scenario: Cadastro completo da PRE
- **WHEN** a curva `PRE` tem origem `B3`/`TS`/`PRE`, construção `PRONTA_TS_B3`, interpolador `LogLinear`, unidade `TAXA`, `cNormaDia` = `Business252`, `cTpoJuro` = `Compounded` e os parâmetros `GRANDEZA` = `Discount`, `DAY_COUNTER_TEMPO` = `Business252`, `FREQUENCY` = `Annual`, `CALENDARIO` = `Brazil`, `MERCADO_CALENDARIO` = `Settlement`, `BUSINESS_DAY_CONVENTION` = `Following`, `EXTRAPOLACAO_FIM` = `FlatForward`, `HORIZONTE` = `10Y`, `CASAS_DECIMAIS` = 7, `MODO_ARREDONDAMENTO` = `HALF_UP`
- **THEN** a curva pode ser construída e interpolada sem nenhum parâmetro vindo do chamador, com extrapolação de início `Disabled`

#### Scenario: Item obrigatório ausente
- **WHEN** a `DCL` não tem `cRotnaCalc` na configuração vigente
- **THEN** a construção falha com `CADASTRO_INVALIDO` informando `DCL` e o item interpolador, e nada é gravado

#### Scenario: Chave desconhecida
- **WHEN** a configuração vigente da `PRE` tem a chave `EXTRAPOLACAO_FINAL`
- **THEN** a construção falha com `CADASTRO_INVALIDO` informando a chave `EXTRAPOLACAO_FINAL`

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

A fração de ano de cada `DayCounter` SHALL ser: `Business252` = `DU/252`; `Actual360` = `DC/360`; `Actual365Fixed` = `DC/365`; `Thirty360` = convenção 30/360 USA (Bond Basis) do QuantLib. O `Business252` SHALL contar dias úteis pelo calendário cadastrado da curva. O mesmo `DayCounter` MAY ser usado de forma diferente como eixo de tempo da interpolação e como convenção da cotação.

#### Scenario: Primeiro dia útil
- **WHEN** a data-base é `2026-09-14` (segunda-feira) e `d` é `2026-09-15`
- **THEN** `DU(d)` = 1 e `DC(d)` = 1

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
- `LogLinear`: `y = y_i · (y_{i+1}/y_i)^w`; todos os `y` da curva MUST ser positivos, ou o cadastro é rejeitado na consulta com `CADASTRO_INVALIDO`;
- `BackwardFlat`: `y = y_{i+1}`;
- `ForwardFlat`: `y = y_i`;
- `Cubic`: spline cúbica natural (segunda derivada nula no primeiro e no último ponto) sobre todos os pontos.

Os pontos SHALL ser ordenados por data, e dois pontos com a mesma data MUST NOT existir. As funções do Manual de Curvas B3 SHALL ser obtidas só por configuração:

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
Datas-base, datas de ponto e prazos SHALL ser datas puras, sem hora nem fuso. Todo "hoje" e todo instante SHALL usar explicitamente o fuso `America/Sao_Paulo`, sem depender do fuso padrão da JVM ou do servidor. Instantes em respostas, planilhas, auditoria, registros de carga, `estado.json` e logs SHALL ser gravados em ISO-8601 com o deslocamento (ex.: `2026-09-14T21:30:00.000-03:00`), e nomes de arquivo com carimbo de tempo SHALL usar o horário de Brasília.

#### Scenario: Servidor em UTC perto da meia-noite
- **WHEN** o engine roda com a JVM em UTC e uma construção é concluída às 22h30 de Brasília (01h30 UTC do dia seguinte)
- **THEN** o instante registrado é `...T22:30:00...-03:00`, com a data de Brasília

### Requirement: Construção grava apenas os pontos
Construir uma curva numa data-base SHALL exigir a carga concluída e conferir a quantidade lida (spec `curve-load-trigger`), executar o modelo de construção cadastrado, arredondar cada ponto e gravar em `tDadoCurva` uma linha por ponto: `dBaseReft` = data-base, `cTickerIndcd` = nome da curva, `dVertcReft` = data do ponto, `vPrecoTx` = valor arredondado. Além dos pontos, SHALL ser gravados só a auditoria no Blob (antes do commit) e as colunas `dBaseReft` e `cUsuarCalc` de `tCurvaMercd`, conforme a spec `curve-audit-history`; nada é gravado em `tCurvaData`, `tDadoVertcCurva` ou `tMtrizCurva`, e o schema não é alterado. A leitura do cadastro, a execução do modelo, a remoção dos pontos anteriores (no recálculo) e a gravação SHALL ocorrer numa única transação, que trava a linha da curva em `tCurvaMercd` até o fim. Uma construção ou edição da mesma curva que não obtiver a trava em 30 segundos MUST falhar com `CONSTRUCAO_EM_ANDAMENTO`.

#### Scenario: Construção da PRE
- **WHEN** a `PRE` de `2026-09-14` é construída
- **THEN** `tDadoCurva` tem 278 linhas para `DIxPRE` em `2026-09-14`, e nenhuma linha é gravada nas outras três tabelas

### Requirement: Leitura consistente durante gravações
Todas as leituras SHALL usar o nível `READ COMMITTED` do SQL Server; `READ UNCOMMITTED`, `NOLOCK` e equivalentes MUST NOT ser usados. Uma consulta, interpolação ou simulação feita durante uma construção, reconstrução ou edição da mesma curva e data SHALL ver os pontos anteriores inteiros ou os novos inteiros, nunca a data vazia ou parcial. Sem `READ_COMMITTED_SNAPSHOT` no banco, a leitura pode esperar o commit da gravação, limitada ao tempo limite de comando da spec `curve-engine-resilience`.

#### Scenario: Consulta durante a reconstrução
- **WHEN** a `PRE` de `2026-09-14` está sendo reconstruída e, entre o apagar e o inserir, chega uma consulta da mesma curva e data
- **THEN** a consulta espera o commit e devolve os 278 pontos novos, nunca uma lista vazia ou parcial

### Requirement: Insumo ausente ou inválido interrompe a construção
Quando a origem não tiver dados para a data, a construção MUST falhar com `INSUMO_AUSENTE`, informando o código da curva, a fonte, o código na fonte e a data. Quando um dado lido violar uma regra do modelo de construção, a construção MUST falhar com `INSUMO_INVALIDO`, informando a linha e a regra. O engine MUST NOT estimar, repetir dados de outra data ou gravar curva parcial. Um descarte de linha só é permitido quando a spec do modelo o prevê, e SHALL ser registrado no log e na memória de cálculo.

#### Scenario: Data sem insumo
- **WHEN** a construção da `DCL` é pedida para uma data sem linhas do código `DCL` em `tBtrsCurvaPrimr`
- **THEN** a construção falha com `INSUMO_AUSENTE` informando `DCL`, `B3`, `DCL` e a data, e nada é gravado

### Requirement: Reconstrução da mesma data
Construir uma curva e data que já tem pontos gravados, sem recálculo, SHALL devolver a situação `EXISTENTE` e os pontos gravados, sem executar o modelo. Com recálculo, SHALL apagar e regravar os pontos da data na mesma transação, com situação `RECONSTRUIDA`. Uma primeira construção tem situação `CONSTRUIDA`. Nenhuma versão anterior é mantida.

#### Scenario: Pedido repetido sem recálculo
- **WHEN** a construção de `PRE` em `2026-09-14` é pedida de novo sem recálculo
- **THEN** a resposta tem situação `EXISTENTE` e o modelo de construção não é executado

### Requirement: Interpolação sob demanda a partir dos pontos gravados
Consultar e interpolar SHALL ler os pontos gravados em `tDadoCurva` e montar a curva a cada chamada, com o cadastro vigente na data-base. O engine MUST NOT manter cache de curva nesta fase. Se não houver pontos gravados na data, a consulta MUST falhar com `CURVA_NAO_CONSTRUIDA`.

#### Scenario: Curva não construída
- **WHEN** a interpolação da `DPL` é pedida para uma data sem pontos gravados
- **THEN** a consulta falha com `CURVA_NAO_CONSTRUIDA` informando `DPL` e a data

### Requirement: Proveniência e hash dos pontos
Toda resposta de construção SHALL informar o modelo de construção, o interpolador e o calendário usados, cada um com nome, origem (`JAVA` ou `GROOVY`) e, para Groovy, versão e hash do script, a versão do engine (versão do artefato e commit, fixada no build), o `estadoScript` (`ATUAL`, `DESATUALIZADO` ou `DESCONHECIDO`, spec `curve-engine-resilience`), os avisos da construção (ex.: `CARGA_NAO_VERIFICADA`, `PONTOS_DE_CARGA_ANTERIOR`) e o `hashPontos`: SHA-256, em hexadecimal minúsculo, do texto formado pelas linhas `AAAA-MM-DD;valor` de cada ponto gravado, em ordem de data, com o valor arredondado em notação simples (sem expoente) e ponto como separador decimal, separadas por `\n`. As respostas de consulta e de interpolação SHALL informar o interpolador e o calendário da mesma forma, e o `hashPontos` dos pontos lidos.

#### Scenario: Interpolador sobrescrito por Groovy
- **WHEN** a interpolação é pedida enquanto um script Groovy ativo sobrescreve `LogLinear`
- **THEN** a resposta informa `LogLinear` com origem `GROOVY`, a versão e o hash do script

#### Scenario: Pontos iguais, hash igual
- **WHEN** a mesma curva e data é reconstruída sem mudança de insumo, cadastro ou modelo
- **THEN** o `hashPontos` da reconstrução é igual ao da construção anterior

### Requirement: Log estruturado da construção e da edição
O engine SHALL registrar em log estruturado (JSON), com `correlationId`, código, nome e data-base, os eventos:
- `CONSTRUCAO_CONCLUIDA`: situação, modelos com origem, versão e hash, cadastro vigente (todos os itens), quantidade de pontos, `hashPontos`, duração em milissegundos;
- `CONSTRUCAO_FALHOU`: código de erro e mensagem;
- `INSUMO_DESCARTADO`: linha e motivo;
- `PONTOS_EDITADOS`: usuário, quantidade de pontos antes e depois, `hashPontos` antes e depois;
- `SIMULACAO_EXECUTADA`: status e `hashPontos`.

#### Scenario: Edição rastreável
- **WHEN** os pontos da `PRE` de `2026-09-14` são editados pela API
- **THEN** o log tem um evento `PONTOS_EDITADOS` com o usuário e os dois `hashPontos`, que permite distinguir os pontos editados dos pontos construídos

### Requirement: Determinismo
Com os mesmos insumos, o mesmo cadastro e as mesmas versões de modelo, a construção MUST gravar exatamente os mesmos pontos, e a interpolação MUST devolver exatamente os mesmos valores.

#### Scenario: Duas interpolações idênticas
- **WHEN** o mesmo prazo da mesma curva e data é interpolado duas vezes, sem mudança de pontos, cadastro ou modelo
- **THEN** os dois valores devolvidos são idênticos
