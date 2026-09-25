## Context

A motivação está no proposal (Why) e o comportamento nas specs. Este documento cobre só o estado atual e as decisões de implementação.

**Engine na `develop` (`services/engine`, ~1.900 linhas de domínio):**
- `ConstruirCurvaService` resolve o builder pelo `cMotorCalc`, lê os insumos de `tDadoCurva` e grava `tDadoVertcCurva`, com um cabeçalho de versão em `tMtrizCurva` (tabela que, no modelo oficial, é de superfícies).
- O builder `ComposableCurveBuilder` não interpola. O `CurveBuilderRegistry` cria um builder por interpolador, mas descarta o interpolador.
- A conversão taxa↔fator está em 4 classes, toda em `double`.
- A interpolação sob demanda (`CalcularCurvaService`) usa os enums `MetodoInterpolacao` e `PoliticaExtrapolacao`, vindos do request.
- O Groovy (`GroovyDynamicModelCompiler` + `ModeloUploadController`) só aceita `CurveBuilderStrategy`, fica só em memória e usa sandbox por lista de bloqueio.

**Schema oficial de curvas de mercado:** já existe coluna para quase todo o cadastro.

| Tabela | Uso nesta mudança |
|---|---|
| `tCurvaMercd` | curva de mercado: `cTickerIndcd` (nome de exibição e chave de todas as FKs), `cTickerIdtfdUnic` (código usado nas rotas por código), `cTpoVlr` (unidade), `cNormaDia` (DayCounter da cotação), `cTpoJuro` (Compounding da cotação) |
| `tCurvaPrvdr` | origem: `iPrvdrDados` (fonte), `cTickerPrvdr` (código na fonte), `cPriorCsumo` (prioridade) |
| `tConfgCurva` | `cMotorCalc` (construção), `cRotnaCalc` (interpolador, hoje sem uso), vigência `dInicVgcia`/`dValidAte`/`cVrsaoReg` |
| `tParmConfgCurva` | demais parâmetros, em chave/valor (`cConfgIdtfd` → `cTpoInstt` ou `vPrecoTx`) |
| `tBtrsCurvaPrimr` | vértices brutos do TaxaSwap B3 (`cDiaCorri`, `cDiaUtil`, `vPrecoTx`) |
| `tDadoCurva` | única saída do engine nesta fase (ver o modelo de dados abaixo) |

**Modelo de dados: curva de mercado, dado curva e curva data.** As chaves estrangeiras do schema definem esta hierarquia:

```
tCurvaMercd        curva de mercado — PK cTickerIndcd
  ├──< tDadoCurva        dado curva — PK (dBaseReft, cTickerIndcd, dVertcReft), FK → tCurvaMercd
  │      └──< tCurvaData curva data — mesma PK, FK → tDadoCurva (0..1 por dia do dado curva)
  ├──< tDadoVertcCurva   vértices calculados — mesma forma de PK, FK → tCurvaMercd
  └──< tMtrizCurva       matriz por curva × data-base — uso reservado a superfícies
```

- **Curva de mercado (`tCurvaMercd`):** a curva como produto, identificada pelo código usado na API.
- **Dado curva (`tDadoCurva`):** os **pontos observados ou calculados** da curva na data-base, montados pelo **modelo de construção** a partir da origem cadastrada. Ex.: os 278 vértices da PRE no TaxaSwap.
- **Curva data (`tCurvaData`):** a **curva interpolada diária**. **Não é gravada nesta fase**: a interpolação é calculada sob demanda na consulta (D9).
- **Vértices calculados (`tDadoVertcCurva`):** não é gravada nesta fase. Os fatores das curvas de taxa são calculados na consulta.
- **`tMtrizCurva`:** reservada a superfícies; não é usada por curvas. O engine atual grava nela um cabeçalho de versão, e esta mudança remove esse uso.

