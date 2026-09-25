## Why

O primeiro objetivo do projeto é entregar as curvas DIxPRE, DCL, PTAX, DPL e IBOVESPA (Taxa Swap B3), NTN-B (ANBIMA) e SOFR (Bloomberg). As curvas B3 já chegam prontas no arquivo `TaxaSwap.txt`: 278 vértices padronizados por curva, com os valores publicados. Mas o `services/engine` da `develop` não consegue tratá-las: a refatoração de modelos ficou pela metade (interpolador descartado no registro, conversão taxa↔fator repetida em quatro lugares, convenção fixa em 252, `cRotnaCalc` sem uso). Além disso, o catálogo de tipos do conector publica a DCL e a DPL como DOL e descarta a PTX e o INP.

Esta mudança termina a refatoração. Cada modelo de construção, de interpolação e de calendário vira um pacote próprio, escolhido pelo cadastro da curva, e pode ser criado ou sobrescrito por Groovy.

## What Changes

- **Pipeline guiado pelo cadastro.** Construir uma curva passa a ser: ler o cadastro → executar o **modelo de construção** (`cMotorCalc`) → gravar os pontos da data. A interpolação acontece na consulta: lê os pontos gravados e aplica o **modelo de interpolação** (`cRotnaCalc`). `cRotnaCalc`, hoje sem uso, passa a guardar o modelo de interpolação.
- **Tudo que varia entre curvas fica no cadastro, e nada é específico de curva no código.** O cadastro guarda:
  - a origem dos dados: fonte e código da curva na fonte. Exemplo: a curva de mercado `PRE` é construída a partir do código `PRE` (DIxPRE) da fonte `TS_B3`;
  - o modelo de construção;
  - a unidade;
  - a grandeza interpolada, o interpolador e o `DayCounter` do eixo de tempo;
  - a cotação da taxa;
  - o calendário e a `BusinessDayConvention`;
  - a extrapolação no início e no fim;
  - o horizonte e o arredondamento.

  Uma curva nova que usa modelos já existentes entra só por cadastro.
- **Um pacote Java por modelo.**
  - Construção: `construcao/<modelo>`, começando por `prontatsb3` (curva pronta do TaxaSwap B3).
  - Interpolação genérica no modelo do QuantLib, sem nada fixo em 252 ou 360. Uma interpolação é a combinação de três escolhas do cadastro:
    - a grandeza interpolada: `Discount`, `ZeroYield`, `ForwardRate` (nomes do QuantLib), `CompoundFactor` (necessária para a Interpolação 360 Linear da B3, que é linear em taxa × prazo) ou `Price` (curvas de preço e pontos);
    - o interpolador (`Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`);
    - o `DayCounter` do eixo de tempo (`Business252`, `Actual360`, `Actual365Fixed`, `Thirty360`).

    A cotação da taxa é separada: `DayCounter`, `Compounding` e `Frequency`. As funções do Manual de Curvas B3 viram só configuração. Exemplo: Flat Forward 252 (1.4.2) = `Discount` + `LogLinear` + `Business252`, cotado `Compounded`/`Annual`. Cada interpolador é um pacote em `interpolacao/<interpolador>`. As políticas de extrapolação são definidas separadamente para o início e para o fim da curva:
    - `Disabled`: padrão do QuantLib;
    - `FlatForward`: estende o forward do segmento adjacente, manual 1.4.6, 1.4.7 e 1.4.10;
    - `FlatValue`: repete o valor adjacente, manual 1.4.8 e 1.4.9.

    Duas delas não existem no QuantLib: `FlatForward` no início e `FlatValue` no fim. Ficam como extensão própria.
  - Calendário: `calendario/<calendario>`, com `Brazil` (mercado `Settlement`, os feriados ANBIMA) e, em seguida, `UnitedStates` (mercados `FederalReserve`/`SOFR`).
- **Tipos e enums idênticos aos do QuantLib.** `Compounding`, `Frequency`, `BusinessDayConvention`, `TimeUnit`, os `DayCounter` e os calendários (com seus mercados) têm os mesmos nomes de tipo e de constante do QuantLib, para que scripts Groovy escritos no estilo QuantLib compilem sem adaptação. É uma implementação própria, 100% Java, sem dependência da biblioteca QuantLib.
- **Só os pontos são gravados; a interpolação é sob demanda.** Nesta fase, a construção grava apenas os pontos observados ou calculados no dado curva (`tDadoCurva`). A proveniência sai na resposta da API e no log; `tMtrizCurva` não é usada, porque é de superfícies. Os pontos também podem ser editados por API, substituindo a lista inteira da data. A curva interpolada não é persistida: a API a calcula na consulta, com domínio até o último ponto real ou até o horizonte do cadastro, o que for mais longe. Persistir a curva diária em `tCurvaData` depende de alterar a FK `FK_tDadoCurva_tCurvaData` do schema oficial, e fica registrado como alvo ideal no design.
- **Modelos Groovy podem criar e sobrescrever.** Construção, interpolação e calendário aceitam scripts Groovy. Um script **ativo** com o mesmo nome de um modelo Java tem precedência sobre ele. O script pode herdar do modelo Java e trocar só um ponto de extensão.
  - Os scripts ficam gravados no banco, com versão, hash e status: sobrevivem a um restart.
  - Só são ativados depois de compilar e de passar numa execução de validação.
  - Rodam numa sandbox por lista permitida.
