## Purpose

Mantém o catálogo de modelos de construção, de interpolação e de calendário do engine, com tipos e nomes compatíveis com o QuantLib. Os modelos nativos em Java podem ser complementados ou sobrescritos por scripts Groovy, com versão, validação, contenção e rastreabilidade. Fixa os contratos que um script implementa, os estados de uma versão e os modelos nativos.

## ADDED Requirements

### Requirement: Três tipos de modelo identificados por nome
O catálogo SHALL ter exatamente três tipos de modelo, cada um identificado por nome dentro do tipo:
- **construção**: o nome é `tConfgCurva.cMotorCalc`;
- **interpolação**: o nome é `tConfgCurva.cRotnaCalc`;
- **calendário**: o nome é o parâmetro `CALENDARIO`.

O mesmo nome MAY existir em tipos diferentes. Grandezas, `DayCounter`, cotação e políticas de extrapolação SHALL ser só nativos, sem sobrescrita por Groovy nesta fase.

#### Scenario: Resolução por tipo
- **WHEN** existe um interpolador chamado `Linear` e nenhum calendário com esse nome
- **THEN** a resolução do interpolador `Linear` funciona, e a do calendário `Linear` falha como nome inexistente

### Requirement: Contratos dos modelos
Todo modelo, nativo ou Groovy, SHALL implementar um destes contratos:
- **Construção:** `List<PontoConstruido> construir(ContextoConstrucao ctx, MemoriaCalculo memoria)`. `PontoConstruido` tem data e valor sem arredondamento. `ContextoConstrucao` fornece o cadastro, a data-base, o calendário resolvido e o leitor de insumos, que é o único acesso às tabelas brutas (`tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `mkt.SofrCurveRaw`). Falhas SHALL ser sinalizadas por exceções do engine que carregam o código de erro (`INSUMO_AUSENTE`, `INSUMO_INVALIDO` ou `MODELO_FALHOU`).
- **Interpolação:** `BigDecimal valor(BigDecimal x, List<BigDecimal> xs, List<BigDecimal> ys, MemoriaCalculo memoria)`, chamado só com `xs[0] <= x <= xs[último]`. Os interpoladores locais (`Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`) SHALL estender uma base comum que localiza o segmento e chama o ponto de extensão `protected BigDecimal valorNoSegmento(BigDecimal w, BigDecimal yEsquerda, BigDecimal yDireita)`. A política `FlatForward` SHALL chamar esse mesmo método com `w` fora de `[0, 1]`.
- **Calendário:** `boolean isBusinessDay(LocalDate data)`, com o ponto de extensão `protected Set<LocalDate> feriados(int ano)`. Contagem de dias úteis, `advance` e `adjust` SHALL ser implementados na base comum a partir de `isBusinessDay`.

#### Scenario: Sobrescrita só do segmento
- **WHEN** um script Groovy ativo estende `LogLinear` e sobrescreve só `valorNoSegmento`
- **THEN** os prazos iguais aos pontos continuam devolvendo o valor do ponto, e os prazos interpolados e extrapolados por `FlatForward` usam a fórmula do script

### Requirement: Tipos e nomes compatíveis com o QuantLib
Os tipos usados pelos modelos e pelo cadastro SHALL ter os nomes de tipo e de constante do QuantLib:
- `Compounding`: `Simple`, `Compounded`, `Continuous`, `SimpleThenCompounded`, `CompoundedThenSimple` (os dois últimos existem no enum, mas o cadastro os rejeita nesta fase);
- `Frequency`: de `NoFrequency` a `OtherFrequency`, com os mesmos valores numéricos;
- `BusinessDayConvention`: `Following`, `ModifiedFollowing`, `Preceding`, `ModifiedPreceding`, `Unadjusted`, `HalfMonthModifiedFollowing`, `Nearest`;
- `TimeUnit`: `Days`, `Weeks`, `Months`, `Years`;
- `DayCounter`: `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360`;
- calendários: `Brazil` com o mercado `Settlement`; `UnitedStates` com o mercado `FederalReserve`;
- interpoladores: `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`;
- grandezas: `Discount`, `ZeroYield` e as extensões próprias `CompoundFactor` e `Price`.

A implementação MUST ser própria, em Java, sem depender da biblioteca QuantLib.

#### Scenario: Script no estilo QuantLib
- **WHEN** um script Groovy usa `Compounding.Compounded`, `Frequency.Annual`, `new Business252(new Brazil(Brazil.Market.Settlement))` e `BusinessDayConvention.Following`
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

### Requirement: Ordem de resolução
Ao resolver um modelo por tipo e nome para uma curva, o catálogo SHALL usar, nesta ordem:
1. a versão fixada no cadastro da curva (`VERSAO_SCRIPT_CONSTRUCAO`, `VERSAO_SCRIPT_INTERPOLACAO` ou `VERSAO_SCRIPT_CALENDARIO`), que MUST ter passado na validação;
2. a versão Groovy `ATIVA` com aquele tipo e nome;
3. o modelo Java nativo com aquele tipo e nome.

Se nenhum existir, a resolução MUST falhar com `CADASTRO_INVALIDO`, informando tipo e nome.

#### Scenario: Sobrescrita de um interpolador nativo
- **WHEN** existe uma versão `ATIVA` de interpolação chamada `LogLinear`
- **THEN** toda curva com `LogLinear` sem versão fixada usa o script

#### Scenario: Modelo novo só em Groovy
- **WHEN** um script de construção chamado `EXEMPLO_SO_GROOVY`, sem equivalente Java, é ativado
- **THEN** uma curva cadastrada com esse modelo pode ser construída

#### Scenario: Nome inexistente
- **WHEN** o cadastro de uma curva indica o calendário `XPTO` e não há Java nem Groovy com esse nome
- **THEN** a resolução falha com `CADASTRO_INVALIDO`, informando o tipo calendário e o nome `XPTO`

#### Scenario: Desativação volta ao nativo
- **WHEN** a versão `ATIVA` que sobrescrevia `LogLinear` é desativada
- **THEN** as consultas seguintes usam o `LogLinear` nativo

#### Scenario: Teste numa curva só
- **WHEN** o cadastro da `DPL` fixa a versão 4 (validada, não ativa) do interpolador `LogLinear`, e a versão ativa é a 3
- **THEN** a `DPL` usa a versão 4, e a `PRE` continua com a versão 3

### Requirement: Armazenamento dos scripts no Blob Storage
Os scripts SHALL ficar no Azure Blob Storage já usado pelo projeto (endpoint e container configurados por `engine.blob.endpoint` e `engine.blob.container`, com autenticação por Managed Identity, sem chave ou connection string em configuração de produção; Azurite só no perfil local), na estrutura:
- `groovy-models/{tipo}/{nome}/v{versao}.groovy`: conteúdo de uma versão, gravado uma única vez (escrita condicional `If-None-Match: *`) e nunca alterado nem apagado;
- `groovy-models/{tipo}/{nome}/estado.json`: `{ "ativa": número ou null, "versoes": { "{versao}": { "status", "hash", "autor", "criadoEm", "aprovador", "atualizadoEm", "motivo" } } }`.

`{tipo}` é `construcao`, `interpolacao` ou `calendario`. Toda alteração de `estado.json` SHALL ser uma escrita condicional pelo ETag lido (`If-Match`). Se outra instância tiver alterado o arquivo antes, a operação MUST falhar com `ESTADO_SCRIPT_CONCORRENTE`, sem alterar nada. O engine MUST NOT guardar scripts no banco.

#### Scenario: Duas ativações simultâneas
- **WHEN** duas requisições ativam versões diferentes do mesmo modelo ao mesmo tempo, em instâncias diferentes
- **THEN** uma é gravada e a outra falha com `ESTADO_SCRIPT_CONCORRENTE`, e `estado.json` tem uma única versão `ATIVA`

### Requirement: Mesma versão em todas as instâncias
Toda resolução de modelo SHALL consultar o `estado.json` do tipo e nome no Blob antes de considerar o modelo Java nativo. A leitura SHALL usar cache local por instância com validade de `engine.groovy.cache-estado-segundos` (padrão 30); expirado o cache, a instância relê o arquivo (requisição condicional por ETag). A inexistência de `estado.json` também SHALL ficar em cache pelo mesmo tempo. O conteúdo compilado de uma versão SHALL ficar em memória por (tipo, nome, versão, hash), sem expiração, porque versões são imutáveis. Ao carregar uma versão, o engine SHALL conferir o SHA-256 do conteúdo contra o `hash` do `estado.json`; se diferir, a operação MUST falhar com `MODELO_FALHOU`, sem executar o script. Se o Blob estiver inacessível, vale a spec `curve-engine-resilience`: a instância segue com o último estado lido e, só quando não tem estado nem a versão necessária em memória, usa o nativo, com erro no log e `estadoScript` = `DESCONHECIDO` na proveniência.

#### Scenario: Ativação chega às demais instâncias
- **WHEN** a versão 3 de `LogLinear` é ativada pela instância A
- **THEN** em até 30 segundos todas as instâncias usam a versão 3, e cada resposta informa a versão usada

#### Scenario: Blob fora do ar
- **WHEN** o Blob está inacessível e o cache de estado da instância venceu
- **THEN** a instância continua usando a versão ativa conhecida de `LogLinear`, registra o aviso, e informa `estadoScript` = `DESATUALIZADO`

#### Scenario: Conteúdo alterado no Blob
- **WHEN** o arquivo `v3.groovy` foi alterado diretamente no Blob e o hash não bate com o `estado.json`
- **THEN** a operação falha com `MODELO_FALHOU`, informando a divergência de hash

### Requirement: Versões e estados
Cada envio SHALL criar a próxima versão (1, 2, 3...) do tipo e nome, com conteúdo, hash SHA-256 do conteúdo, autor e data, e SHALL nunca ser apagada. Os estados e transições SHALL ser:
- envio → `RASCUNHO`;
- validação de `RASCUNHO` → `VALIDADA` ou `REPROVADA`;
- ativação de `VALIDADA` ou `INATIVA` → `ATIVA`, e a versão que estava `ATIVA` passa a `INATIVA`, e quem ativou é gravado como `aprovador` (pode ser o próprio autor);
- desativação de `ATIVA` → `INATIVA`.

Qualquer outra transição MUST resultar em `SCRIPT_INVALIDO`. Cada tipo e nome SHALL ter no máximo uma versão `ATIVA`. Depois de um reinício, ou numa instância nova, as versões `ATIVA` e as fixadas em cadastro SHALL ser usadas sem reenvio, porque são lidas do Blob.

#### Scenario: Ativação de nova versão
- **WHEN** a versão 3 é ativada enquanto a versão 2 estava ativa
- **THEN** a versão 3 fica `ATIVA` e a versão 2 fica `INATIVA`

#### Scenario: Reinício do engine
- **WHEN** o engine reinicia com uma versão `ATIVA` do interpolador `LogLinear`
- **THEN** o script continua sendo usado, sem novo envio

### Requirement: Validação
A validação SHALL compilar o script, verificar que ele implementa o contrato do tipo e executá-lo:
- interpolação: `valor` nos pontos `xs = [1, 2, 3]`, `ys = [0,9; 0,8; 0,7]`, em `x = 1,5` e `x = 2,5`;
- calendário: `isBusinessDay` para todos os dias do ano corrente;
- construção: exige `codigo` e `dataBase` no corpo da validação e executa a simulação (spec `curve-calculation-memory`) dessa curva e data com a versão em validação, que precisa terminar com `status` = `OK`.

A versão SHALL passar a `VALIDADA` só se tudo terminar sem erro dentro do tempo limite; senão, `REPROVADA`, com o motivo (e a linha do script, quando houver) devolvido sem stack trace.

#### Scenario: Script que não compila
- **WHEN** a validação de um script com erro de sintaxe é pedida
- **THEN** a versão fica `REPROVADA` com a mensagem do compilador e a linha

#### Scenario: Script do tipo errado
- **WHEN** um script de calendário é enviado como interpolação
- **THEN** a versão fica `REPROVADA`, informando que o script não implementa o contrato de interpolação

### Requirement: Contenção da execução
Um script SHALL poder usar somente os pacotes do engine `curva`, `quantlib`, `matematica`, `calendario`, `interpolacao`, `construcao` e `memoria`, e `java.math`, `java.time`, `java.util`. Um script que referencie outro pacote, ou que tente acessar arquivo, rede, processo, threads, reflexão ou propriedades do sistema, MUST ser reprovado na validação. Cada chamada a um script MUST ser interrompida após o tempo limite (propriedade `engine.groovy.timeout-segundos`, padrão 5), e a operação falha com `MODELO_FALHOU`, informando o modelo e o tempo.

#### Scenario: Script tenta acessar a rede
- **WHEN** um script que abre uma conexão de rede é validado
- **THEN** a versão fica `REPROVADA` por violação de contenção

#### Scenario: Script em laço infinito
- **WHEN** um script ativo não termina dentro do tempo limite durante uma construção
- **THEN** a execução é interrompida e a construção falha com `MODELO_FALHOU`, informando o modelo e o tempo esgotado