Fluxo desta fase:
```
tBtrsCurvaPrimr ─ modelo de construção ─► tDadoCurva (pontos)
 (bruto)
                     consulta pela API ─► interpolação em tempo de execução a partir de tDadoCurva
```

O schema é mantido como está. Como nada é gravado em `tCurvaData`, a FK real `FK_tDadoCurva_tCurvaData` não interfere nesta fase. Gravar a curva diária em `tCurvaData` fica para o alvo ideal descrito em D6.

**Conector:** o catálogo casa pela descrição via regex. Verificado com as linhas reais do `TaxaSwap.txt`: `DCL` e `DPL` viram `DOL`, e `PTX` e `INP` são descartados.

## Goals / Non-Goals

**Goals:**
- Núcleo de curvas em Java puro dentro do engine, com tipos e nomes do QuantLib, estendível por Groovy nos três tipos de modelo.
- Cadastro como única fonte de variação entre curvas, reaproveitando as colunas oficiais.
- PRE, DCL, DPL, INP e PTX construídas do TaxaSwap e conferidas contra os valores publicados.

**Non-Goals:**
- Metodologias de construção de SOFR (Bloomberg) e NTN-B (ANBIMA). Ficam só os pontos de extensão prontos.
- Bootstrap a partir de contratos, validação estatística de curva e publicação em Kafka.
- Módulo Maven separado para o núcleo. Fica como pacote do engine; separar depois é mecânico, porque o pacote não depende de Spring.

## Decisions

### D1. Pacotes do domínio
```
domain/
  curva/          CadastroCurva, CurvaData (saída da construção), Vertice, CurvaInterpolada, Proveniencia
  quantlib/       tipos com nomes do QuantLib: Compounding, Frequency, BusinessDayConvention, TimeUnit,
                  Period, InterestRate, DayCounter (+ Business252, Actual360, Actual365Fixed, Thirty360)
  matematica/     DecimalMath (pow/ln/exp em BigDecimal), Arredondamento (casas + modo, truncamento incluso)
  calendario/     Calendar (contrato do QuantLib), brazil/ (Settlement = feriados ANBIMA; B3BusinessCalendar migra),
                  unitedstates/ (depois)
  construcao/     ModeloConstrucao, prontatsb3/
  interpolacao/   Grandeza (Discount, ZeroYield, ForwardRate, CompoundFactor, Price),
                  Interpolator (linear/, loglinear/, backwardflat/, forwardflat/, cubic/),
                  extrapolacao/ (Disabled, FlatForward, FlatValue)
  modelo/         RegistroModelos<T>, CarregadorGroovy, ScriptModelo
```
Cada modelo concreto é um pacote. Os nomes Java de tipo e de constante seguem o QuantLib (ex.: `Compounding.Compounded`, `Brazil.Market.Settlement`), inclusive o PascalCase das constantes, que é o que o QuantLib-SWIG expõe em Java. **Alternativa rejeitada:** depender do QuantLib-SWIG (JNI). Ele traz binário nativo e não roda em `BigDecimal`.

### D2. Interpolação = grandeza + interpolador numérico + DayCounter do eixo
O interpolador numérico é puro: recebe `(t, ts[], ys[])` e não sabe nada de juros. A grandeza converte vértice ↔ valor interpolável:
- `Discount`: DF;
- `CompoundFactor`: 1/DF;
- `ZeroYield`: taxa;
- `Price`: preço.

O `DayCounter` do eixo gera `t`. A cotação (`InterestRate` = taxa + DayCounter + Compounding + Frequency) converte taxa ↔ DF.

Mapeamento das funções do manual, conferido fórmula a fórmula no PDF:

