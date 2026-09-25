## Why

O primeiro objetivo do projeto é entregar as 7 curvas: DIxPRE, DCL, PTAX, DPL e IBOVESPA (Taxa Swap B3), NTN-B (ANBIMA) e SOFR (Bloomberg). As três fontes exigem tratamento diferente: as curvas B3 já chegam prontas no arquivo `TaxaSwap.txt` (278 vértices padronizados, valores publicados); a NTN-B exige bootstrap real (a taxa indicativa da ANBIMA é YTM de título com cupom, não taxa zero); o SOFR do Bloomberg (`S0490Z <tenor> BLC2 Curncy`) já vem como zero rate por tenor, sem bootstrap, mas por uma fonte e um formato de dado diferentes dos de qualquer curva já tratada no projeto. Mas o `services/engine` da `develop` não consegue tratar nem a mais simples das três: a refatoração de modelos ficou pela metade (interpolador descartado no registro, conversão taxa↔fator repetida em quatro lugares, convenção fixa em 252, `cRotnaCalc` sem uso).

Esta mudança termina a refatoração do pipeline e entrega as três fontes de uma vez. Cada modelo de construção, de interpolação e de calendário vira um pacote próprio, escolhido pelo cadastro da curva, e pode ser criado ou sobrescrito por Groovy.

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
  - Construção: `construcao/<modelo>`, um pacote por fonte/metodologia:
    - `pontosprontos`: utilitário comum aos modelos **sem bootstrap** (leem um valor já calculado na fonte e devolvem como ponto, sem resolver nada);
    - `prontatsb3` (`PRONTA_TS_B3`, usa `pontosprontos`): curva pronta do TaxaSwap B3 — PRE, DCL, DPL, INP, PTX;
    - `sofrzerobloomberg` (`SOFR_ZERO_BLOOMBERG`, usa `pontosprontos`): curva SOFR — lê os nós por tenor do curve member Bloomberg (zero rate já pronta, confirmado por busca — [Lucido Group](https://www.lucidogroup.io/smoother-path-to-sofr-curve-construction/)) e converte tenor em vértice de data pelo calendário `UnitedStates`;
    - `ntnbbootstrapanbima` (`NTNB_BOOTSTRAP_ANBIMA`, com bootstrap real): curva NTN-B — lê as taxas indicativas por título da ANBIMA (YTM, não taxa zero) e resolve a taxa zero de cada vencimento por iteração sequencial, descontando os cupons intermediários (semestrais, reais, indexados ao IPCA) pelas taxas já resolvidas nos vencimentos anteriores.

    `pontosprontos` e `ntnbbootstrapanbima` só compartilham nome de pacote com `prontatsb3`/`sofrzerobloomberg` no sentido de organização — cada um é registrado com seu próprio nome de modelo, nunca reaproveitando o nome de outro: a proveniência gravada na curva depende de o nome do modelo identificar exatamente o que rodou.
  - Interpolação genérica no modelo do QuantLib, sem nada fixo em 252 ou 360. Uma interpolação é a combinação de três escolhas do cadastro:
    - a grandeza interpolada: `Discount`, `ZeroYield`, `ForwardRate` (nomes do QuantLib), `CompoundFactor` (necessária para a Interpolação 360 Linear da B3, que é linear em taxa × prazo) ou `Price` (curvas de preço e pontos);
    - o interpolador (`Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`);
    - o `DayCounter` do eixo de tempo (`Business252`, `Actual360`, `Actual365Fixed`, `Thirty360`).

    A cotação da taxa é separada: `DayCounter`, `Compounding` e `Frequency`. As funções do Manual de Curvas B3 viram só configuração. Exemplo: Flat Forward 252 (1.4.2) = `Discount` + `LogLinear` + `Business252`, cotado `Compounded`/`Annual`. Cada interpolador é um pacote em `interpolacao/<interpolador>`. As políticas de extrapolação são definidas separadamente para o início e para o fim da curva:
    - `Disabled`: padrão do QuantLib;
    - `FlatForward`: estende o forward do segmento adjacente, manual 1.4.6, 1.4.7 e 1.4.10;
    - `FlatValue`: repete o valor adjacente, manual 1.4.8 e 1.4.9.

    Duas delas não existem no QuantLib: `FlatForward` no início e `FlatValue` no fim. Ficam como extensão própria.
  - Calendário: `calendario/<calendario>`, com `Brazil` (mercado `Settlement`, os feriados ANBIMA, usado por PRE/DCL/DPL/INP/PTX/NTN-B) e `UnitedStates` (mercado `FederalReserve`, feriados federais dos EUA, usado pelo SOFR).
- **Tipos e enums idênticos aos do QuantLib.** `Compounding`, `Frequency`, `BusinessDayConvention`, `TimeUnit`, os `DayCounter` e os calendários (com seus mercados) têm os mesmos nomes de tipo e de constante do QuantLib, para que scripts Groovy escritos no estilo QuantLib compilem sem adaptação. É uma implementação própria, 100% Java, sem dependência da biblioteca QuantLib.
- **Só os pontos são gravados; a interpolação é sob demanda.** Nesta fase, a construção grava apenas os pontos observados ou calculados no dado curva (`tDadoCurva`). A proveniência sai na resposta da API e no log; `tMtrizCurva` não é usada, porque é de superfícies. Os pontos também podem ser editados por API, substituindo a lista inteira da data. A curva interpolada não é persistida: a API a calcula na consulta, com domínio até o último ponto real ou até o horizonte do cadastro, o que for mais longe. Persistir a curva diária em `tCurvaData` depende de alterar a FK `FK_tDadoCurva_tCurvaData` do schema oficial, e fica registrado como alvo ideal no design.
- **Modelos Groovy podem criar e sobrescrever.** Construção, interpolação e calendário aceitam scripts Groovy. Um script **ativo** com o mesmo nome de um modelo Java tem precedência sobre ele. O script pode herdar do modelo Java e trocar só um ponto de extensão.
  - Os scripts ficam gravados no banco, com versão, hash e status: sobrevivem a um restart.
  - Só são ativados depois de compilar e de passar numa execução de validação.
  - Rodam numa sandbox por lista permitida.
- **Proveniência por curva gravada.** Toda curva gravada registra quais modelos de construção, interpolação e calendário foram usados: origem (Java ou Groovy), versão e hash.
- **BREAKING — endpoints por código da curva + data.** Construir, consultar, editar os pontos e interpolar passam a identificar a curva pelo código (ex.: `PRE`) e pela data-base na URL, sem id técnico. Para usuários externos, consultar e interpolar também existem pelo nome de exibição, junto com um catálogo de curvas para busca por nome. A interpolação sob demanda deixa de aceitar método e política no request: usa os modelos do cadastro. Substitui `POST /api/v1/curvas/construir`, `POST /api/v1/calculo` e `POST /api/v1/modelos/upload`.
- **Conversão numérica centralizada, com precisão decimal.** A conversão taxa↔fator passa a existir num único lugar, no equivalente ao `InterestRate` do QuantLib (taxa + `DayCounter` + `Compounding` + `Frequency`), em `BigDecimal`. O arredondamento também. Curvas de preço (PTX) e de pontos (INP) não recebem fatores de juros.
- **Removido do engine:** os enums `MetodoInterpolacao` e `PoliticaExtrapolacao` e os parâmetros em texto `CONVENCAO` e `MOD_DADO`. `ComposableCurveBuilder`, `CurveBuilderRegistry`, `CurveInterpolatorRegistry` e `CurveExtrapolatorRegistry` são substituídos pelos registros genéricos de modelos.

## Capabilities

### New Capabilities
- `curve-build-pipeline`: construção de uma curva a partir do cadastro (código + data): resolução dos modelos de construção, interpolação e calendário, montagem da curva da data, gravação dos pontos no dado curva com versão e proveniência, e interpolação sob demanda com domínio até o último ponto ou o horizonte.
- `curve-extension-models`: catálogo de modelos de construção, interpolação e calendário. Inclui modelos Java por pacote, criação e sobrescrita por Groovy, versões, ativação com validação, sandbox e rastreabilidade do modelo usado.
- `curve-engine-api`: endpoints do engine identificados por código da curva + data-base (construir, consultar a curva gravada, interpolar prazos arbitrários) e gestão de modelos Groovy por tipo e nome.
- `b3-ready-curve-model`: modelo de construção `PRONTA_TS_B3`, que usa os 278 vértices prontos do `TaxaSwap.txt` para PRE, DCL, PTX, DPL e INP. Inclui a semântica de cada curva (unidade, convenção, interpolação, extrapolação, arredondamento).
- `ntnb-anbima-curve-model`: modelo de construção `NTNB_BOOTSTRAP_ANBIMA` — bootstrap da curva NTN-B a partir das taxas indicativas por título da ANBIMA: montagem do fluxo de caixa real indexado ao IPCA, resolução sequencial da taxa zero por vencimento, e tratamento de título sem preço na data.
- `sofr-bloomberg-curve-model`: modelo de construção `SOFR_ZERO_BLOOMBERG` — leitura dos nós do curve member Bloomberg (`S0490Z <tenor> BLC2 Curncy`) por tenor, conversão de tenor em vértice de data via calendário `UnitedStates`, e montagem do dado curva sem bootstrap (o valor já é taxa zero).

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas na develop. -->

## Impact

- **services/engine:** reorganização de `domain` (novos pacotes `curva`, `construcao`, `interpolacao`, `calendario`, `matematica`) e reescrita de `ConstruirCurvaService` e `CalcularCurvaService`. Controllers e DTOs REST passam a usar código + data. `GroovyDynamicModelCompiler` e `ModeloUploadController` viram um carregador genérico com persistência. Nova tabela de scripts de modelo. O engine passa a gravar só `tDadoCurva` (pontos) e deixa de gravar `tDadoVertcCurva` e `tMtrizCurva`. `tCurvaData` não é gravada nesta fase.
- **Banco:** `tConfgCurva.cRotnaCalc` passa a ser usado. `tParmConfgCurva` recebe os parâmetros de convenção, unidade, calendário, extrapolação, horizonte e arredondamento. Nova tabela para os scripts Groovy de modelo. Nova tabela `tSerieTituloNtnb` (estrutura de cupom por série da NTN-B), lida só pelo engine.
- **Dependências fora do engine (outros changes).** O engine só lê as tabelas brutas; quem as preenche são o conector e o processor, em changes próprios:
  - B3: o catálogo do conector hoje publica a DCL e a DPL como DOL e descarta a PTX e o INP. Precisa passar a classificar pelo código exato do `TaxaSwap.txt`, e o processor precisa gravar em `tBtrsCurvaPrimr` (hoje grava em `mkt.B3CurveRaw`).
  - NTN-B: `tAnbmaCurvaPrimr` precisa trazer o vencimento de cada título (`dVctoTitulo`, design D11), preenchido pela ingestão ANBIMA.
  - SOFR: tabela bruta dos nós do `S0490Z` por tenor (contrato de leitura no design D15), com o feeder e a ingestão correspondentes. O feeder ainda não foi localizado.
- **Consumidores da API do engine:** qualquer cliente dos endpoints atuais por corpo JSON precisa migrar para as rotas por código + data (BREAKING).
- **Fora de escopo: cadastro de curva e provedor.** O engine só **lê** `tCurvaMercd`, `tCurvaPrvdr`, `tConfgCurva` e `tParmConfgCurva`; nunca cria nem edita curva de mercado ou provedor. No sistema real, quem faz esse CRUD é um serviço próprio (`acts-srv-curvas`). O equivalente no poc (`services/curve-api`, hoje renomeado `curve-api-legado`) também nunca escreveu cadastro. O `services/curves` que fará esse CRUD é uma mudança futura, à parte desta. Nesta mudança, o cadastro das 7 curvas é inserido por SQL direto, só para viabilizar os testes do engine (ver `cadastro-exemplo.md`) — isso não representa como o cadastro é criado em produção. O que o engine grava e edita pela própria API são só os **pontos** da curva (`tDadoCurva`), que são dado, não cadastro.
