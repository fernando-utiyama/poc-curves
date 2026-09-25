## Purpose

Mantém o catálogo de modelos de construção, de interpolação e de calendário usados pelo engine, com tipos e enums compatíveis com o QuantLib. Os modelos nativos em Java podem ser complementados por modelos novos, ou sobrescritos, por scripts Groovy, com versão, validação, contenção e rastreabilidade.

## ADDED Requirements

### Requirement: Três tipos de modelo identificados por nome
O catálogo SHALL ter três tipos de modelo: construção, interpolação e calendário. Dentro de cada tipo, o modelo SHALL ser identificado por um nome em texto, que é o valor gravado no cadastro da curva. O mesmo nome MAY existir em tipos diferentes sem conflito.

#### Scenario: Resolução por tipo
- **WHEN** existe um interpolador chamado `Linear` e nenhum calendário com esse nome
- **THEN** a resolução do interpolador `Linear` funciona, e a do calendário `Linear` falha como nome inexistente

### Requirement: Tipos e enums compatíveis com o QuantLib
Os tipos usados pelos modelos e pelo cadastro SHALL ter os mesmos nomes de tipo e de constante do QuantLib:
- `Compounding`: `Simple`, `Compounded`, `Continuous`, `SimpleThenCompounded`, `CompoundedThenSimple`;
- `Frequency`: de `NoFrequency` a `OtherFrequency`, com os mesmos valores numéricos;
- `BusinessDayConvention`: `Following`, `ModifiedFollowing`, `Preceding`, `ModifiedPreceding`, `Unadjusted`, `HalfMonthModifiedFollowing`, `Nearest`;
- `TimeUnit`: `Days`, `Weeks`, `Months`, `Years`;
- os `DayCounter` (`Business252`, `Actual360`, `Actual365Fixed`, `Thirty360`);
- os calendários e seus mercados (`Brazil` com `Settlement`; `UnitedStates` com `FederalReserve` e `SOFR`);
- os interpoladores (`Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`) e as grandezas interpoladas (`Discount`, `ZeroYield`, `ForwardRate`).

As extensões sem equivalente no QuantLib (`CompoundFactor`, `Price` e as políticas de extrapolação) SHALL seguir o mesmo estilo de nome. A implementação MUST ser própria, em Java, sem depender da biblioteca QuantLib.

#### Scenario: Script no estilo QuantLib
- **WHEN** um script Groovy usa `Compounding.Compounded`, `Frequency.Annual`, `new Business252(new Brazil(Brazil.Market.Settlement))` e `BusinessDayConvention.Following`
- **THEN** o script compila e executa contra o engine sem adaptação de nomes

### Requirement: Modelos nativos disponíveis sem cadastro prévio
O engine SHALL disponibilizar, sem nenhuma importação:
- o modelo de construção `PRONTA_TS_B3`;
- os interpoladores `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat` e `Cubic`;
- as grandezas `Discount`, `ZeroYield`, `ForwardRate`, `CompoundFactor` e `Price`;
- os `DayCounter` `Business252`, `Actual360`, `Actual365Fixed` e `Thirty360`;
- as políticas de extrapolação `Disabled`, `FlatForward` e `FlatValue`;
- o calendário `Brazil` (mercado `Settlement`, com os feriados ANBIMA).

#### Scenario: Engine recém-iniciado
- **WHEN** o engine sobe sem nenhum script Groovy cadastrado
- **THEN** uma curva cadastrada com `PRONTA_TS_B3`, `Discount` + `LogLinear`, `Business252` e `Brazil`/`Settlement` pode ser construída

### Requirement: Groovy ativo tem precedência sobre Java
Ao resolver um modelo por tipo e nome, o catálogo SHALL usar, nesta ordem:
1. a versão de script fixada no cadastro da curva, se houver;
2. o script Groovy ativo com aquele tipo e nome;
3. o modelo Java nativo com aquele tipo e nome.

Se nenhum existir, a resolução MUST falhar informando tipo e nome, sem usar modelo padrão.

#### Scenario: Sobrescrita de um interpolador nativo
- **WHEN** existe um script Groovy ativo de interpolação chamado `LogLinear`
- **THEN** toda curva cadastrada com `LogLinear` passa a usar o script, e não o interpolador Java

#### Scenario: Modelo novo só em Groovy
- **WHEN** um script Groovy de construção chamado `EXEMPLO_SO_GROOVY`, sem equivalente Java, é ativado
- **THEN** uma curva cadastrada com esse modelo de construção pode ser construída

#### Scenario: Calendário novo em Groovy
- **WHEN** um script Groovy de calendário chamado `Brazil` com mercado `Exchange` é ativado
- **THEN** uma curva cadastrada com esse calendário usa os feriados definidos no script