| Função B3 | Configuração |
|---|---|
| 1.4.2 / 1.4.4 | `Discount`+`LogLinear`, eixo `Business252` / `Actual360`, cotação `Compounded`/`Annual` |
| 1.4.3 | `Discount`+`LogLinear`, eixo `Business252`, cotação `Simple`/`Actual360` |
| 1.4.11 | `CompoundFactor`+`Linear`, eixo `Actual360`, cotação `Simple`/`Actual360`. É linear em taxa×prazo; `ZeroYield`+`Linear` daria outro resultado |
| 1.4.5 | `Price`+`LogLinear`, eixo `Business252` |
| 1.4.1 | sem mapeamento: interpola `(1+i)` geometricamente. Fica para uma grandeza nova (Java ou Groovy) quando a CYI entrar |

**Alternativa rejeitada:** um interpolador por função B3 (`FLAT_FORWARD_252`, `LINEAR_360`...). É o que multiplica classes e amarra a base de dias.

### D3. Extrapolação por lado, fora do interpolador
`ExtrapolationPolicy.extrapolar(lado, vertices, t)` é aplicada pela curva, não pelo interpolador:
- `FlatForward` reutiliza a grandeza e o interpolador do segmento adjacente com peso fora de [0,1]. Isso reproduz 1.4.6, 1.4.7 e 1.4.10 com a mesma fórmula da interpolação.
- `FlatValue` repete o valor do vértice adjacente.
- `Disabled` lança erro (padrão QuantLib).

**Alternativa rejeitada:** extrapolar dentro do interpolador, como no `enableExtrapolation()` do QuantLib. Não permite políticas diferentes no início e no fim, que o manual exige.

### D4. Onde fica cada item do cadastro
- **Colunas oficiais:**
  - `tCurvaPrvdr`: origem;
  - `tConfgCurva.cMotorCalc` / `cRotnaCalc`: modelos;
  - `tCurvaMercd.cTpoVlr` / `cNormaDia` / `cTpoJuro`: unidade e cotação.
- **`tParmConfgCurva` em chave/valor**, com nomes fixos: `GRANDEZA`, `DAY_COUNTER_TEMPO`, `FREQUENCY`, `CALENDARIO`, `MERCADO_CALENDARIO`, `BUSINESS_DAY_CONVENTION`, `EXTRAPOLACAO_INICIO`, `EXTRAPOLACAO_FIM`, `HORIZONTE` (ex.: `50Y`, no formato `Period` do QuantLib), `CASAS_DECIMAIS`, `MODO_ARREDONDAMENTO`, `VERSAO_SCRIPT_CONSTRUCAO`, `VERSAO_SCRIPT_INTERPOLACAO` e `VERSAO_SCRIPT_CALENDARIO`.
- **Vigência:** a construção de uma data usa o `tConfgCurva` vigente naquela data (`dInicVgcia <= data <= dValidAte`). É o que torna um reprocessamento de data antiga reprodutível.
- **Os parâmetros em texto do engine** (`CONVENCAO`, `MOD_DADO`, `HORIZONTE_MAX_ANOS`) e o `cModDado` deixam de ser lidos.

**Alternativa rejeitada:** colunas novas em `tConfgCurva` para cada item. Isso diverge mais do schema oficial do que usar a tabela de parâmetros que já existe para isso.

### D5. O modelo de construção monta os pontos do dado curva
Todo modelo de construção lê a fonte que conhece e monta os pontos da curva na data. No `PRONTA_TS_B3`:
1. obtém a origem do cadastro (`tCurvaPrvdr` com a menor `cPriorCsumo` da fonte B3);
2. lê `tBtrsCurvaPrimr` por código na fonte + data-base;
3. devolve os pontos (data, dias úteis, dias corridos e valor como publicados), que são gravados em `tDadoCurva` sob o código da curva de mercado.

O futuro modelo SOFR/Bloomberg segue o mesmo contrato, lendo `tBbergCurvaPrimr`.

**Alternativa rejeitada:** não gravar nada e montar os pontos da fonte a cada consulta. Isso perde o registro do que entrou na curva, amarra toda consulta à tabela bruta e impede versionar a construção.

