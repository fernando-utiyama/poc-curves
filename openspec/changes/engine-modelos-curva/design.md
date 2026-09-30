## Context

A motivação está no proposal e o comportamento normativo (fórmulas, regras, erros, rotas) nas specs. Este documento registra o estado atual e as decisões de implementação. Em caso de dúvida, a spec prevalece.

**Engine na `develop` (`services/engine`, ~1.900 linhas de domínio):**
- `ConstruirCurvaService` resolve o builder pelo `cMotorCalc`, lê os insumos de `tDadoCurva` e grava `tDadoVertcCurva`, com um cabeçalho de versão em `tMtrizCurva` (tabela que, no modelo oficial, é de superfícies).
- O `ComposableCurveBuilder` não interpola. O `CurveBuilderRegistry` cria um builder por interpolador, mas descarta o interpolador.
- A conversão taxa↔fator está em 4 classes, toda em `double`.
- A interpolação sob demanda (`CalcularCurvaService`) usa os enums `MetodoInterpolacao` e `PoliticaExtrapolacao`, vindos do request.
- O Groovy (`GroovyDynamicModelCompiler` + `ModeloUploadController`) só aceita `CurveBuilderStrategy`, fica só em memória e usa sandbox por lista de bloqueio.
- Não há biblioteca de planilha nem cliente de Blob Storage no engine (o `azure-storage-blob` só está na gestão de dependências do pom raiz).

**Schema oficial de curvas de mercado (colunas usadas):**

| Tabela | Uso |
|---|---|
| `tCurvaMercd` | `cTickerIndcd` (nome, PK de todas as FKs), `cTickerIdtfdUnic` (código), `cTpoVlr`, `cNormaDia`, `cTpoJuro`; o engine escreve só `dBaseReft` e `cUsuarCalc` (D23) |
| `tCurvaPrvdr` | origem: `iPrvdrDados`, `cPrvdrMercd`, `cTickerPrvdr`, `cPriorCsumo` |
| `tConfgCurva` | `cMotorCalc`, `cRotnaCalc`, vigência `dInicVgcia`/`dValidAte` |
| `tConfgCurva.cModDado` | parâmetros da curva em JSON (D4) |
| `tBtrsCurvaPrimr` | bruto B3: `cDiaCorri`, `cDiaUtil`, `vPrecoTx` |
| `tAnbmaCurvaPrimr` | bruto ANBIMA: `vVertcCurva`, `vPrecoTx` |
| `tDadoVertcCurva` | **curva construída**: os pontos (vértices), gravados pelo engine e pela edição manual no `services/curves` (D36, D39): `dVertcReft`, `vPrecoTx`, `cDiaUtil`, `cQtdDiaPer`, `cQtdDiaReft`, `vFatorDia`, `vFatorAcum` |
| `tDadoCurva` | **curva interpolada**: um valor por dia corrido até o fim do domínio, gravada só pelo engine a partir dos pontos (D6): `dBaseReft`, `cTickerIndcd`, `dVertcReft`, `vPrecoTx` |

**Modelo de dados.** `tDadoVertcCurva` guarda a **curva construída**: os pontos (vértices) da curva na data-base, com dias e fatores (D36). `tDadoCurva` guarda a **curva interpolada**: um valor por dia corrido, calculado dos pontos (D6). `tCurvaData` não é usada e sai do schema (change `banco-curvas-ajustes`), e `tMtrizCurva` (reservada a superfícies) não é gravada.

```
tabela bruta ─ modelo de construção ─► tDadoVertcCurva (pontos) ─ interpolação ─► tDadoCurva (dia a dia)
                  consulta pela API ─► interpolação em tempo de execução a partir de tDadoVertcCurva
                  simulação ──────────► modelo de construção + interpolação em memória, sem gravar
```

## Goals / Non-Goals

**Goals:**
- Núcleo de curvas em Java puro dentro do engine, com tipos e nomes do QuantLib, estendível por Groovy nos três tipos de modelo.
- Cadastro como única fonte de variação entre curvas.
- As 7 curvas do primeiro objetivo: PRE, DCL, DPL, INP e PTX do TaxaSwap (conferidas contra o arquivo), NTN-B por bootstrap e SOFR das zero rates Bloomberg.
- Investigação em produção sem acesso ao banco: simulação sem gravar e memória de cálculo em planilha.

**Non-Goals:**
- ETTJ IPCA paramétrica (Svensson) da ANBIMA e bootstrap de contratos futuros de SOFR.
- Data quality (validação estatística, checagens de sanidade, aprovação) e publicação em Kafka: data quality será uma feature futura. **Nesta fase, curva gravada é curva liberada para consumo**; não há estado de aprovação.
- Cache de curva.
- Módulo Maven separado para o núcleo.
- CRUD de cadastro de curva e provedor e edição manual dos pontos: são do `services/curves` (`acts-srv-curvas` no sistema real), no change `curves-cadastro-curvas`; o CRUD de provedores é de outro dev. O engine só lê o cadastro. Os testes usam fixtures com o cadastro definido nas specs.
- Preencher as tabelas brutas: é do conector e do processor (D10).

## Decisions

### D1. Arquitetura hexagonal, Java 21 e código nativo
O engine é hexagonal: o **domínio** não conhece Spring, JPA, Jackson, Blob nem POI; a **aplicação** tem os casos de uso e as portas; os **adaptadores** ligam as portas ao mundo (REST, banco, Blob, planilha, Groovy).
```
domain/                 Java puro, sem framework
  curva/                records do negócio, com os nomes das tabelas (CurvaMercado, CurvaProvedor, ConfiguracaoCurva,
                        VerticeConstruido, DadoVerticeCurva, DadoCurva,
                        Proveniencia, Aviso, ResultadoConstrucao sealed...), enums de erro e aviso, hashPontos
  quantlib/             enums com nomes do QuantLib: Compounding, Frequency, BusinessDayConvention, DayCounter;
                        InterestRate (record)
  matematica/           DecimalMath: ponte entre BigDecimal e StrictMath (pow, ln, exp)
  calendario/           Calendario (base), Brazil, UnitedStates, CalendarioPorLista
  interpolacao/         BaseInterpolacao e Extrapolacao (enums), Interpolador, InterpoladorPorSegmento, Linear, LogLinear,
                        BackwardFlat, ForwardFlat, Cubic, EixoDiasUteis, CurvaInterpolada, InterpolacaoDadoCurva
  construcao/           ModeloConstrucao, ContextoConstrucao, ProntaTsB3, SofrZeroBloomberg, NtnbBootstrapAnbima
  memoria/              MemoriaCalculo e as suas linhas (records)
application/
  port/in/              casos de uso (interfaces)
  port/out/             portas de saída, uma por tabela (CurvaMercadoPort, CurvaProvedorPort, ConfiguracaoCurvaPort,
                        DadoVerticeCurvaPort, DadoCurvaPort) e as de scripts, planilha e eventos
  service/              implementação dos casos de uso (transação, orquestração, paralelismo)
adapter/
  in/rest/              controllers, DTOs (records), erros, correlação, segurança, serialização
  out/persistence/      entidades JPA e repositórios, implementando as portas
  out/blob/  out/planilha/  out/groovy/
```
**Código nativo do Java sempre que existir:** `java.time` (`LocalDate`, `Period`, `DayOfWeek`), `BigDecimal` e `RoundingMode`, `StrictMath` (`pow`, `ln` e `exp`, D7) (os modos `HALF_UP`, `HALF_EVEN` e `DOWN` do cadastro são os nomes do `RoundingMode`), records, sealed interfaces, pattern matching, `HexFormat`, `MessageDigest`, virtual threads. **Código próprio só onde o Java não tem:** os enums e tipos com nomes do QuantLib (para os scripts Groovy usarem os nomes do mercado), os calendários de feriados, os interpoladores (estendíveis por Groovy) e os modelos de construção. **Alternativas rejeitadas:** QuantLib-SWIG (JNI), que traz binário nativo e não roda em `BigDecimal`; tipos próprios de período, unidade de tempo, arredondamento e relógio, que duplicariam o `java.time` e o `java.math`.