#### Scenario: Nome inexistente
- **WHEN** o cadastro de uma curva indica o calendário `XPTO` e não há Java nem Groovy com esse nome
- **THEN** a resolução falha informando tipo calendário e nome `XPTO`

#### Scenario: Desativação volta ao nativo
- **WHEN** o script Groovy ativo que sobrescrevia `LogLinear` é desativado
- **THEN** as construções seguintes voltam a usar o `LogLinear` Java nativo

### Requirement: Sobrescrita parcial por herança
Um script Groovy SHALL poder estender um modelo nativo e substituir apenas um de seus pontos de extensão. O resto do comportamento SHALL permanecer o do modelo nativo. Pontos de extensão mínimos:
- **Interpolação:** valor entre dois vértices, extrapolação do início e extrapolação do fim.
- **Construção:** leitura de insumos e montagem de vértice.
- **Calendário:** feriados de um ano e teste de dia útil.

#### Scenario: Troca só da extrapolação de fim
- **WHEN** um script Groovy ativo estende `LogLinear` e substitui só a extrapolação de fim
- **THEN** a curva construída tem os pontos entre vértices iguais aos do nativo, e só os pontos após o último vértice mudam

#### Scenario: Feriado extra no calendário
- **WHEN** um script Groovy ativo estende `Brazil`/`Settlement` e acrescenta um feriado local
- **THEN** a contagem de dias úteis passa a desconsiderar esse dia, e os demais feriados continuam os do nativo

### Requirement: Versões de script persistentes
Cada envio de script SHALL criar uma nova versão para aquele tipo e nome, com status `RASCUNHO`, conteúdo, hash SHA-256, autor e data. Os scripts MUST ficar gravados de forma persistente: depois de um reinício do engine, os scripts ativos voltam a ser usados sem reenvio. Cada tipo e nome SHALL ter no máximo uma versão `ATIVA`.

#### Scenario: Reinício do engine
- **WHEN** o engine é reiniciado com um script ativo para o interpolador `LogLinear`
- **THEN** depois do reinício o script continua sendo usado, sem novo envio

#### Scenario: Ativação de nova versão
- **WHEN** a versão 3 de um script é ativada enquanto a versão 2 estava ativa
- **THEN** a versão 3 passa a `ATIVA` e a versão 2 passa a `INATIVA`

### Requirement: Validação antes de ativar ou fixar
Um script SHALL poder ser ativado, ou fixado no cadastro de uma curva, somente depois de passar na validação. A validação verifica que o script compila, que implementa o tipo de modelo declarado e que sua execução de validação termina sem erro dentro do tempo limite. Se a validação falhar, o script MUST continuar sem ativação, e o motivo da falha SHALL ser devolvido.

#### Scenario: Script que não compila
- **WHEN** a validação de um script com erro de sintaxe é pedida
- **THEN** a validação falha com a mensagem do compilador e o script não pode ser ativado

#### Scenario: Script do tipo errado
- **WHEN** um script de calendário é enviado como modelo de interpolação
- **THEN** a validação falha informando que o script não implementa o tipo interpolação

### Requirement: Contenção da execução
Um script Groovy SHALL poder usar somente os pacotes permitidos: os objetos de curva do engine, os tipos compatíveis com o QuantLib e as bibliotecas padrão de números, datas e coleções. Um script que tente acessar arquivo, rede, processo, threads, reflexão ou propriedades do sistema MUST ser rejeitado na validação. A execução de um script MUST ser interrompida quando passar do tempo limite configurado, e a construção que o usava falha.

#### Scenario: Script tenta acessar a rede
- **WHEN** um script que abre uma conexão de rede é enviado para validação
- **THEN** a validação falha por violação de contenção e o script não pode ser ativado

#### Scenario: Script em laço infinito
- **WHEN** um script ativo não termina dentro do tempo limite durante uma construção
- **THEN** a execução é interrompida e a construção falha informando o modelo e o tempo esgotado

### Requirement: Fixação de versão no cadastro da curva
O cadastro de uma curva MAY fixar uma versão validada de script para qualquer um dos seus modelos. Uma versão fixada SHALL ser usada por aquela curva mesmo que outra versão esteja ativa, o que permite testar um script numa curva antes de ativá-lo para todas.

#### Scenario: Teste numa curva só
- **WHEN** o cadastro de `DPL` fixa a versão 4 (validada, não ativa) do interpolador `LogLinear`, e a versão ativa é a 3
- **THEN** `DPL` é construída com a versão 4, e `PRE` continua com a versão 3