### D6. Gravação desta fase: só os pontos
Por curva × data-base, a construção grava em `tDadoCurva` os pontos montados pelo modelo de construção (D5), com o valor arredondado pelo cadastro. Num recálculo, os pontos da data são apagados e regravados na mesma transação, sem manter versão anterior. `tCurvaData`, `tDadoVertcCurva` e `tMtrizCurva` não são gravadas.

A proveniência da construção (modelo de construção, calendário, origem Java ou Groovy, versões e hash de script, cadastro vigente) vai na resposta da API e no log estruturado. Persistir versão e proveniência exige uma tabela própria de curvas. Isso fica fora desta fase, porque o schema não tem tabela para isso: `tMtrizCurva` é de superfícies.

**Alternativa rejeitada nesta fase:** gravar a curva diária. Com a FK atual, isso obrigaria a inverter os papéis das tabelas (curva diária em `tDadoCurva`, pontos em `tCurvaData`), o que contraria a semântica original. Interpolar sob demanda (D9) evita a FK e mantém a semântica.

**Alvo ideal, fora desta mudança.** Persistir a curva interpolada diária em `tCurvaData`, mantendo a semântica original (**dado curva = pontos**, **curva data = curva diária**). Para isso, o schema oficial precisa mudar:
- remove `FK_tDadoCurva_tCurvaData`, desacoplando a curva data dos pontos, e liga `tCurvaData` a `tCurvaMercd` exatamente como `tDadoCurva` já é ligada (`FK_tCurvaMercd_tCurvaData`). Se passar a existir uma tabela de cabeçalho de curva por data (versão e proveniência), ligar a curva data a ela é preferível, porque o banco passa a garantir que toda curva diária tem a proveniência necessária para ser regerada.

Motivo: a curva data diária é o volume grande (≈ 12.600 linhas por curva e data em 50 anos) e deve ser **expurgada periodicamente**. Com a FK atual, ela fica acoplada aos pontos e o expurgo depende da ordem entre as tabelas. Sem esse acoplamento, a curva data pode ser apagada e regerada a partir dos pontos a qualquer momento, sem tocar neles.

Consistência sem a FK entre pontos e curva diária:
- **Curva diária desatualizada após recálculo:** pontos e curva diária são regravados na mesma transação, e a curva diária anterior é apagada antes.
- **Regeração idêntica após expurgo:** exige a vigência do cadastro em `tConfgCurva`, todas as versões de script em `tScriptModlCurva` (nunca apagadas) e, idealmente, a proveniência persistida numa tabela de cabeçalho de curva.
- **Construções simultâneas da mesma curva e data:** serializadas por trava de aplicação por (código, data-base), ou pela linha de cabeçalho quando ela existir.

Quando a FK for alterada no schema oficial, persistir a curva diária é aditivo. A construção passa a gravar também `tCurvaData`, até `max(último ponto, dataBase + HORIZONTE)`, e a consulta passa a ler dela em vez de interpolar. Os pontos em `tDadoCurva` não mudam.

### D7. Precisão
Tudo em `BigDecimal` com `MathContext` de 34 dígitos (`DECIMAL128`), usando `DecimalMath.pow/ln/exp` para potências fracionárias. O arredondamento do cadastro é aplicado só na gravação e na resposta. Isso segue a regra de precisão do `config.yaml`. **Alternativa rejeitada:** `double` com arredondamento no fim. Ela já produz as diferenças de convenção que o oráculo B3 detectaria.

### D8. Registro genérico e Groovy
`RegistroModelos<T>` é um só, para os três tipos. A resolução segue a spec: versão fixada → Groovy ativo → Java nativo → erro. Os nativos se registram na subida por uma lista explícita (Spring `@Component` por modelo).

Scripts ficam numa tabela nova, **`tScriptModlCurva`**, no padrão de nome legado da `develop`: `cTpoModl`, `iModl`, `cVrsaoReg`, `rScript`, `cHashScript`, `cSitReg`, `cUsuarAtulz`, `dCriacReg`, `dUltAtulz`. Os ativos são compilados na subida e mantidos em cache por (tipo, nome, versão).