**Virtual threads:** as requisições rodam em virtual threads (`spring.threads.virtual.enabled`), e o paralelismo da construção da data e da situação usa `Executors.newVirtualThreadPerTaskExecutor()` limitado por `Semaphore` (o limite protege o pool de conexões do banco, não as threads).

### D2. Interpolação = base de interpolação + interpolador + DayCounter do eixo
O interpolador é puro: recebe `(x, xs, ys)` e não sabe nada de juros. A base de interpolação converte ponto ↔ `y`, o `DayCounter` do eixo gera `x`, e a cotação (`InterestRate`) converte taxa ↔ fator. As funções do Manual de Curvas B3 viram configuração (tabela na spec `curve-build-pipeline`). A 1.4.1 (interpolação geométrica de `(1+i)`) não tem mapeamento e fica para uma base de interpolação nova em Java, numa mudança futura. `ForwardRate` fica fora desta fase. **Alternativa rejeitada:** um interpolador por função B3, que multiplica classes e amarra a base de dias.

### D3. Extrapolação por lado, fora do interpolador
A curva aplica a política de início ou de fim; o interpolador só é chamado dentro de `[x_1, x_n]`. `FlatForward` chama `valorNoSegmento` do interpolador local com `w` fora de `[0, 1]`, o que reproduz 1.4.6, 1.4.7 e 1.4.10 com a mesma fórmula da interpolação; por isso exige `Linear` ou `LogLinear`. `FlatValue` repete o valor do ponto (taxa, preço ou pontos), e não a base de interpolação, como 1.4.8 e 1.4.9. **Alternativa rejeitada:** `enableExtrapolation()` do QuantLib, que não permite políticas diferentes no início e no fim.

### D4. Cadastro
Itens, colunas, valores aceitos e obrigatoriedade estão na spec `curve-build-pipeline`. Decisões:
- **Sem mudança de schema nesta fase.** `tParmConfgCurva` tem PK só em `cldtfdConfg`, então guarda um parâmetro por configuração, e o cadastro tem cerca de 14. Os parâmetros ficam como um objeto JSON em `tConfgCurva.cModDado` (`VARCHAR(1024)`, sobra espaço), coluna que o engine antigo usava para o "modo de dado" e que o engine novo não usa mais. Vantagem: o JSON fica na mesma linha da vigência, então muda junto com ela. Reaproveitar a coluna precisa ser confirmado com o dono do schema. O alvo ideal, quando o banco puder mudar, é `tParmConfgCurva` em chave/valor com PK `(cldtfdConfg, cConfgIdtfd)`.
- Chave desconhecida é erro: um nome digitado errado (`EXTRAPOLACAO_FINAL`) cairia silenciosamente no padrão `Disabled`.
- Só dois padrões existem: `Disabled` para as extrapolações. Todo o resto é obrigatório.
- A vigência de `tConfgCurva` torna reprodutível o reprocessamento de uma data antiga.
- Origem: a construção automática usa só a linha de menor `cPriorCsumo`; troca automática de fonte fica fora. O usuário pode construir por uma origem secundária (D38).
- Os parâmetros em texto antigos (`CONVENCAO`, `MOD_DADO`, `HORIZONTE_MAX_ANOS`) deixam de ser lidos.

**Alternativas rejeitadas:** colunas novas em `tConfgCurva` (mudam o schema); parâmetros no Blob (separariam o cadastro do banco e da vigência).

### D5. Modelo de construção
O contrato está na spec `curve-extension-models`. O modelo recebe um `ContextoConstrucao` com o cadastro, a data-base, o calendário e o `CurvaPrimariaPort`, que é o único acesso às tabelas brutas. Scripts Groovy não acessam o banco. Cada modelo declara a fonte e o produto que aceita (`PRONTA_TS_B3`: `B3`/`TS`; `NTNB_BOOTSTRAP_ANBIMA`: `ANBIMA`/`MS`; `SOFR_ZERO_BLOOMBERG`: `BLOOMBERG`/`BLC2`), e o pipeline rejeita cadastro que aponte um modelo para outra fonte. O modelo devolve pontos sem arredondamento; o pipeline arredonda e grava.

### D6. Gravação: pontos e curva interpolada, numa transação travada
A construção grava os pontos arredondados em `tDadoVertcCurva` e, na mesma transação, a curva interpolada em `tDadoCurva`: um valor por dia corrido, do início ao fim do domínio (cerca de 12.400 linhas na PRE, com o último vértice em 2060). Dia corrido, e não útil, para quem consome achar uma linha em qualquer data sem depender do calendário: com eixo em dias úteis, o fim de semana e o feriado repetem o valor do dia útil anterior. A interpolada é saída: o engine nunca a lê para calcular, e ela sai idêntica ao que a rota de interpolação devolve para a mesma data. Depois de uma edição manual, o `services/curves` pede ao engine a regravação da interpolada (rota própria); com o engine fora, a edição fica gravada, e a interpolada, desatualizada (`INTERPOLADA_DESATUALIZADA`) até a regravação. A transação começa com um `SELECT` com trava de escrita (`PESSIMISTIC_WRITE`, tempo limite de 30 segundos) na linha da curva em `tCurvaMercd`, e a edição manual de pontos no `services/curves` usa a mesma trava. Isso serializa construções e edições da mesma curva entre réplicas sem tabela nova. Quem não obtém a trava recebe `CONSTRUCAO_EM_ANDAMENTO`. A simulação não trava nada.

A proveniência vai na resposta e no log `CURVA_GRAVADA` (D23); o banco não tem tabela para ela, porque `tMtrizCurva` é de superfícies.

**Alternativa rejeitada:** gravar a curva diária em `tCurvaData`, como numa versão anterior deste design. `tDadoCurva` já é a tabela da curva interpolada, e `tCurvaData` sai do schema.

### D7. Precisão
Híbrido: valores, somas, divisões e arredondamento em `BigDecimal` com `MathContext.DECIMAL128`; `pow` fracionário, `ln` e `exp` em `double` pelo `StrictMath`, com expoente inteiro pelo `BigDecimal.pow`. O `StrictMath` é determinístico em qualquer máquina, então o mesmo insumo dá o mesmo `hashPontos` em todas as instâncias. O `double` tem cerca de 15 a 16 dígitos significativos: sobra para as taxas de 7 ou 8 casas, e as últimas casas dos fatores de 16 casas carregam a imprecisão do `double`. O arredondamento é o `setScale` do próprio `BigDecimal` com o `RoundingMode` do cadastro. O arredondamento do cadastro vale só para o valor da curva, na gravação e na resposta. Os fatores saem do valor já arredondado, com 16 casas: quem lê a taxa publicada consegue reproduzir o fator. **Alternativas rejeitadas:** tudo em `BigDecimal` com `ln`/`exp` próprios em série (código matemático próprio, contra a preferência por código nativo do Java); `Math` em vez de `StrictMath` (pode variar na última casa entre máquinas, pelos intrinsics da CPU). **Risco aceito:** um valor que caia a menos de ~1e-14 de uma fronteira de arredondamento da 7ª ou 8ª casa pode arredondar para o outro lado; nos vértices não há conta (o valor é o publicado), e o oráculo da B3 compara os vértices.

### D8. Registro genérico e Groovy
`RegistroModelos<T>` é um só para os três tipos, com a resolução: versão fixada → `ATIVA` → Java nativo → erro. Os nativos se registram na subida (`@Component` por modelo).