- **Proveniência por curva gravada.** Toda curva gravada registra quais modelos de construção, interpolação e calendário foram usados: origem (Java ou Groovy), versão e hash.
- **BREAKING — endpoints por código da curva + data.** Construir, consultar, editar os pontos e interpolar passam a identificar a curva pelo código (ex.: `PRE`) e pela data-base na URL, sem id técnico. Para usuários externos, consultar e interpolar também existem pelo nome de exibição, junto com um catálogo de curvas para busca por nome. A interpolação sob demanda deixa de aceitar método e política no request: usa os modelos do cadastro. Substitui `POST /api/v1/curvas/construir`, `POST /api/v1/calculo` e `POST /api/v1/modelos/upload`.
- **Conversão numérica centralizada, com precisão decimal.** A conversão taxa↔fator passa a existir num único lugar, no equivalente ao `InterestRate` do QuantLib (taxa + `DayCounter` + `Compounding` + `Frequency`), em `BigDecimal`. O arredondamento também. Curvas de preço (PTX) e de pontos (INP) não recebem fatores de juros.
- **Catálogo do conector por código exato.** O catálogo de tipos de curva B3 passa a casar pelo código exato do `TaxaSwap.txt`, e inclui `PRE`, `DCL`, `PTX`, `DPL`, `INP`, `ZUS` e `TIC`. A descrição deixa de ser usada para decidir o tipo.
- **Removido do engine:** os enums `MetodoInterpolacao` e `PoliticaExtrapolacao` e os parâmetros em texto `CONVENCAO` e `MOD_DADO`. `ComposableCurveBuilder`, `CurveBuilderRegistry`, `CurveInterpolatorRegistry` e `CurveExtrapolatorRegistry` são substituídos pelos registros genéricos de modelos.

## Capabilities

### New Capabilities
- `curve-build-pipeline`: construção de uma curva a partir do cadastro (código + data): resolução dos modelos de construção, interpolação e calendário, montagem da curva da data, gravação dos pontos no dado curva com versão e proveniência, e interpolação sob demanda com domínio até o último ponto ou o horizonte.
- `curve-extension-models`: catálogo de modelos de construção, interpolação e calendário. Inclui modelos Java por pacote, criação e sobrescrita por Groovy, versões, ativação com validação, sandbox e rastreabilidade do modelo usado.
- `curve-engine-api`: endpoints do engine identificados por código da curva + data-base (construir, consultar a curva gravada, interpolar prazos arbitrários) e gestão de modelos Groovy por tipo e nome.
- `b3-ready-curve-model`: modelo de construção `PRONTA_TS_B3`, que usa os 278 vértices prontos do `TaxaSwap.txt` para PRE, DCL, PTX, DPL e INP. Inclui a semântica de cada curva (unidade, convenção, interpolação, extrapolação, arredondamento) e o catálogo por código exato no conector.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas na develop. -->

## Impact

- **services/engine:** reorganização de `domain` (novos pacotes `curva`, `construcao`, `interpolacao`, `calendario`, `matematica`) e reescrita de `ConstruirCurvaService` e `CalcularCurvaService`. Controllers e DTOs REST passam a usar código + data. `GroovyDynamicModelCompiler` e `ModeloUploadController` viram um carregador genérico com persistência. Nova tabela de scripts de modelo. O engine passa a gravar só `tDadoCurva` (pontos) e deixa de gravar `tDadoVertcCurva` e `tMtrizCurva`. `tCurvaData` não é gravada nesta fase.
- **Banco:** `tConfgCurva.cRotnaCalc` passa a ser usado. `tParmConfgCurva` recebe os parâmetros de convenção, unidade, calendário, extrapolação, horizonte e arredondamento. Nova tabela para os scripts Groovy de modelo.
- **services/conector:** `curveB3TypeCatalog.ts` e `normalizeB3CurveType.ts` passam a casar pelo código exato. O cálculo de fatores em `curveB3Factors.ts` deixa de ser a referência (o processor já descarta esses campos).
- **Consumidores da API do engine:** qualquer cliente dos endpoints atuais por corpo JSON precisa migrar para as rotas por código + data (BREAKING).
- **Fora de escopo:** NTN-B (ANBIMA) e SOFR (Bloomberg) como curvas construídas. Esta mudança deixa os pontos de extensão prontos para elas, mas as metodologias ficam para mudanças seguintes.