Os modelos nativos expõem os pontos de extensão como métodos `protected`, e um script estende a classe nativa (`class X extends br.com.poc.domain.interpolacao.loglinear.LogLinear`).

**Sandbox:**
- `SecureASTCustomizer` com lista **permitida** de imports e receptores: `br.com.poc.domain.curva`, `...quantlib`, `...matematica`, `...calendario`, `...interpolacao`, `...construcao`, `java.math`, `java.time`, `java.util`;
- `TimedInterrupt` para o tempo limite;
- a validação executa o script contra uma `CurvaData` de fixture antes de permitir ativar ou fixar.

Isso substitui o `GroovyDynamicModelCompiler` e resolve o débito de segurança registrado (sandbox por lista de bloqueio, endpoint sem autenticação, mensagem de erro exposta).

### D9. API por código + data
Controllers novos conforme a spec `curve-engine-api`, e os antigos são removidos (BREAKING, sem convivência). A interpolação é sempre sob demanda:
1. lê os pontos gravados em `tDadoCurva` para o código e a data (404 se não houver);
2. monta grandeza + interpolador + extrapolação do cadastro, resolvidos pelo `RegistroModelos`;
3. calcula cada prazo pedido dentro do domínio `[1º dia útil, max(último ponto, dataBase + HORIZONTE)]` (422 fora dele);
4. arredonda pelo cadastro e calcula os fatores quando a curva é de taxa.

O objeto de curva montado a partir dos pontos é imutável e fica em cache por (código, data-base, versões dos modelos). A entrada é invalidada quando os pontos daquela data mudam: reconstrução ou edição.

**Código e nome.** O nome de exibição é `tCurvaMercd.cTickerIndcd`, a chave primária usada por todas as FKs do schema. O código é `tCurvaMercd.cTickerIdtfdUnic`. As duas formas de rota resolvem para `cTickerIndcd` e delegam para os mesmos serviços:
- **Rota por código:** busca exata por `cTickerIdtfdUnic`. Como o schema não garante unicidade dessa coluna e não é alterado, código ausente dá 404 e código repetido dá 409.
- **Rota por nome:** compara o nome normalizado (sem acentos, minúsculo, sem espaços nas pontas) com `cTickerIndcd` normalizado. O nome é único por ser chave primária; o 409 só ocorre se dois nomes diferirem apenas em maiúsculas ou acentos.

O catálogo (código, nome, unidade) é pequeno, então fica em cache e é invalidado quando o cadastro muda. O nome vai em parâmetro de query, e não no path, porque tem espaço, acento e caracteres como `/`. **Alternativa rejeitada:** escrita pelo nome. Nome é rótulo de exibição e pode mudar ou colidir; operações que alteram dados ficam presas ao código.

**Edição de pontos** (`PUT .../pontos`): a lista recebida é validada inteira contra o calendário cadastrado. Em seguida, na mesma transação, os pontos da data são apagados e a lista é inserida (substituição total, sem merge). A resposta relê os pontos gravados, para devolver exatamente o que ficou no banco. É o mesmo caminho de gravação da construção (D6), com a origem registrada como manual no log. Assim, consultas repetidas não relêem o banco nem remontam a interpolação. O Redis que o engine já configura pode guardar esse cache entre réplicas.

### D10. Conector
`curveB3TypeCatalog` passa a ter uma entrada por código exato (`PRE`, `DCL`, `PTX`, `DPL`, `INP`, `ZUS`, `TIC`...). `normalizeCurveType` casa primeiro pelo código exato, e a descrição só é usada para código ausente do catálogo. O cálculo de fatores em `curveB3Factors.ts` não é alterado: o processor já descarta esses campos, e a fonte de verdade dos fatores passa a ser o engine.

## Risks / Trade-offs

