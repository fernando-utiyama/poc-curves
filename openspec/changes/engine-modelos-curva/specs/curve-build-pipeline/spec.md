## Purpose

Constrói e grava os pontos de uma curva numa data a partir do cadastro da curva, e interpola esses pontos sob demanda. O cadastro concentra tudo o que varia entre curvas (origem dos dados, modelos, convenções de tempo e de cotação, extrapolação, horizonte e arredondamento), e o engine não tem nenhuma regra específica de curva no código.

## ADDED Requirements

### Requirement: Cadastro completo da curva
O cadastro de cada curva SHALL conter todos os itens abaixo:
- **código da curva**, que identifica a curva na API (ex.: `PRE`);
- **nome de exibição**, usado pelos usuários para encontrar e visualizar a curva (ex.: `DIxPRE`);
- **origem**: a fonte dos dados (ex.: `TS_B3`) e o código da curva nessa fonte (ex.: `PRE`, descrita `DIxPRE` no `TaxaSwap.txt`);
- **modelo de construção**;
- **unidade**: `TAXA`, `PRECO` ou `PONTOS`;
- **grandeza interpolada**: `Discount`, `ZeroYield`, `ForwardRate` (nomes do QuantLib) ou `CompoundFactor` para curvas de taxa, `Price` para curvas de preço ou pontos;
- **interpolador**: ex.: `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`;
- **`DayCounter` do eixo de tempo**: ex.: `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360`;
- **cotação da taxa** (só para curvas de taxa): `DayCounter`, `Compounding` e `Frequency`;
- **calendário**: nome, mercado e `BusinessDayConvention`;
- **extrapolação**: uma política para o início e outra para o fim da curva;
- **horizonte de extrapolação**: quantidade + `TimeUnit`;
- **arredondamento**: casas decimais e modo, com truncamento como modo possível;
- **opcional**: versão fixada de script para qualquer um dos modelos.

#### Scenario: Cadastro da PRE
- **WHEN** a curva `PRE` é cadastrada com origem `TS_B3`/`PRE`, construção `PRONTA_TS_B3`, unidade `TAXA`, `Discount` + `LogLinear`, tempo `Business252`, cotação `Business252`/`Compounded`/`Annual`, calendário `Brazil`/`Settlement`/`Following`, extrapolação de fim `FlatForward` e horizonte de 50 `Years`
- **THEN** a curva pode ser construída e interpolada sem nenhum outro parâmetro vindo do chamador

### Requirement: Nenhuma regra específica de curva no código
Todo comportamento que muda entre curvas SHALL vir do cadastro. Incluir uma nova curva que usa modelos já existentes MUST exigir apenas o cadastro, sem mudança de código nem de deploy.

#### Scenario: Nova curva pronta só por cadastro
- **WHEN** a curva `SLP` é cadastrada com origem `TS_B3`/`SLP`, construção `PRONTA_TS_B3` e interpolação `Discount` + `LogLinear` em `Business252`
- **THEN** a curva `SLP` pode ser construída e interpolada sem alteração de código

### Requirement: Construção guiada pelo cadastro da curva
O engine SHALL construir uma curva a partir do seu código e da data-base, usando exclusivamente o cadastro da curva. Nenhum modelo, convenção ou política SHALL vir do chamador. Se o código não tiver cadastro, ou se faltar um item obrigatório do cadastro, a construção MUST falhar informando o código e o item ausente, sem usar valor padrão.

#### Scenario: Código sem cadastro
- **WHEN** a construção de um código sem cadastro é pedida
- **THEN** a construção falha informando que o código não tem cadastro, e nada é gravado

#### Scenario: Cadastro sem interpolador
- **WHEN** o cadastro da curva não indica o interpolador
- **THEN** a construção falha informando o código e o item ausente, sem cair em interpolador padrão

### Requirement: Construção grava apenas os pontos
Cada construção de uma curva numa data-base SHALL gravar no **dado curva** apenas os pontos observados ou calculados pelo modelo de construção a partir da origem cadastrada. A construção MUST NOT gravar curva interpolada: a interpolação acontece sob demanda, na consulta.

#### Scenario: Construção da PRE
- **WHEN** a curva `PRE` de `2026-09-14` é construída
- **THEN** o dado curva tem os 278 pontos da fonte, e nenhum ponto interpolado é gravado

### Requirement: Insumo ausente interrompe a construção
Quando a origem cadastrada não tiver os dados exigidos para a data, a construção MUST falhar informando o código da curva, a fonte, o código na fonte e a data. O engine MUST NOT estimar, repetir dados de outra data ou gravar curva parcial.

#### Scenario: Data sem insumo
- **WHEN** a construção de `DCL` é pedida para uma data em que a fonte `TS_B3` não tem vértices do código `DCL`
- **THEN** a construção falha informando `DCL`, `TS_B3` e a data, e nenhuma versão é gravada