**Scripts no Blob Storage, não no banco.** O engine roda em no mínimo duas instâncias no Azure, e um script ativado numa instância precisa chegar às outras. Os scripts ficam no Blob já usado pelo projeto, em `groovy-models/{tipo}/{nome}/`: um arquivo imutável por versão (`v{n}.groovy`) e um `estado.json` com a versão ativa e o status, o hash e o autor de cada versão (formato na spec `curve-extension-models`).
- **Propagação:** toda resolução lê o `estado.json` antes do nativo, com cache local de 30 segundos. Uma ativação chega a todas as instâncias em até 30 segundos, sem mensagem entre elas e sem reinício.
- **Cache sem invalidação:** versões são imutáveis (gravadas com `If-None-Match: *`), então a classe compilada fica em memória por (tipo, nome, versão, hash) para sempre. Só o ponteiro de versão ativa expira.
- **Concorrência:** o `estado.json` é gravado com `If-Match` do ETag lido. Duas ativações simultâneas não se sobrescrevem; a segunda recebe `ESTADO_SCRIPT_CONCORRENTE`.
- **Integridade:** o hash do conteúdo é conferido contra o `estado.json` na carga, e alteração manual no Blob é detectada.
- **Blob fora do ar:** a instância segue com o último estado lido; só sem estado nenhum usa o nativo, com erro no log e `estadoScript` = `DESCONHECIDO` (D26).
- **Reprodutibilidade:** versões nunca são apagadas, então qualquer execução passada pode ser reproduzida com a versão informada na proveniência.

**Alternativas rejeitadas:**
- Buscar no Blob só quando o nome não existe localmente: não sobrescreve um nativo (o Java sempre é achado) e não percebe versão nova de um script já carregado.
- Endpoint de recarga: a chamada cai numa instância só, a que o balanceador escolher.
- Tabela no banco (`tScriptModlCurva`): funcionaria para propagar, mas cria tabela nova no schema oficial para um conteúdo que é arquivo.

**Sandbox:**
- `SecureASTCustomizer` com lista **permitida** de imports e receptores (spec `curve-extension-models`);
- `TimedInterrupt` com o tempo limite `engine.groovy.timeout-segundos`.

Isso substitui o `GroovyDynamicModelCompiler` e resolve o débito de segurança registrado: sandbox por lista de bloqueio, endpoint sem autenticação e mensagem de erro exposta.

### D9. API por código + data, sem cache
Rotas, parâmetros, corpos e erros estão na spec `curve-engine-api`. Os controllers antigos são removidos, sem convivência.

Toda consulta relê os pontos de `tDadoVertcCurva` e monta a curva na hora: são no máximo algumas centenas de linhas por curva e data. **Alternativa rejeitada nesta fase:** cache do objeto de curva, que exigiria invalidação entre réplicas na edição e na reconstrução. É fonte de erro sem ganho medido; entra depois, se a latência pedir.

**Código e nome.** A rota por código busca `cTickerIdtfdUnic`, e a rota por nome compara o nome normalizado com `cTickerIndcd`. As duas resolvem para `cTickerIndcd` e usam os mesmos serviços. O nome vai em query, não no path, porque tem espaço, acento e `/`. **Alternativa rejeitada:** escrita pelo nome. O nome tem espaço e acento e pode colidir depois de normalizado (`NOME_AMBIGUO`), então operações que alteram dados ficam presas ao código. O nome é imutável no cadastro, por ser a chave das FKs, e por isso os eventos de log de auditoria trazem sempre o nome, além do código, para o histórico sobreviver a uma troca de código.

**Edição de pontos:** não é do engine. A edição manual é do `services/curves` (change `curves-cadastro-curvas`), que grava os pontos em `tDadoVertcCurva` com a mesma trava por curva de D6, calcula o `hashPontos` pela mesma fórmula e pede ao engine a regravação da curva interpolada. O engine percebe a edição pelos `hashPontos`.

### D10. O engine só lê as tabelas brutas
Os modelos esperam das tabelas brutas o contrato abaixo. Preenchê-las é do conector e do processor, em changes próprios. Nos testes, as tabelas são carregadas por fixture.

| Tabela bruta | O engine espera | Situação hoje |
|---|---|---|
| `tBtrsCurvaPrimr` | uma linha por vértice, `cTickerIndcd` = nome da curva de mercado ligada, em `tCurvaPrvdr`, ao código exato da curva no `TaxaSwap.txt`, `cDiaCorri`, `cDiaUtil`, `vPrecoTx` em percentual | na `develop`, o conector classifica pela descrição (`DCL`/`DPL` viram `DOL`, `PTX`/`INP` são descartados) e o processor grava em `mkt.B3CurveRaw`; corrigido no change `conector-b3-webhook-ingest` |
| `tAnbmaCurvaPrimr` | uma linha por título inteiro (código SELIC terminado em `99`; os desmembrados não são gravados), `cTickerIndcd` = nome da curva de mercado: `vPrecoTx` = taxa indicativa em percentual, `vVertcCurva` = prazo em dias úteis | colunas existem; escala de `vPrecoTx` confirmada pelo arquivo `ms{AAMMDD}.txt` (percentual ao ano); unidade de `vVertcCurva` a confirmar com a ingestão ANBIMA |
| `tBbergCurvaPrimr` | uma linha por nó da SOFR, `cTickerIndcd` = nome da curva de mercado, `cTickerBberg` = `{membro} {tenor} ...`, `vPrecoUlt` = taxa zero em percentual | tabela existe; feeder não localizado; `cTickerBberg` `CHAR(20)` não cabe o ticker completo (change `banco-curvas-ajustes`) |

Além das tabelas, o processor chama o webhook `POST /api/v1/cargas` depois do commit de cada carga, com a quantidade de linhas por código na fonte, e repete com o mesmo `idCarga` até receber 2xx (D22). Para a B3, isso está especificado no change `conector-b3-webhook-ingest` (conector publica uma mensagem por carga; o processor grava `tBtrsCurvaPrimr` e avisa o engine).

### NTN-B (ANBIMA): `NTNB_BOOTSTRAP_ANBIMA`

**Contexto.** Nenhuma referência do projeto faz bootstrap de título com cupom. O `curve-platform` tem `CdiRateHelper` e `Di1RateHelper` (instrumentos zero-cupom) e um `CurveBootstrapper` que só ordena taxas já implícitas. O algoritmo completo está na spec `ntnb-anbima-curve-model`.

### D11. Leitura de `tAnbmaCurvaPrimr` como está
O vencimento vem do prazo e da regra do título: o prazo em dias úteis leva a uma data aproximada `A`, o vencimento nominal é o dia 15 do mês de `A`, e a data do ponto é esse dia 15 ajustado. Os dias úteis do ponto são o `vVertcCurva` publicado (D39). Um calendário com um feriado a mais ou a menos move `A` um ou dois dias, sem mudar o mês: a curva sai certa, com `CALENDARIO_DIVERGENTE`. O que vem do arquivo é respeitado sem questionamento: não há tolerância nem conferência de mês de vencimento. A curva usa só o título inteiro (código SELIC terminado em `99`, hoje `760199`); os desmembrados, como a NTN-B Principal (`760198`), ficam fora, e quem filtra é a ingestão ANBIMA, porque `tAnbmaCurvaPrimr` não tem o código SELIC. **Alternativa rejeitada:** falhar quando o prazo não cai perto do dia 15 ou num mês de cupom (versão anterior), que questionava o dado da fonte. **Alternativa rejeitada:** coluna nova de vencimento (`dVctoTitulo`), que mudaria o schema oficial e a ingestão sem necessidade.

### D12. Bisseção, não Newton
Cada título resolve `f(z) = 0` com uma incógnita, mas os cupons entre o último título resolvido e o vencimento dependem de `z` pela interpolação. A bisseção em `[−0,99; 1,00]` é determinística, não precisa de derivada e sempre termina (≈ 48 iterações até `10^−14`). O "sem troca de sinal" vira um erro claro. **Alternativa rejeitada:** Newton, que é mais rápido mas pode divergir e exige derivada da interpolação. Também foi rejeitado resolver todos os títulos de uma vez por mínimos quadrados (como a ETTJ), que é mais difícil de auditar ponto a ponto.