- **Schema da `develop` diverge do código em dois pontos.** `tParmConfgCurva` tem PK só em `cldtfdConfg` e `cConfgIdtfd` como `INT`, mas o engine espera chave composta e texto. → Uma migração torna a PK `(cldtfdConfg, cConfgIdtfd)` com `cConfgIdtfd VARCHAR(50)`. Antes de aplicar em ambiente real, conferir com o schema do banco de origem.
- **Custo da interpolação em tempo de execução.** Cada consulta interpola na hora. → Cache do objeto de curva por versão (D9). O custo por prazo é constante após o cache, e uma curva de ~278 pontos cabe inteira em memória.
- **Curva diária não persistida.** Consumidores que precisam da curva dia a dia, como a curve-api lendo `tCurvaData`, não a encontram gravada nesta fase. → A API de interpolação atende por prazo; a persistência diária entra com o alvo ideal (D6).
- **As colunas de `tDadoCurva` divergem entre o schema e o engine.** O schema tem `dVertcReft` e `vPrecoTx`; a entidade do engine usa `dtVerticeReferencia`, `cDiaUtil`, `cQtdDiaReft`, `vDiaFator` e `vFatorCalc`. → A entidade se ajusta ao schema, que não é alterado. Dias úteis e dias corridos são recalculados pelo calendário cadastrado a partir da data do ponto. Um teste confere, para a data real do TaxaSwap, que o recálculo bate com os dias publicados.
- **O processor grava o B3 em `mkt.B3CurveRaw`, não em `tBtrsCurvaPrimr`.** → Tarefa de alinhar a entidade B3 do processor com `tBtrsCurvaPrimr`, como já foi feito com a ANBIMA (`tAnbmaCurvaPrimr`).
- **`tBtrsCurvaPrimr.cTickerIndcd` tem FK para `tCurvaMercd`.** Guardar ali o código na fonte exige que esses códigos existam em `tCurvaMercd`. → Os códigos da fonte (`PRE`, `DCL`...) são cadastrados em `tCurvaMercd` como curvas primárias. A curva de mercado pode ter o mesmo código e apontar para eles via `tCurvaPrvdr`.
- **Groovy pode sobrescrever um nativo usado por todas as curvas.** → Ativar exige validação. Fixar versão por curva permite testar antes, e a proveniência mostra qual versão gerou cada curva.
- **`FlatForward` no início e `FlatValue` no fim não existem no QuantLib.** Um script "estilo QuantLib" não os conhece. → Os nomes ficam documentados como extensão, e o comportamento padrão continua sendo o do QuantLib (`Disabled`).
- **Diferença de centésimo contra o TaxaSwap por convenção errada no cadastro.** → Testes de oráculo por curva (tarefas) com os valores publicados arredondados pela política da curva, comparação exata.

## Migration Plan

1. Alterações no DDL do schema de curvas de mercado, acompanhadas de migração versionada: PK de `tParmConfgCurva`, criação de `tScriptModlCurva` e carga do cadastro das 5 curvas (`tCurvaMercd`, `tCurvaPrvdr`, `tConfgCurva`, `tParmConfgCurva`).
2. Deploy do conector com o catálogo novo e do processor gravando `tBtrsCurvaPrimr`.
3. Deploy do engine com os endpoints novos. Não há convivência com os antigos. Os clientes internos (curve-bff) migram no mesmo release.
4. **Rollback:** voltar o deploy do engine e do conector. As migrações são aditivas, exceto a PK de `tParmConfgCurva`, que tem script reverso.

## Open Questions

- Quando alterar `FK_tDadoCurva_tCurvaData` no schema oficial para o alvo ideal (D6) e qual a política de expurgo da curva data diária: retenção por quantidade de datas-base ou por idade.

- `cLingSist`, `cPreCalc`/`cPosCalc` e `cPreMotorCalc`/`cPosMotorCalc` existem em `tConfgCurva` e sugerem ganchos pré e pós cálculo no sistema original. Esta mudança não os usa. Dá para mapear para scripts Groovy de gancho numa mudança futura sem alterar o desenho.