### Requirement: Interpolação sob demanda a partir dos pontos gravados
A interpolação SHALL ser calculada no momento da consulta, a partir dos pontos gravados no dado curva para o código e a data-base, com os modelos e o cadastro da curva. Se a curva não tiver pontos gravados naquela data, a interpolação MUST falhar informando que a curva não foi construída.

#### Scenario: Curva construída
- **WHEN** a interpolação da `PRE` de `2026-09-14` é pedida para 21 dias úteis, depois de a curva ter sido construída
- **THEN** o valor é calculado a partir dos 278 pontos gravados, sem nova leitura da fonte

#### Scenario: Curva não construída
- **WHEN** a interpolação da `DPL` é pedida para uma data sem pontos gravados
- **THEN** a interpolação falha informando `DPL` e a data

### Requirement: Interpolação genérica por grandeza, interpolador e DayCounter
A interpolação SHALL ser feita sobre a grandeza cadastrada, com o interpolador cadastrado e com o tempo medido pelo `DayCounter` do eixo de tempo cadastrado. Nenhum interpolador SHALL depender de uma base fixa de dias. As taxas SHALL ser convertidas de e para fatores pela cotação cadastrada (`DayCounter`, `Compounding`, `Frequency`), que MAY ser diferente do `DayCounter` do eixo de tempo.

#### Scenario: Flat forward 252 (PRE)
- **WHEN** a curva cadastrada com `Discount` + `LogLinear`, tempo `Business252` e cotação `Business252`/`Compounded`/`Annual` é interpolada entre dois pontos
- **THEN** o resultado é o mesmo da Interpolação Flat Forward 252 do Manual de Curvas B3 (item 1.4.2)

#### Scenario: Flat forward 252 com convenção linear (DCL)
- **WHEN** a curva cadastrada com `Discount` + `LogLinear`, tempo `Business252` e cotação `Actual360`/`Simple` é interpolada entre dois pontos
- **THEN** o resultado é o mesmo da Interpolação Flat Forward 252 com Convenção Linear do manual (item 1.4.3)

#### Scenario: Mesma interpolação com outro DayCounter
- **WHEN** a curva cadastrada com `Discount` + `LogLinear`, tempo `Actual360` e cotação `Actual360`/`Compounded`/`Annual` é interpolada entre dois pontos
- **THEN** o resultado é o mesmo da Interpolação 360 do manual (item 1.4.4), sem nenhum interpolador específico de 360

#### Scenario: Linear 360
- **WHEN** a curva cadastrada com `CompoundFactor` + `Linear`, tempo `Actual360` e cotação `Actual360`/`Simple` é interpolada entre dois pontos
- **THEN** o resultado é o mesmo da Interpolação 360 Linear do manual (item 1.4.11), que é linear em taxa × prazo e não na taxa

#### Scenario: Preços
- **WHEN** a curva cadastrada com `Price` + `LogLinear` e tempo `Business252` é interpolada entre dois pontos
- **THEN** o resultado é o mesmo da Interpolação de Preços do manual (item 1.4.5)

### Requirement: Políticas de extrapolação por lado
O cadastro SHALL definir uma política de extrapolação para o início (antes do primeiro ponto) e outra para o fim (depois do último), escolhidas entre:
- `Disabled`: extrapolação desligada; é o comportamento padrão do QuantLib e o valor usado quando o cadastro não define a política. Pedir prazo fora do domínio é erro.
- `FlatForward`: estende o forward do segmento adjacente. No fim, usa o penúltimo e o último ponto (manual 1.4.6 e 1.4.10); no início, o primeiro e o segundo (manual 1.4.7).
- `FlatValue`: repete o valor do ponto adjacente. No fim, repete a última taxa, preço ou pontos (manual 1.4.8); no início, o primeiro (manual 1.4.9).

A extrapolação SHALL usar a mesma grandeza, `DayCounter` e cotação cadastrados para a interpolação.

#### Scenario: Flat forward no fim com convenção linear (DCL)
- **WHEN** a `DCL` (`Discount` + `LogLinear`, tempo `Business252`, cotação `Actual360`/`Simple`) é extrapolada no fim com `FlatForward`
- **THEN** o resultado é o da Extrapolação Flat Forward 252 com Convenção Linear (Fim) do manual (item 1.4.10)

#### Scenario: Flat value no fim (INP)
- **WHEN** a `INP` é extrapolada no fim com `FlatValue`
- **THEN** todos os prazos após o último ponto têm os pontos de índice do último ponto

#### Scenario: Flat forward no início
- **WHEN** uma curva é extrapolada no início com `FlatForward`
- **THEN** os prazos antes do primeiro ponto seguem o forward entre o primeiro e o segundo ponto (manual 1.4.7), e não uma taxa constante