### D13. Cupom fixo no modelo
Todas as NTN-B pagam 6% a.a. real, semestral. É metodologia do modelo, não varia entre curvas, então fica como constante do `NTNB_BOOTSTRAP_ANBIMA`. Se a regra mudar, o modelo pode ser sobrescrito por Groovy, sem deploy. **Alternativas rejeitadas:** parâmetros de cupom no cadastro e tabela de séries de título, que guardariam um dado igual para todas.

### D14. Cupons antes do primeiro título
No primeiro título, todos os eventos são descontados pela própria incógnita, o que dá `z_1 = y_1`. Nos títulos seguintes, eventos antes do primeiro vencimento usam `z_1` (taxa zero constante). A extrapolação de início `FlatValue` da curva gravada usa a mesma hipótese, então bootstrap e consulta são coerentes nesse trecho.

### SOFR (Bloomberg): `SOFR_ZERO_BLOOMBERG`

**Contexto.** `S0490Z <tenor> BLC2 Curncy` é a série de **zero rates** do SOFR do Bloomberg, segundo [A Smoother Path to SOFR Curve Construction](https://www.lucidogroup.io/smoother-path-to-sofr-curve-construction/). Não há bootstrap, e o caso é análogo à `ZUS` do Manual de Curvas B3 (item 2.11). O `BloombergCurveRaw` do processor tem formato de contrato futuro e não serve.

### D15. Nós na tabela Bloomberg existente
Os nós ficam em `tBbergCurvaPrimr`: `cTickerIndcd` = curva de mercado, `cTickerBberg` = ticker (o tenor é o segundo termo), `vPrecoUlt` = taxa zero, decimal pela regra de precisão (D7). As linhas da SOFR se separam das de contrato futuro pelo `cTickerIndcd`. O modelo aceita o ticker completo, que precisa da coluna maior (change `banco-curvas-ajustes`), e a forma curta `{membro} {tenor}`, que cabe no schema atual. **Por ora não é preciso se preocupar com o tamanho:** a lista de tickers da SOFR está fixa no conector, que até a homologação grava a forma curta (ex.: `S0490Z 15M`); para produção, com o ALTER aplicado, o ticker fica livre e passa a ser gravado completo. Como o modelo lê as duas formas, a troca não exige mudança no engine. **Alternativa rejeitada:** tabela nova `mkt.SofrCurveRaw`, que mudaria o schema sem necessidade.

### D16. Tenor por `java.time.Period`
Qualquer `nD`, `nW`, `nM`, `nY`, sem tabela fixa de tenores (a lista real tem `9M` e `15M`). `D` conta dias úteis, como o `advance` do QuantLib, porque `1D` é o overnight.

### D17. Duplicidade de tenor
A captura real mostrou `1D` duas vezes. Valores idênticos: fica um, o descarte é registrado. Valores diferentes: `INSUMO_INVALIDO`, porque escolher um dos dois seria arbitrário e esconderia um dado inconsistente.

### D18. `construcao/pontosprontos`: código comum, nome de modelo próprio
`PRONTA_TS_B3` e `SOFR_ZERO_BLOOMBERG` só leem valores prontos. A leitura e a conversão de data são de cada um; o comum (ordenar, checar datas repetidas, registrar a memória dos pontos) fica em `pontosprontos`. **Alternativa rejeitada:** um único modelo genérico com leitor injetado, em que a proveniência mostraria sempre o mesmo nome.

### D19. Calendário `UnitedStates`/`FederalReserve`
O SOFR é publicado pelo Fed de Nova York nos dias úteis do Federal Reserve. Os feriados e a regra de fim de semana estão na spec `curve-extension-models`.

### D20. Memória de cálculo e simulação
- **Mesmo código.** `MemoriaCalculo` é preenchida pelos próprios métodos que calculam (pipeline, modelos, interpolação, extrapolação). A simulação chama o mesmo serviço de construção com a gravação desligada. Por isso o que a simulação mostra é o que a construção gravaria, e o teste "simular e construir dão o mesmo `hashPontos`" protege essa garantia.
- **Simulação responde 200 com `status` = `ERRO`.** Ela serve para diagnosticar, e uma falha é justamente o que se quer ver: a memória até o ponto da falha vale mais do que um 422 vazio. Só autenticação, "curva não existe" e parâmetro inválido respondem erro HTTP.
- **Comparação com o gravado.** A simulação mostra, ponto a ponto, o valor gravado e a diferença. Um ponto editado à mão ou um insumo que mudou desde a construção aparecem direto na planilha.
- **`formato=xlsx` nas rotas de leitura, em vez de negociação por `Accept`.** Um link comum na tela baixa o arquivo sem configurar cabeçalho. A mesma planilha serve a consulta normal (fonte `GRAVADA`) e a simulação (fonte `SIMULACAO`), com as mesmas abas e colunas.
- **Apache POI (`poi-ooxml`, `XSSFWorkbook`).** Biblioteca padrão para `.xlsx` em Java. O volume é pequeno: até 5.000 prazos e algumas centenas de pontos e fluxos.
- Números como células numéricas, para que a planilha possa ser recalculada, com a limitação de 15 dígitos significativos do Excel registrada no `Resumo`. A precisão completa está no JSON.

### D21. Observabilidade e roteiro de investigação
Logs JSON (o engine já tem `logstash-logback-encoder`) com `correlationId`, código, nome e data-base nos eventos da spec `curve-build-pipeline`. O histórico de quem gravou o quê está no log (`CURVA_GRAVADA` no engine, `PONTOS_EDITADOS` no `services/curves`), e o estado de agora, no arquivo de auditoria montado na hora (D23).

Roteiro para "a curva X da data D está errada":
1. Baixar `GET /curvas/X/D/auditoria?formato=xlsx`: pontos gravados, cadastro vigente, e a aba `Conferencia` com o que a fonte produz agora, ponto a ponto. Pontos `DIFERENTE` indicam edição manual, republicação da fonte ou cadastro alterado depois da construção.
2. Buscar no log os eventos `CURVA_GRAVADA` e `PONTOS_EDITADOS` da curva e data: quem construiu, recalculou ou editou, quando, de qual carga, com quais modelos, e os pontos substituídos.
3. Se a conferência bater e o valor ainda parecer errado, o erro está no insumo (aba `Insumos`) ou no cadastro (aba `Resumo`).
4. Se o modelo falhar, a simulação (`GET /curvas/X/D/simulacao?formato=xlsx`) dá o erro em `Resumo` e `Eventos`, e a linha em `Insumos`.
5. Para um prazo interpolado suspeito: `GET /curvas/X/D/interpolacao?du=N&formato=xlsx`, que mostra os vizinhos, o `W` e o `Y` de cada prazo.

### D22. Carga concluída por webhook do processor
O processor é quem sabe que terminou de gravar o bruto, então é ele que avisa (`POST /api/v1/cargas`, spec `curve-load-trigger`). Decisões:
- **O aviso é o gatilho; a transação do processor é a garantia.** O processor grava cada carga numa única transação e só avisa depois do commit, então dado bruto presente é carga completa. Na construção pelo webhook, o engine ainda confere a quantidade lida contra a avisada no corpo (`INSUMO_INCOMPLETO`). A construção pela API lê o que está gravado.
- **Construção síncrona na própria requisição.** São poucas curvas por carga (5 da B3, 1 da ANBIMA, 1 do SOFR), construídas em segundos. A durabilidade vem do retry do processor com o mesmo `idCarga` e da idempotência do engine (curva com pontos → `EXISTENTE`), sem fila nem varredura agendada.
- **Nenhum registro da carga.** O engine não guarda a carga em lugar nenhum: o Blob fica só com os originais dos feeders e os scripts Groovy, e o banco não pode mudar. O que se precisaria saber depois é calculado na hora a partir do banco: há dado bruto da origem na data, há pontos gravados, e os pontos gravados batem com o que a fonte atual produz.
- **A carga nunca recalcula.** Ela só constrói curvas que ainda não têm pontos na data. Para as que já têm, o engine roda o modelo sem gravar e compara: se a fonte atual produz pontos diferentes (republicação, edição manual ou cadastro alterado), devolve o aviso `PONTOS_DIFERENTES_DA_FONTE`. Recalcular é decisão de um operador, por `forcarRecalculo=true`, porque a curva gravada pode já ter sido consumida.

**Alternativas rejeitadas:**
- Registro da carga no Blob ou em tabela: o Blob é só para originais e scripts, e o banco não pode mudar. A comparação com a fonte atual dá a mesma informação útil (a curva está coerente com a fonte?) sem estado guardado.
- Engine consultar periodicamente se a carga terminou: exige que o engine saiba o que é "completo" para cada fonte, e isso é conhecimento do processor.
- Fila (Kafka) em vez de webhook: o engine precisaria de um consumidor e de controle de offset para um evento por fonte e dia. O webhook com retry dá a mesma garantia com menos peças.
- Construção assíncrona com resposta 202: exigiria fila interna e varredura de pendências para não perder o gatilho se a instância cair.

### D23. Auditoria sem mudar o schema e sem Blob: log, colunas de cálculo e arquivo montado na hora
O banco não pode ser alterado, e o Blob é só para originais e scripts. A trilha fica em três lugares:
- **Log:** cada construção e recálculo emite `CURVA_GRAVADA`, depois do commit, com usuário, carga, `hashPontos` antes e depois, proveniência e os pontos substituídos (spec `curve-audit-history`). O destino dos logs precisa de retenção definida pela área de risco.
- **Resumo em `tCurvaMercd`:** a construção atualiza `dBaseReft` (maior data-base construída) e `cUsuarCalc` (quem calculou), colunas do schema oficial que não eram usadas por ninguém (o `services/curves` só as lê, no painel, e nunca as escreve). É a mesma linha que a construção já trava (D6), então não há custo extra de concorrência.
- **Arquivo de auditoria sob demanda:** o front pede, e o engine monta na hora, sem guardar: pontos gravados, cadastro vigente, modelos, e a conferência ponto a ponto com o que a fonte produz agora.

**Alvo ideal, quando o banco puder mudar:** tabelas `tAuditCurva` e `tHistDadoCurva` na mesma transação dos pontos, com o histórico consultável pela API. **Alternativa rejeitada:** registros de auditoria no Blob (fora do que o Blob guarda) e histórico consultável sem tabela.

### D24. Leitura consistente sem opção nova no banco
A reconstrução apaga e insere na mesma transação. No `READ COMMITTED` padrão do SQL Server, uma leitura concorrente espera o commit em vez de ver a data vazia, então a consistência já está garantida, desde que ninguém use `NOLOCK`. A espera é limitada ao tempo de uma construção (segundos). `READ_COMMITTED_SNAPSHOT` eliminaria a espera e fica como melhoria quando o banco puder mudar.

### D25. Autenticação e papéis pelo Entra ID
Todas as rotas exigem JWT do Entra ID. O acesso é por papéis de aplicação: `Curvas.Leitura`, `Curvas.Operador`, `Curvas.Processor` (só a identidade de serviço do processor, por client credentials), `Curvas.Orquestrador` (só a identidade de serviço do orquestrador, por client credentials; ela também recebe `Curvas.Leitura` para o calendário), e a identidade do `services/curves` recebe `Curvas.Leitura` e `Curvas.Operador` (para regravar a curva interpolada depois de uma edição manual), `Curvas.ModelosAutor` e `Curvas.ModelosAprovador`. Quem ativa um script pode ser o próprio autor, porque muitas vezes há um só operador no horário; o `estado.json` e o log registram quem ativou. A leitura também exige papel, porque a curva é dado de mercado usado em risco e precificação. O Blob é acessado por Managed Identity, sem chave em configuração. **Alternativa rejeitada:** chave de API por cliente, que não identifica o usuário para a auditoria e exige rotação manual.

### D26. Resiliência
Os tempos limite, a política de repetição, a saúde, os logs de dependência e as métricas estão na spec `curve-engine-resilience`. Decisões:
- **Só repete o que é idempotente:** leituras e gravação condicional de versão imutável. Gravação no banco e no `estado.json` devolvem o erro, para não duplicar efeito.
- **Estado de script desatualizado mantido sem prazo, só com log.** Sem isso, uma queda do Blob derrubaria todas as consultas no fechamento, porque toda resolução de modelo lê o estado. Com o Blob fora, ninguém consegue ativar ou desativar script (essas operações também escrevem no Blob), então o último estado lido continua correto. A única exceção é uma queda parcial, em que uma instância enxerga o Blob e outra não; ela aparece no aviso de log e na métrica de idade do estado, que serve para alerta.
- **Blob fora nunca bloqueia construção nem consulta.** No fechamento, construir com o que existe vale mais do que esperar o Blob. O engine só lê scripts Groovy do Blob, e a degradação é definida: estado de script desatualizado ou, na falta dele, modelos nativos. Toda degradação aparece na resposta, no log e na proveniência (`estadoScript`, avisos), e não em silêncio.
- **Circuit breaker no Blob.** Depois de 5 falhas seguidas, o engine para de chamar o Blob por 60 segundos, para que uma queda não some um tempo limite a cada operação no meio do fechamento.
- **Prontidão depende do banco e, na subida, espera o Blob por até 5 minutos.** Sem banco não há o que fazer. Uma instância nova espera o Blob para carregar os scripts ativos; se ele não voltar em 5 minutos, ela fica pronta com os modelos nativos, com erro no log, para não deixar o fechamento sem instância.

### D27. Calendário por planilha, gerado como Groovy
Feriado decretado de última hora não pode esperar deploy. A planilha de feriados vira um script Groovy de calendário, e não uma tabela ou um arquivo lido direto, por dois motivos: reaproveita todo o ciclo de versão, validação, ativação, propagação entre instâncias e proveniência dos scripts; e o script gerado é uma subclasse mínima de `CalendarioPorLista`, sem lógica, só com a lista de datas. A geração é determinística (mesma planilha, mesmo hash). A exportação usa o mesmo formato da importação, de modo que o fluxo de manutenção é exportar, editar e importar. A cobertura é explícita: fora dela o calendário falha, em vez de supor dia útil. **Alternativa rejeitada:** tabela de feriados no banco (o banco não pode mudar nesta fase, e ficaria fora do versionamento dos modelos).

### D28. Pacote de depuração com o código que rodou
A proveniência diz quais modelos e versões rodaram, mas investigar exige o código. O zip leva a planilha, o JSON e o código-fonte de cada script Groovy tirado da memória da instância (o texto que foi compilado, não uma releitura do Blob), mais um manifesto com os hashes. Modelos nativos são identificados pela versão do artefato do engine, gravada no build (sem o commit: cada versão publicada corresponde a um código só). Com isso, um caso de produção pode ser reproduzido fora do ambiente, com o mesmo código.

### D29. Datas e horários de Brasília, testes em massa
- **Horário de Brasília em tudo, pela JVM inteira:** os servidores do Azure rodam em UTC, e às 21h de Brasília já é o dia seguinte em UTC. O `main` do engine fixa o fuso padrão da JVM em `America/Sao_Paulo` antes de subir o Spring (`TimeZone.setDefault`), e a subida falha se o fuso não for esse. Assim, `LocalDate.now()` e `OffsetDateTime.now()` já saem no horário de Brasília, sem classe de relógio própria. **Alternativa rejeitada:** um relógio próprio injetado em todo lugar, que duplicava o `java.time` e espalhava uma dependência por todo o código.
- **Regressão com muitos pregões:** o oráculo contra a B3 roda sobre pelo menos 12 meses de `TaxaSwap.txt`, cobrindo os feriados móveis e a virada de ano, onde erros de calendário aparecem. Somam-se testes de propriedade com JUnit parametrizado e semente fixa (ponto preservado, determinismo, ida e volta taxa↔fator em valores aleatórios), sem biblioteca extra no pom e um teste do `DecimalMath` contra valores de referência, com tolerância de `double`.
- **Sem contrato de API versionado nesta fase.**

### D30. Situação e vigência da curva só valem para a construção automática
Inativar a curva, ou deixar a data-base fora da vigência dela no cadastro, é uma decisão de não construí-la no dia a dia. A construção automática (carga e construção da data pelo orquestrador) respeita isso e devolve a curva como `IGNORADA`, sem erro. A construção pedida por um usuário, porém, é uma ação consciente (reprocessar uma data antiga de uma curva já desativada, testar uma curva antes de ativá-la) e é executada, com o aviso `CURVA_INATIVA` ou `FORA_DA_VIGENCIA_CURVA`. Consulta e interpolação nunca dependem disso. **Alternativa rejeitada:** `CADASTRO_INVALIDO` em toda construção, que impediria o reprocessamento histórico.

### D31. Interpolação tolerante a pontos gravados à mão
A edição manual no `services/curves` só recusa inconsistência de banco; regra de negócio vira aviso (change `curves-cadastro-curvas`). Por isso o engine precisa conviver com pontos que nenhum modelo geraria: em fim de semana ou feriado, na data-base ou antes dela. A base comum de interpolação, por onde passam todos os interpoladores, inclusive `Cubic` e Groovy, descarta com aviso os pontos de prazo não positivo e, no mesmo prazo do eixo, fica com o de menor data. Os pontos gravados nunca são alterados, e o aviso aparece em toda saída. O que não tem tratamento seguro (preço ou pontos não positivos no `LogLinear`) falha com `PONTOS_NAO_INTERPOLAVEIS`, citando os pontos. **Alternativa rejeitada:** recusar a consulta com qualquer ponto fora da regra, que deixaria a curva inutilizável até alguém corrigir, justamente quando a edição manual foi o recurso de contingência.

### D32. Valores aceitos gerados do validador
O front e a planilha do cadastro precisam das listas de valores aceitos, e os modelos crescem com Groovy sem deploy. `GET /api/v1/valores-cadastro` é gerado dos mesmos enums e da mesma tabela que o validador usa, com os modelos Groovy ativos, e um teste garante a ida e volta. O `services/curves` repassa essa rota ao front. **Alternativa rejeitada:** só `enum` no Swagger, que não mostra os modelos Groovy nem as regras de combinação.

### D33. Situação das curvas para o painel do gestor, calculada na hora
O painel de acompanhamento fica no `services/curves` (change `curves-cadastro-curvas`), que já tem o cadastro e os pontos. O que só o engine sabe calcular (há insumo na data, o modelo roda, os pontos gravados batem com a fonte atual) sai por uma rota só de leitura, `GET /api/v1/curvas/situacao`, que confere todas as curvas na hora, em paralelo, sem nenhum estado guardado. Com cerca de 120 curvas de algumas centenas de pontos, a conferência cabe em segundos. **Alternativas rejeitadas:** guardar a última tentativa de cada curva (exigiria Blob ou tabela); o curves rodar os modelos (duplicaria o engine).

### D34. Curva derivada de outras curvas, sem modelo nesta fase
Curvas como a inflação implícita (PRE sobre a NTN-B bootstrapada) não vêm de fonte: vêm de outras curvas já construídas. A estrutura fica pronta agora, sem nenhum modelo derivado:
- **Cadastro sem schema novo:** as curvas componentes são ligações em `tCurvaPrvdr` com o provedor interno `TCEN`, o nome da curva componente em `cTickerPrvdr` e o papel (ex.: `NUMERADOR`, `DENOMINADOR`) em `cPrvdrMercd`.
- **O modelo lê as curvas componentes pelo contexto**, já montadas para interpolar com o cadastro de cada uma, e nunca pelas tabelas brutas.
- **Construção em cadeia na carga:** depois das curvas da carga, o engine constrói as derivadas cujas curvas componentes já estão prontas, em ordem de dependência. As curvas componentes de fontes diferentes se resolvem sozinhas: a derivada sai na carga que completa as curvas componentes.
- **Nada é recalculado em cascata.** A curva componente recalculada ou editada deixa a derivada diferente do que as curvas componentes atuais produzem, o que aparece na conferência do painel, e o recálculo é do usuário, como em toda curva.

**Alternativas rejeitadas:** tabela de dependências entre curvas (muda o schema); o modelo derivado ler `tDadoVertcCurva` direto (duplicaria a montagem da curva e escaparia da proveniência); recálculo em cascata (uma edição manual na PRE mudaria em silêncio todas as filhas já consumidas).

### D35. Fontes ANBIMA: mercado secundário hoje, curva zero no futuro
A NTN-B desta fase vem do bootstrap dos títulos do arquivo de Mercado Secundário (`ms{AAMMDD}.txt`, produto `MS`). No futuro, a ingestão ANBIMA também SHALL baixar a **curva zero** (Estrutura a Termo das Taxas de Juros Estimada), que a ANBIMA publica pronta:
- **endereço:** `POST https://www.anbima.com.br/informacoes/est-termo/CZ-down.asp`, com `application/x-www-form-urlencoded`: `escolha=2`, `Idioma=PT`, `saida=txt` (ou `csv`, `xls`, `xml`), `Dt_Ref=dd/mm/aaaa` e `Dt_Ref_Ver=AAAAMMDD` (limite inferior que a página envia). A resposta é o anexo `CurvaZero_{DDMMAAAA}.txt`. Só há **5 dias úteis de histórico**: o download precisa ser diário;
- **formato:** Latin-1, campos separados por `@`, vírgula decimal, ponto de milhar nos vértices (`1.008`) e betas em notação científica (`4,329E-03`). O primeiro campo de cada linha é o bloco: `0` cabeçalho e data; `1` parâmetros Svensson de PREFIXADOS e IPCA; `2` ETTJ por vértice em dias úteis (252 a 8.316, de 126 em 126) com ETTJ IPCA (taxa zero real), ETTJ PREF e Inflação Implícita (estas duas até 2.520); `3` PREFIXADOS da Circular 3.361 (21 a 2.520); `4` erro título a título. Célula vazia é ausência de vértice, não zero;
- **gravação:** cabe em `tAnbmaCurvaPrimr` sem mudar colunas, uma curva de mercado por série (verificado, change `banco-curvas-ajustes`, D5), com produto `CZ` e o nome da série como código na fonte;
- **uso:** a ETTJ IPCA é a curva zero real oficial, alternativa ao bootstrap da NTN-B; a Inflação Implícita vem pronta, sem precisar de curva derivada; a ETTJ PREF e a Circular 3.361 servem de conferência da PRE. Construí-las exige só um modelo de leitura de vértices prontos, como o `PRONTA_TS_B3`, numa change futura.

### D36. Curva construída em `tDadoVertcCurva`
`tDadoVertcCurva` ("informações detalhadas de cada vértice da curva") é a curva construída: cada ponto com data, valor, dias úteis, dias do período, dias em 30/360 e os fatores. O usuário confere na tela o que foi calculado e entregue, sem puxar o log, e o registro não muda se o calendário mudar depois.
- **Uma fonte para os pontos:** data, valor, dias úteis e `hashPontos` vêm de `tDadoVertcCurva`; a curva interpolada em `tDadoCurva` é derivada deles (D6).
- **Coerência com a edição manual:** o `services/curves` regrava só os pontos que mudaram, com os dias úteis informados pelo usuário e sem fatores, e apaga os excluídos; os pontos que não mudaram mantêm os fatores do engine. A tela mostra `SEM_CALCULO_GRAVADO` para os pontos sem fatores até um recálculo.
- **Conferência:** a consulta mostra o gravado ao lado do recalculado; `CALENDARIO_DIVERGENTE` quando os dias úteis do ponto diferem do calendário, `CALCULO_GRAVADO_DIVERGENTE` quando os fatores diferem com os mesmos dias úteis.
- **Colunas:** os nomes são os do schema aplicado (`dVertcReft`); o significado vem das descrições da ideia original: `cDiaUtil` dias úteis, `cQtdDiaPer` dias do período, `cQtdDiaReft` dias em 30/360, `vFatorDia` fator diário de capitalização, `vFatorAcum` fator acumulado até o vértice.
- **Período = da data-base ao vértice.** O sistema real não preenche `tDadoVertcCurva`; esta spec define o conteúdo. As três contagens usam o mesmo intervalo `(B, d]`: `cDiaUtil` em dias úteis, `cQtdDiaPer` em dias corridos e `cQtdDiaReft` em 30/360, e `vFatorDia` é o fator médio por dia útil desde a data-base (`FA^(1/DU)`). Assim, `cDiaUtil` e `cQtdDiaPer` se conferem direto contra `cDiaUtil` e `cDiaCorri` do `TaxaSwap.txt`. **Alternativa rejeitada:** período entre um vértice e o anterior, que mudaria também o sentido de `vFatorDia` (fator a termo do trecho) e não se confere contra a fonte.

**Alternativa rejeitada:** pontos em `tDadoCurva` e só o detalhe em `tDadoVertcCurva` (versão anterior deste design): `tDadoCurva` é a curva interpolada, e dividir o ponto entre as duas tabelas obrigava a manter as duas em sincronia na edição manual.

### D37. Dois gatilhos automáticos: processor e orquestrador
O processor sabe quando o dado de mercado puro terminou de ser gravado, então ele dispara as curvas da carga (D22). Mas nem toda construção automática nasce de uma carga: a derivada cuja curva componente foi construída ou recalculada à mão pela API não entra em nenhuma cadeia, e um aviso de carga perdido deixa a curva sem pontos. Por isso o orquestrador também dispara, por `POST /api/v1/construcoes/{dataBase}`, com o papel próprio `Curvas.Orquestrador`:
- **Todas as curvas da data, com as regras automáticas:** constrói o que tem insumo e não tem pontos, nunca recalcula, ignora curva inativa ou fora da vigência, e devolve `SEM_INSUMO` para o que ainda não tem dado. Primeiro as curvas de provedor, em paralelo; depois as derivadas, em cadeia.
- **Não usa a rota de construção por curva**, que é a do usuário e constrói até curva inativa.
- **Papel próprio**, para a auditoria distinguir `acionadoPor` = `ORQUESTRADOR` de `CARGA` e `API`.
- **Sem filtro por curva nesta fase.** Uma curva só, com as regras do usuário, continua pela rota de construção por curva.
- **Concorrência com o webhook:** a trava por curva (D6) serializa as duas, e a existência de pontos é conferida depois da trava.

**Alternativa rejeitada:** agendador dentro do engine. Com duas ou mais instâncias, todas disparariam juntas, e evitar isso exigiria trava distribuída; agendar é papel do orquestrador.

### D38. Construção por origem secundária, escolhida pelo usuário
Uma curva pode ter fontes de reserva em `tCurvaPrvdr` (prioridades 2, 3...). Os feeders gravam o bruto para toda curva ligada ao código, principal ou não, então o dado da reserva já está no banco quando a principal falha. A construção automática continua só pela principal; a troca é decisão do usuário, por `fonte` e `produto` na rota de construção existente (e na simulação, para ver antes):
- **Mesma rota, não uma nova.** Construir pela secundária é construir a mesma curva, com as mesmas regras de trava, recálculo e situação; muda só de onde vêm os pontos.
- **Modelo por origem no `cModDado`.** Cada modelo de construção aceita uma fonte e um produto, e o cadastro tem um `cMotorCalc` só. `MODELOS_POR_ORIGEM` diz qual modelo lê cada origem secundária, sem mudar o schema; sem a chave, vale o `cMotorCalc`, se ele aceitar a origem.
- **O resto do cadastro é o da curva.** Interpolador, cotação, calendário e arredondamento não mudam com a fonte, para os consumidores não verem outra curva.
- **Rastro só no log.** O banco não guarda a origem dos pontos. `CURVA_GRAVADA` e o aviso `ORIGEM_SECUNDARIA` dizem qual origem foi usada; a conferência automática compara com a principal e mostra a data como diferente, o que é o sinal para recalcular pela principal quando ela chegar.

**Alternativas rejeitadas:** trocar a prioridade em `tCurvaPrvdr` para construir (mudaria o cadastro de todas as datas e a construção automática); rota nova só para a secundária (duplicaria as regras da construção).

### D40. Regras por arquivo
Cada arquivo de origem tem a sua forma de estar errado: na B3, uma linha contradiz outra; na ANBIMA, o prazo não leva a um vencimento de NTN-B; na Bloomberg, um tenor repete com outro valor. Por isso não há regra de insumo genérica no pipeline: cada modelo traz a tabela "Regras do arquivo" na sua spec, com o resultado de cada situação (constrói, constrói com aviso, descarta com aviso ou falha). O critério comum é só este: tolerar quando o dado tem uma leitura única, e falhar quando qualquer escolha seria um chute. Fora dessas regras, que só impedem gravar um dado que se contradiz, não há checagem de qualidade nesta fase: variação anormal, comparação entre fontes e aprovação são do módulo de data quality, que será implantado no futuro. A validação do leiaute do arquivo continua na ingestão de cada fonte (processor da B3; a da ANBIMA e a da Bloomberg nas changes delas).

### D39. Dias úteis da fonte e do usuário obedecidos
Quando a fonte publica os dias úteis de um ponto (B3: `cDiaUtil`; ANBIMA: `vVertcCurva`) ou o usuário os informa na edição manual, eles são obedecidos sem discussão. A data do ponto é exata (data-base + dias corridos), então o que pode divergir é o nosso calendário, e ele passa a importar só onde ninguém informou nada: as datas entre os pontos e a conversão de `du` em data.
- **Construção nunca falha por calendário:** grava os dias publicados em `tDadoVertcCurva.cDiaUtil`, calcula os fatores com eles e avisa `CALENDARIO_DIVERGENTE` para alguém corrigir o feriado.
- **Ancoragem:** o prazo de um ponto é o `cDiaUtil` gravado; o de uma data entre dois pontos é o do ponto anterior mais os dias úteis que o calendário conta no trecho, sem passar do seguinte. Com o calendário certo, o resultado é idêntico ao de antes.
- **Cada modelo declara o que a fonte publica** (`diasUteisPublicados`): a B3 e a ANBIMA publicam; a SOFR da Bloomberg publica só o tenor, e os dias úteis dela vêm do calendário `UnitedStates` na construção. Uma fonte nova só muda a spec do seu modelo.
- **Usuário:** a edição manual e a planilha de pontos do `services/curves` aceitam dias úteis opcionais por ponto, gravados em `tDadoVertcCurva` e obedecidos da mesma forma; sem eles, o calendário.

**Alternativa rejeitada:** falhar a construção com `INSUMO_INVALIDO` quando o calendário diverge da fonte (decisão anterior): deixava o fechamento sem curva por um feriado, quando a fonte já diz os dias certos.

## Risks / Trade-offs

- **Parâmetros em JSON numa coluna legada (`cModDado`).** Outro sistema pode usar essa coluna com outro sentido. → Confirmar com o dono do schema antes do apply; o engine rejeita qualquer conteúdo que não seja o JSON esperado, então um uso diferente aparece como `CADASTRO_INVALIDO`, nunca como curva errada.
- **Engine passa a escrever em `tCurvaMercd`** (`dBaseReft`, `cUsuarCalc`), tabela do cadastro. → Só essas duas colunas, na linha já travada; confirmar com o dono do cadastro que elas são de cálculo.
- **Curva interpolada é o maior volume** (cerca de 12.400 linhas por curva e data na PRE). → Gravada na mesma transação dos pontos, com `INSERT` em lote; a PK de `tDadoCurva` clustered fica como pergunta na change `banco-curvas-ajustes`.
- **Interpolada desatualizada depois de uma edição manual com o engine fora.** → A edição fica gravada, a consulta, a auditoria e o painel mostram `INTERPOLADA_DESATUALIZADA`, e a regravação pelo `services/curves` ou por um operador corrige.
- **Entidade de `tDadoCurva` do engine diverge do schema** (`dtVerticeReferencia`, `cDiaUtil`, `vDiaFator`...). → A entidade passa a ter só as quatro colunas do schema, e os pontos com dias e fatores ficam na entidade de `tDadoVertcCurva`.
- **Tabelas brutas ainda fora do contrato de D10.** → Testes com fixtures; os changes do conector e do processor precisam entrar antes do deploy.
- **`tBtrsCurvaPrimr.cTickerIndcd` tem FK para `tCurvaMercd`.** → O processor grava os vértices sob o nome da curva de mercado, mapeada pelo `tCurvaPrvdr`; não há linhas de "curva da fonte" em `tCurvaMercd`.
- **Calendário desatualizado com dias úteis publicados.** → A curva segue a fonte (D39), e só as datas entre vértices usam o calendário, ancoradas no vértice anterior; o aviso `CALENDARIO_DIVERGENTE` aponta o feriado a corrigir, inclusive por planilha, sem deploy.
- **Groovy pode sobrescrever um nativo usado por todas as curvas.** → Validação obrigatória, fixação por curva para testar antes, e proveniência em toda resposta.
- **Janela de até 30 segundos após uma ativação em que instâncias diferentes usam versões diferentes.** → Cada resposta e cada log informam a versão usada; quem precisa de troca imediata numa curva fixa a versão no cadastro.
- **Dependência do Blob para resolver modelos.** → Cache de estado de 30 segundos, classes compiladas em memória e último estado mantido sem prazo com o Blob fora (D26).
- **`FlatForward` no início e `FlatValue` no fim não existem no QuantLib.** → Documentados como extensão; o padrão é `Disabled`.
- **Unidade do prazo ANBIMA e escala do SOFR não confirmadas.** A escala da NTN-B está confirmada pelo arquivo `ms{AAMMDD}.txt` (`Tx. Indicativas` em percentual ao ano); o prazo `vVertcCurva` depende da ingestão ANBIMA, ainda não transcrita, que o calcula a partir de `Data Vencimento`. → A checagem do dia 15 (D11) pega a unidade da NTN-B. A escala aparece na primeira simulação com dado real (uma taxa de 0,06 em vez de 6 salta aos olhos na planilha).
- **Natureza zero rate e convenção (`Actual360`/`Simple`) do SOFR vêm de fonte de terceiro.** → Confirmar no Terminal antes do apply. A convenção é cadastro. Se for par rate, o modelo ganha um passo de bootstrap sem mudar D15 e D16.
- **Data construída pela origem secundária aparece como diferente da fonte** na situação e na auditoria, que comparam com a principal. → É o sinal esperado para recalcular pela principal quando ela chegar; a origem usada está no `CURVA_GRAVADA`.
- **Republicação da fonte deixa a curva gravada diferente da fonte até alguém recalcular.** → O webhook devolve o aviso `PONTOS_DIFERENTES_DA_FONTE`, o log registra o evento com nível `AVISO` (base para alerta), e o painel mostra a curva como divergente da fonte.
- **Sem histórico consultável pela API.** Quem gravou antes, e os pontos substituídos, estão só no log. → Retenção do log definida pela área de risco; o arquivo de auditoria sob demanda mostra o estado atual e a conferência com a fonte; tabelas de auditoria entram quando o banco puder mudar (D23).
- **No mínimo duas instâncias.** Toda coordenação entre elas é pelo banco (trava por curva) e pelo Blob (`estado.json` com ETag), nunca por memória local, e nada depende de a mesma instância atender duas chamadas seguidas. Cada requisição roda inteira numa instância: as instâncias dividem as requisições, não o trabalho de uma requisição (a rota de situação e a construção da data usam o paralelismo da própria instância).
- **Conferência na hora custa leitura e CPU.** O webhook roda o modelo das curvas que já têm pontos, e a rota de situação roda o de todas. → Só leitura, sem trava, em paralelo limitado e com tempo limite por requisição; o volume é de centenas de curvas pequenas.
- **Processor sem retry perde o gatilho.** → A construção da data pelo orquestrador constrói a curva na chamada seguinte; o painel mostra a curva com insumo e sem pontos até lá, e a construção manual continua possível.
- **Evento `CURVA_GRAVADA` emitido depois do commit.** Se a instância cair entre o commit e o log, a gravação fica sem evento. → Janela de milissegundos; `dBaseReft`, `cUsuarCalc` e os pontos gravados continuam no banco, e o arquivo de auditoria mostra o estado atual.
- **Instância nova com o Blob fora usa modelos nativos.** Se houver script Groovy ativo, a curva sai com a matemática nativa. → `estadoScript` = `DESCONHECIDO` na proveniência e erro no log; a simulação depois da volta do Blob mostra a diferença, e o recálculo corrige.
- **Leitura espera o commit de uma reconstrução em andamento.** → Espera de segundos, limitada pelo tempo limite de comando; RCSI resolve quando o banco puder mudar.
- **Simulação em produção gera carga de leitura.** → É só leitura e não trava nada; o limite de 5.000 prazos por chamada contém o custo.

## Migration Plan

1. Sem migração de banco. No build: versão do artefato gravada no pacote do engine. No Blob: só a pasta `groovy-models/` (scripts e calendários importados), com acesso do engine por Managed Identity; nenhum dado de curva vai para o Blob. No log: retenção dos eventos `CURVA_GRAVADA` definida pela área de risco. No Entra ID: registro da aplicação com os cinco papéis e atribuição à identidade do processor. No cadastro: parâmetros de cada curva em `tConfgCurva.cModDado`.
2. Pré-requisitos, fora deste change: tabelas brutas no contrato de D10 (conector e processor), chamada do webhook de carga pelo processor (change `conector-b3-webhook-ingest`) e cadastro das 7 curvas pelo `services/curves` (change `curves-cadastro-curvas`, arquivo `exemplo-cadastro-7-curvas.txt`), com os valores das specs.
3. Deploy do engine com as rotas novas, sem convivência com as antigas. Os clientes internos (curve-bff) migram no mesmo release, em change próprio.
4. **Rollback:** voltar o deploy do engine. Não há migração de banco para reverter.

## Open Questions

- Política de expurgo da curva interpolada em `tDadoCurva` (o maior volume), se houver.
- `cLingSist`, `cPreCalc`/`cPosCalc` e `cPreMotorCalc`/`cPosMotorCalc` de `tConfgCurva` sugerem ganchos pré e pós cálculo. Não são usados; podem virar scripts Groovy de gancho numa mudança futura.
- Unidade de `tAnbmaCurvaPrimr.vVertcCurva`, a confirmar com a ingestão ANBIMA (não transcrita): o arquivo traz `Data Vencimento`, e a ingestão grava o prazo. A escala de `vPrecoTx` está confirmada: percentual ao ano, como `Tx. Indicativas` do arquivo.
- Prazo de retenção dos logs com `CURVA_GRAVADA` (exigência regulatória ou interna).
- Quando o banco puder mudar: `tParmConfgCurva` em chave/valor, tabelas de auditoria e `READ_COMMITTED_SNAPSHOT`.
- Confirmar no Bloomberg Terminal que `S0490Z ... BLC2 Curncy` é zero rate, qual a convenção de cotação e a causa do `1D` duplicado.
- **Calendário ANBIMA:** importar os feriados nacionais do arquivo `.xls` publicado pela ANBIMA para o calendário `Brazil`/`Settlement` (formato, periodicidade da atualização e quem importa). Vindo do `curves-cadastro-curvas`, a revisar na sequência.
- **SOFR em dia útil só dos EUA:** data-base que é dia útil do `UnitedStates`/`FederalReserve` e feriado no Brasil (e o inverso): se a SOFR é construída, com qual data-base, e como o painel do curves a mostra. Vindo do `curves-cadastro-curvas`, a revisar na sequência.
