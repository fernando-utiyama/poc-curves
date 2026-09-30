## Purpose

Mantém o catálogo de modelos de construção, de interpolação e de calendário do engine, com tipos e nomes compatíveis com o QuantLib, os contratos que todo modelo implementa e os modelos nativos em Java. Os scripts Groovy que complementam ou sobrescrevem os nativos (versões, validação, contenção) estão na change `engine-modelos-curva`.

## ADDED Requirements

### Requirement: Três tipos de modelo identificados por nome
O catálogo SHALL ter exatamente três tipos de modelo, cada um identificado por nome dentro do tipo:
- **construção**: o nome é `tConfgCurva.cMotorCalc`;
- **interpolação**: o nome é `tConfgCurva.cRotnaCalc`;
- **calendário**: o nome é o parâmetro `CALENDARIO`.

O mesmo nome MAY existir em tipos diferentes. Bases de interpolação, `DayCounter`, cotação e políticas de extrapolação SHALL ser só nativos, sem sobrescrita por Groovy nesta fase.

#### Scenario: Resolução por tipo
- **WHEN** existe um interpolador chamado `Linear` e nenhum calendário com esse nome
- **THEN** a resolução do interpolador `Linear` funciona, e a do calendário `Linear` falha como nome inexistente

### Requirement: Contratos dos modelos
Todo modelo, nativo ou Groovy, SHALL implementar um destes contratos:
- **Construção:** `List<VerticeConstruido> construir(ContextoConstrucao ctx, MemoriaCalculo memoria)`. `VerticeConstruido` tem data, valor sem arredondamento e `diasUteisPublicados` (inteiro, nulo quando a fonte não publica dias úteis), obedecido pelo pipeline (spec `curve-build-pipeline`). `ContextoConstrucao` fornece o cadastro, a data-base, o calendário resolvido e o leitor de insumos, que é o único acesso às tabelas brutas (`tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr`). Para curva derivada (spec `curve-build-pipeline`), o contexto SHALL fornecer também `curvaComponente(String papel)`: a curva componente na mesma data-base, montada a partir dos pontos gravados em `tDadoVertcCurva` com o cadastro vigente da curva componente, pronta para interpolar, e com o `hashPontos` dos pontos lidos; um modelo não derivado que chame `curvaComponente` falha com `MODELO_FALHOU`. Todo modelo de construção SHALL declarar a fonte e o produto que aceita, e o modelo derivado declara a fonte `TCEN` e a lista de papéis. Falhas SHALL ser sinalizadas por exceções do engine que carregam o código de erro (`INSUMO_AUSENTE`, `INSUMO_INVALIDO` ou `MODELO_FALHOU`).
- **Interpolação:** `BigDecimal valor(BigDecimal x, List<BigDecimal> xs, List<BigDecimal> ys, MemoriaCalculo memoria)`, chamado só com `xs[0] <= x <= xs[último]`. Os interpoladores locais (`Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`) SHALL estender uma base comum que localiza o segmento e chama o ponto de extensão `protected BigDecimal valorNoSegmento(BigDecimal w, BigDecimal yEsquerda, BigDecimal yDireita)`. A política `FlatForward` SHALL chamar esse mesmo método com `w` fora de `[0, 1]`.
- **Calendário:** `boolean isBusinessDay(LocalDate data)` e `String mercado()`, que declara o mercado aceito em `MERCADO_CALENDARIO`, com o ponto de extensão `protected Set<LocalDate> feriados(int ano)`. Contagem de dias úteis, `advance` e `adjust` SHALL ser implementados na base comum a partir de `isBusinessDay`.

#### Scenario: Sobrescrita só do segmento
- **WHEN** um script Groovy ativo estende `LogLinear` e sobrescreve só `valorNoSegmento`
- **THEN** os prazos iguais aos pontos continuam devolvendo o valor do ponto, e os prazos interpolados e extrapolados por `FlatForward` usam a fórmula do script

### Requirement: Tipos e nomes compatíveis com o QuantLib
Os tipos usados pelos modelos e pelo cadastro SHALL ter os nomes de tipo e de constante do QuantLib:
- `Compounding`: `Simple`, `Compounded`, `Continuous`, `SimpleThenCompounded`, `CompoundedThenSimple` (os dois últimos existem no enum, mas o cadastro os rejeita nesta fase);
- `Frequency`: de `NoFrequency` a `OtherFrequency`, com os mesmos valores numéricos;
- `BusinessDayConvention`: `Following`, `ModifiedFollowing`, `Preceding`, `ModifiedPreceding`, `Unadjusted`, `HalfMonthModifiedFollowing`, `Nearest`;
- `DayCounter`: `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` (constantes de um enum);
- calendários: `Brazil` com o mercado `Settlement`; `UnitedStates` com o mercado `FederalReserve`;
- interpoladores: `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`;
- bases de interpolação: `Discount`, `ZeroYield` e as extensões próprias `CompoundFactor` e `Price`.

A implementação MUST ser própria, em Java, sem depender da biblioteca QuantLib. Prazos e horizontes (`nD`, `nW`, `nM`, `nY`) SHALL usar o `java.time.Period` do próprio Java, sem tipo próprio de período nem de unidade de tempo.

#### Scenario: Script no estilo QuantLib
- **WHEN** um script Groovy usa `Compounding.Compounded`, `Frequency.Annual`, `DayCounter.Business252`, `new Brazil()` e `BusinessDayConvention.Following`
- **THEN** o script compila e executa sem adaptação de nomes

### Requirement: Modelos nativos
O engine SHALL disponibilizar, sem importação:
- construção: `PRONTA_TS_B3`, `NTNB_BOOTSTRAP_ANBIMA`, `SOFR_ZERO_BLOOMBERG`;
- interpolação: `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`;
- calendário `Brazil`, mercado `Settlement`, com sábados, domingos e os feriados: 1º de janeiro; segunda e terça de Carnaval (48 e 47 dias antes da Páscoa); Sexta-feira Santa (2 dias antes da Páscoa); 21 de abril; 1º de maio; Corpus Christi (60 dias após a Páscoa); 7 de setembro; 12 de outubro; 2 de novembro; 15 de novembro; 20 de novembro, a partir de 2024; 25 de dezembro. A Páscoa SHALL ser calculada pelo algoritmo de Meeus/Jones/Butcher;
- calendário `UnitedStates`, mercado `FederalReserve`, com sábados, domingos e os feriados: New Year's Day (1º de janeiro); Martin Luther King Jr. Day (3ª segunda de janeiro, a partir de 1998); Washington's Birthday (3ª segunda de fevereiro); Memorial Day (última segunda de maio); Juneteenth (19 de junho, a partir de 2022); Independence Day (4 de julho); Labor Day (1ª segunda de setembro); Columbus Day (2ª segunda de outubro); Veterans Day (11 de novembro); Thanksgiving (4ª quinta de novembro); Christmas (25 de dezembro). Feriado de data fixa que cai no domingo SHALL passar para a segunda seguinte; o que cai no sábado não é observado.

#### Scenario: Engine recém-iniciado
- **WHEN** o engine sobe sem nenhum script Groovy
- **THEN** as sete curvas do primeiro objetivo podem ser construídas com os modelos nativos

#### Scenario: Feriado de data fixa no domingo
- **WHEN** 4 de julho cai num domingo
- **THEN** a segunda-feira seguinte não é dia útil no `UnitedStates`/`FederalReserve`