### Requirement: Domínio da interpolação até o último ponto ou o horizonte
O domínio da interpolação SHALL ir do primeiro dia útil após a data-base até a **maior** entre duas datas: a do último ponto gravado e a data-base somada ao horizonte cadastrado. Prazos até o último ponto SHALL ser interpolados. Prazos depois do último ponto e até o horizonte SHALL seguir a política de extrapolação de fim. Prazos além do domínio, ou além do último ponto com extrapolação de fim `Disabled`, MUST resultar em erro informando o prazo.

#### Scenario: Horizonte menor que o último ponto
- **WHEN** a `PRE` de `2026-09-14` tem o último ponto em `2060-08-16`, o horizonte cadastrado é de 10 anos, e é pedido o prazo de `2045-01-02`
- **THEN** o valor é interpolado, porque o domínio vai até o último ponto mesmo com horizonte menor

#### Scenario: Prazo extrapolado dentro do horizonte
- **WHEN** uma curva tem o último ponto a 5 anos da data-base, o horizonte cadastrado é de 10 anos, a extrapolação de fim é `FlatForward`, e é pedido o prazo de 7 anos
- **THEN** o valor é extrapolado com `FlatForward` e marcado como extrapolado

#### Scenario: Prazo além do horizonte
- **WHEN** a mesma curva recebe pedido de prazo de 12 anos
- **THEN** a interpolação falha informando o prazo fora do domínio

### Requirement: Pontos preservados na interpolação
Um prazo pedido que coincide com um ponto gravado MUST devolver o valor do ponto depois do arredondamento cadastrado, sem alteração introduzida pela interpolação.

#### Scenario: Prazo coincidente
- **WHEN** a `PRE` de `2026-09-14` tem ponto em 7.406 dias úteis com taxa 14,1670000, e esse prazo é pedido
- **THEN** o valor devolvido é 14,1670000

### Requirement: Fatores só para curvas de taxa
Para curvas de unidade `TAXA`, os valores devolvidos na consulta SHALL trazer o fator acumulado e o fator diário calculados pela cotação cadastrada. Curvas `PRECO` ou `PONTOS` MUST NOT receber fatores de juros.

#### Scenario: Curva de pontos
- **WHEN** a curva `INP` (unidade `PONTOS`) é consultada
- **THEN** os valores trazem apenas os pontos de índice, sem fator diário nem acumulado

### Requirement: Precisão e arredondamento
Valores de taxa, preço, pontos e fatores MUST ser calculados em aritmética decimal, sem passar por ponto flutuante binário. Cada valor gravado ou devolvido SHALL seguir o arredondamento cadastrado para a curva.

#### Scenario: Curva truncada
- **WHEN** a curva `PTX` tem arredondamento cadastrado de 7 casas com truncamento
- **THEN** todos os valores devolvidos de `PTX` têm 7 casas decimais truncadas, nunca arredondadas

### Requirement: Proveniência dos modelos e do cadastro
Toda resposta de construção SHALL informar o modelo de construção e o calendário usados, cada um com nome, origem (Java nativo ou script Groovy) e, para Groovy, versão e hash do script. A mesma informação e os valores do cadastro vigentes SHALL ser registrados no log estruturado da construção. Toda resposta de interpolação SHALL informar o interpolador e as políticas de extrapolação usados, com a mesma identificação de origem, versão e hash.

#### Scenario: Interpolação com interpolador sobrescrito por Groovy
- **WHEN** a interpolação é pedida enquanto um script Groovy ativo sobrescreve o interpolador `LogLinear`
- **THEN** a resposta informa `LogLinear` com origem Groovy, a versão e o hash do script

### Requirement: Reconstrução da mesma data
Pedir a construção de uma curva e data que já tem pontos gravados, sem pedir recálculo, SHALL devolver os pontos existentes sem reconstruir. Com recálculo pedido, o engine SHALL reconstruir e substituir, numa única transação, os pontos daquela data. Nenhuma versão anterior é mantida nesta fase.

#### Scenario: Pedido repetido sem recálculo
- **WHEN** a construção de `PRE` em `2026-09-14` é pedida de novo sem recálculo
- **THEN** o engine devolve os pontos já gravados e não executa o modelo de construção

#### Scenario: Pedido com recálculo
- **WHEN** a construção de `PRE` em `2026-09-14` é pedida com recálculo
- **THEN** os pontos da data são apagados e regravados na mesma transação, e as interpolações seguintes usam os pontos novos

### Requirement: Determinismo
Com os mesmos insumos, o mesmo cadastro e as mesmas versões de modelo, a construção MUST gravar exatamente os mesmos pontos, e a interpolação MUST devolver exatamente os mesmos valores.

#### Scenario: Duas interpolações idênticas
- **WHEN** o mesmo prazo da mesma curva e data é interpolado duas vezes, sem mudança de pontos, cadastro ou modelo
- **THEN** os dois valores devolvidos são idênticos
