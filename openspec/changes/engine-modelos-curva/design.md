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
| `tDadoCurva` | pontos gravados pelo engine: `dBaseReft`, `cTickerIndcd`, `dVertcReft`, `vPrecoTx` |

**Modelo de dados.** `tDadoCurva` (dado curva) guarda os **pontos** da curva na data-base. `tCurvaData` (curva data) seria a curva interpolada diária e tem FK para `tDadoCurva` (`FK_tDadoCurva_tCurvaData`); não é gravada nesta fase. `tDadoVertcCurva` e `tMtrizCurva` (reservada a superfícies) também não são gravadas.

```
tabela bruta ─ modelo de construção ─► tDadoCurva (pontos)
                  consulta pela API ─► interpolação em tempo de execução a partir de tDadoCurva
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
- CRUD de cadastro de curva e provedor: é do serviço de cadastro (`acts-srv-curvas` no sistema real; `services/curves` no poc, mudança futura). O engine só lê o cadastro. Os testes usam fixtures com o cadastro definido nas specs.
- Preencher as tabelas brutas: é do conector e do processor (D10).

## Decisions

### D1. Pacotes do domínio
```
domain/
  curva/          CadastroCurva, PontoConstruido, Ponto, CurvaInterpolada, Proveniencia, hashPontos
  quantlib/       Compounding, Frequency, BusinessDayConvention, TimeUnit, Period, InterestRate,
                  DayCounter (+ Business252, Actual360, Actual365Fixed, Thirty360)
  matematica/     DecimalMath (pow/ln/exp em BigDecimal), Arredondamento
  calendario/     Calendar (base: contagem, advance, adjust), brazil/, unitedstates/,
                  CalendarioPorLista (base dos calendários importados por planilha)
  construcao/     ModeloConstrucao, ContextoConstrucao, LeitorInsumos, pontosprontos/,
                  prontatsb3/, ntnbbootstrapanbima/, sofrzerobloomberg/
  interpolacao/   Grandeza (Discount, CompoundFactor, ZeroYield, Price), Interpolador,
                  InterpoladorLocal (valorNoSegmento), linear/, loglinear/, backwardflat/,
                  forwardflat/, cubic/, extrapolacao/ (Disabled, FlatForward, FlatValue)
  memoria/        MemoriaCalculo e suas linhas (insumo, ponto, fluxo, prazo, evento)
  modelo/         RegistroModelos<T>, CarregadorGroovy, RepositorioScripts (porta)
  tempo/          Relogio (único ponto de "hoje" e de instantes, em America/Sao_Paulo)
adapter/out/planilha/  PlanilhaMemoriaCalculo (Apache POI)
adapter/out/blob/      RepositorioScriptsBlob (Azure Blob Storage)
```
Nomes de tipo e de constante seguem o QuantLib (`Compounding.Compounded`, `Brazil.Market.Settlement`), inclusive o PascalCase das constantes. **Alternativa rejeitada:** QuantLib-SWIG (JNI), que traz binário nativo e não roda em `BigDecimal`.

### D2. Interpolação = grandeza + interpolador + DayCounter do eixo
O interpolador é puro: recebe `(x, xs, ys)` e não sabe nada de juros. A grandeza converte ponto ↔ `y`, o `DayCounter` do eixo gera `x`, e a cotação (`InterestRate`) converte taxa ↔ fator. As funções do Manual de Curvas B3 viram configuração (tabela na spec `curve-build-pipeline`). A 1.4.1 (interpolação geométrica de `(1+i)`) não tem mapeamento e fica para uma grandeza nova em Java, numa mudança futura. `ForwardRate` fica fora desta fase. **Alternativa rejeitada:** um interpolador por função B3, que multiplica classes e amarra a base de dias.

### D3. Extrapolação por lado, fora do interpolador
A curva aplica a política de início ou de fim; o interpolador só é chamado dentro de `[x_1, x_n]`. `FlatForward` chama `valorNoSegmento` do interpolador local com `w` fora de `[0, 1]`, o que reproduz 1.4.6, 1.4.7 e 1.4.10 com a mesma fórmula da interpolação; por isso exige `Linear` ou `LogLinear`. `FlatValue` repete o valor do ponto (taxa, preço ou pontos), e não a grandeza, como 1.4.8 e 1.4.9. **Alternativa rejeitada:** `enableExtrapolation()` do QuantLib, que não permite políticas diferentes no início e no fim.

### D4. Cadastro
Itens, colunas, valores aceitos e obrigatoriedade estão na spec `curve-build-pipeline`. Decisões:
- **Sem mudança de schema nesta fase.** `tParmConfgCurva` tem PK só em `cldtfdConfg`, então guarda um parâmetro por configuração, e o cadastro tem cerca de 14. Os parâmetros ficam como um objeto JSON em `tConfgCurva.cModDado` (`VARCHAR(1024)`, sobra espaço), coluna que o engine antigo usava para o "modo de dado" e que o engine novo não usa mais. Vantagem: o JSON fica na mesma linha da vigência, então muda junto com ela. Reaproveitar a coluna precisa ser confirmado com o dono do schema. O alvo ideal, quando o banco puder mudar, é `tParmConfgCurva` em chave/valor com PK `(cldtfdConfg, cConfgIdtfd)`.
- Chave desconhecida é erro: um nome digitado errado (`EXTRAPOLACAO_FINAL`) cairia silenciosamente no padrão `Disabled`.
- Só dois padrões existem: `Disabled` para as extrapolações. Todo o resto é obrigatório.
- A vigência de `tConfgCurva` torna reprodutível o reprocessamento de uma data antiga.
- Origem: só a linha de menor `cPriorCsumo`; troca automática de fonte fica fora.
- Os parâmetros em texto antigos (`CONVENCAO`, `MOD_DADO`, `HORIZONTE_MAX_ANOS`) deixam de ser lidos.

**Alternativas rejeitadas:** colunas novas em `tConfgCurva` (mudam o schema); parâmetros no Blob (separariam o cadastro do banco e da vigência).

### D5. Modelo de construção
O contrato está na spec `curve-extension-models`. O modelo recebe um `ContextoConstrucao` com o cadastro, a data-base, o calendário e o `LeitorInsumos`, que é o único acesso às tabelas brutas. Scripts Groovy não acessam o banco. Cada modelo declara a fonte e o produto que aceita (`PRONTA_TS_B3`: `B3`/`TS`; `NTNB_BOOTSTRAP_ANBIMA`: `ANBIMA`/`TP`; `SOFR_ZERO_BLOOMBERG`: `BLOOMBERG`/`ZR`), e o pipeline rejeita cadastro que aponte um modelo para outra fonte. O modelo devolve pontos sem arredondamento; o pipeline arredonda e grava.

### D6. Gravação: só os pontos, numa transação travada
A construção grava em `tDadoCurva` só os pontos arredondados. A transação começa com um `SELECT` com trava de escrita (`PESSIMISTIC_WRITE`, tempo limite de 30 segundos) na linha da curva em `tCurvaMercd`, e a edição de pontos usa a mesma trava. Isso serializa construções e edições da mesma curva entre réplicas sem tabela nova. Quem não obtém a trava recebe `CONSTRUCAO_EM_ANDAMENTO`. A simulação não trava nada.

A proveniência vai na resposta, no log e no registro de auditoria do Blob (D23); o banco não tem tabela para ela, porque `tMtrizCurva` é de superfícies.

**Alvo ideal, fora desta mudança:** persistir a curva diária em `tCurvaData`, mantendo dado curva = pontos e curva data = curva diária. Exige remover `FK_tDadoCurva_tCurvaData` e ligar `tCurvaData` a `tCurvaMercd` (ou a uma futura tabela de cabeçalho de curva por data). A curva diária é o volume grande (≈ 12.600 linhas por curva e data em 50 anos) e precisa ser expurgável sem tocar nos pontos. Quando a FK mudar, a construção passa a gravar também `tCurvaData` até o fim do domínio, sem mudar os pontos.

### D7. Precisão
`BigDecimal` com `MathContext.DECIMAL128` e `DecimalMath.pow/ln/exp` para potências fracionárias, sem arredondamento intermediário. O arredondamento do cadastro vale só para o valor da curva, na gravação e na resposta. Os fatores saem do valor já arredondado, com 16 casas: quem lê a taxa publicada consegue reproduzir o fator. **Alternativa rejeitada:** `double` com arredondamento no fim, que já produz as diferenças que o oráculo B3 detecta.

### D8. Registro genérico e Groovy
`RegistroModelos<T>` é um só para os três tipos, com a resolução: versão fixada → `ATIVA` → Java nativo → erro. Os nativos se registram na subida (`@Component` por modelo).

**Scripts no Blob Storage, não no banco.** O engine roda em duas ou mais instâncias no Azure, e um script ativado numa instância precisa chegar às outras. Os scripts ficam no Blob já usado pelo projeto, em `groovy-models/{tipo}/{nome}/`: um arquivo imutável por versão (`v{n}.groovy`) e um `estado.json` com a versão ativa e o status, o hash e o autor de cada versão (formato na spec `curve-extension-models`).
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

Toda consulta relê os pontos de `tDadoCurva` e monta a curva na hora: são no máximo algumas centenas de linhas por curva e data. **Alternativa rejeitada nesta fase:** cache do objeto de curva, que exigiria invalidação entre réplicas na edição e na reconstrução. É fonte de erro sem ganho medido; entra depois, se a latência pedir.

**Código e nome.** A rota por código busca `cTickerIdtfdUnic`, e a rota por nome compara o nome normalizado com `cTickerIndcd`. As duas resolvem para `cTickerIndcd` e usam os mesmos serviços. O nome vai em query, não no path, porque tem espaço, acento e `/`. **Alternativa rejeitada:** escrita pelo nome. Nome é rótulo e pode mudar ou colidir, então operações que alteram dados ficam presas ao código.

**Edição de pontos:** valida a lista inteira, apaga e insere os pontos na transação travada (D6), relê o que ficou gravado e registra `PONTOS_EDITADOS`.

### D10. O engine só lê as tabelas brutas
Os modelos esperam das tabelas brutas o contrato abaixo. Preenchê-las é do conector e do processor, em changes próprios. Nos testes, as tabelas são carregadas por fixture.

| Tabela bruta | O engine espera | Situação hoje |
|---|---|---|
| `tBtrsCurvaPrimr` | uma linha por vértice, `cTickerIndcd` = nome da curva de mercado ligada, em `tCurvaPrvdr`, ao código exato da curva no `TaxaSwap.txt`, `cDiaCorri`, `cDiaUtil`, `vPrecoTx` em percentual | o conector classifica pela descrição (`DCL`/`DPL` viram `DOL`, `PTX`/`INP` são descartados) e o processor grava em `mkt.B3CurveRaw` |
| `tAnbmaCurvaPrimr` | uma linha por título, `cTickerIndcd` = nome da curva de mercado: `vPrecoTx` = taxa indicativa em percentual, `vVertcCurva` = prazo em dias úteis | colunas existem; unidade de `vVertcCurva` e escala de `vPrecoTx` não confirmadas |
| `mkt.SofrCurveRaw` | `curve_member`, `tenor`, `ref_date`, `valor DECIMAL(28,12)` em percentual | tabela e ingestão inexistentes; feeder não localizado |

Além das tabelas, o processor chama o webhook `POST /api/v1/cargas` depois do commit de cada carga, com a quantidade de linhas por código na fonte, e repete com o mesmo `idCarga` até receber 2xx (D22). Para a B3, isso está especificado no change `conector-b3-webhook-ingest` (conector publica uma mensagem por carga; o processor grava `tBtrsCurvaPrimr` e avisa o engine).

### NTN-B (ANBIMA): `NTNB_BOOTSTRAP_ANBIMA`

**Contexto.** Nenhuma referência do projeto faz bootstrap de título com cupom. O `curve-platform` tem `CdiRateHelper` e `Di1RateHelper` (instrumentos zero-cupom) e um `CurveBootstrapper` que só ordena taxas já implícitas. O algoritmo completo está na spec `ntnb-anbima-curve-model`.

### D11. Leitura de `tAnbmaCurvaPrimr` como está
O vencimento vem do prazo: `P = B + vVertcCurva dias úteis`, e o vencimento nominal é o dia 15 do mês de `P`. A checagem "dia 15 ajustado = `P`" confirma a hipótese de dias úteis a cada construção: se a unidade estiver errada, a construção falha com `INSUMO_INVALIDO` em vez de gerar uma curva errada. **Alternativa rejeitada:** coluna nova de vencimento (`dVctoTitulo`), que mudaria o schema oficial e a ingestão sem necessidade.

### D12. Bisseção, não Newton
Cada título resolve `f(z) = 0` com uma incógnita, mas os cupons entre o último título resolvido e o vencimento dependem de `z` pela interpolação. A bisseção em `[−0,99; 1,00]` é determinística, não precisa de derivada e sempre termina (≈ 48 iterações até `10^−14`). O "sem troca de sinal" vira um erro claro. **Alternativa rejeitada:** Newton, que é mais rápido mas pode divergir e exige derivada da interpolação. Também foi rejeitado resolver todos os títulos de uma vez por mínimos quadrados (como a ETTJ), que é mais difícil de auditar ponto a ponto.

### D13. Cupom fixo no modelo
Todas as NTN-B pagam 6% a.a. real, semestral. É metodologia do modelo, não varia entre curvas, então fica como constante do `NTNB_BOOTSTRAP_ANBIMA`. Se a regra mudar, o modelo pode ser sobrescrito por Groovy, sem deploy. **Alternativas rejeitadas:** parâmetros de cupom no cadastro e tabela de séries de título, que guardariam um dado igual para todas.

### D14. Cupons antes do primeiro título
No primeiro título, todos os eventos são descontados pela própria incógnita, o que dá `z_1 = y_1`. Nos títulos seguintes, eventos antes do primeiro vencimento usam `z_1` (taxa zero constante). A extrapolação de início `FlatValue` da curva gravada usa a mesma hipótese, então bootstrap e consulta são coerentes nesse trecho.

### SOFR (Bloomberg): `SOFR_ZERO_BLOOMBERG`

**Contexto.** `S0490Z <tenor> BLC2 Curncy` é a série de **zero rates** do SOFR do Bloomberg, segundo [A Smoother Path to SOFR Curve Construction](https://www.lucidogroup.io/smoother-path-to-sofr-curve-construction/). Não há bootstrap, e o caso é análogo à `ZUS` do Manual de Curvas B3 (item 2.11). O `BloombergCurveRaw` do processor tem formato de contrato futuro e não serve.

### D15. Tabela de nós própria
`mkt.SofrCurveRaw (curve_member, tenor, ref_date, valor DECIMAL(28,12))`, com chave natural `(curve_member, tenor, ref_date)`. O `valor` é decimal, não `double`, pela regra de precisão (D7). **Alternativa rejeitada:** reaproveitar `tBbergCurvaPrimr` com `tenor` opcional, que mistura contrato futuro e nó de curve member na mesma tabela.

### D16. Tenor por `Period`
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
Logs JSON (o engine já tem `logstash-logback-encoder`) com `correlationId`, código, nome e data-base nos eventos da spec `curve-build-pipeline`. O histórico persistente de quem gravou o quê está na auditoria do Blob (D23), e o log é o complemento operacional. O `hashPontos` liga uma consulta ou simulação à construção ou edição que gravou aqueles pontos.

Roteiro para "a curva X da data D está errada":
1. Baixar `GET /curvas/X/D?formato=xlsx` e anotar o `hashPontos` gravado.
2. Consultar `GET /curvas/X/D/historico`: a linha mais recente diz se os pontos vieram de construção, reconstrução ou edição, com usuário, motivo, `idCarga` e `hashPontos`. Os pontos substituídos estão em `historico/{idAuditoria}`.
3. Baixar `GET /curvas/X/D/simulacao?formato=xlsx`. Se o `hashPontos` simulado for igual ao gravado, a construção fez o que o insumo e o cadastro atuais mandam, e o erro está no insumo (aba `Insumos`) ou no cadastro (aba `Resumo`). Se for diferente, a coluna `Diferenca` mostra quais pontos mudaram desde a construção, e o cadastro registrado na auditoria da construção pode ser comparado com o do `Resumo`.
4. Se a simulação falhar, `Resumo` e `Eventos` dão o erro, e `Insumos` mostra a linha.
5. Para um prazo interpolado suspeito: `GET /curvas/X/D/interpolacao?du=N&formato=xlsx`, que mostra os vizinhos, o `W` e o `Y` de cada prazo.

### D22. Carga concluída por webhook do processor
O processor é quem sabe que terminou de gravar o bruto, então é ele que avisa (`POST /api/v1/cargas`, spec `curve-load-trigger`). Decisões:
- **O aviso é o gatilho e a trava.** Nenhuma construção roda sem carga registrada, e o modelo confere se leu exatamente a quantidade avisada. Com o Blob fora, o webhook usa a carga do próprio corpo e a construção manual segue com o aviso `CARGA_NAO_VERIFICADA` (D26). Isso cobre carga parcial, leitura antes do commit e réplica de banco atrasada.
- **Construção síncrona na própria requisição.** São poucas curvas por carga (5 da B3, 1 da ANBIMA, 1 do SOFR), construídas em segundos. A durabilidade vem do retry do processor com o mesmo `idCarga` e da idempotência do engine, sem fila nem varredura agendada.
- **Registro no Blob**, como os scripts Groovy: o webhook cai numa instância só, e o registro precisa ser visível às outras, sem tabela nova no schema oficial.
- **A carga nunca recalcula.** Ela só constrói curvas que ainda não têm pontos na data. Uma republicação da fonte (`idCarga` novo) é registrada, a carga anterior vai para o `historico`, e cada curva que já tinha pontos é mantida com o aviso `PONTOS_DE_CARGA_ANTERIOR`. Recalcular é decisão de um operador, por `forcarRecalculo=true`, porque a curva gravada pode já ter sido consumida.

**Alternativas rejeitadas:**
- Engine consultar periodicamente se a carga terminou: exige que o engine saiba o que é "completo" para cada fonte, e isso é conhecimento do processor.
- Fila (Kafka) em vez de webhook: o engine precisaria de um consumidor e de controle de offset para um evento por fonte e dia. O webhook com retry dá a mesma garantia com menos peças.
- Construção assíncrona com resposta 202: exigiria fila interna e varredura de pendências para não perder o gatilho se a instância cair.

### D23. Auditoria sem mudar o schema: Blob imutável + colunas de cálculo de `tCurvaMercd`
O banco não pode ser alterado nesta fase. A trilha fica em dois lugares:
- **Histórico completo no Blob:** um registro imutável por gravação de pontos, em `auditoria/{codigo}/{dataBase}/`, com usuário, motivo, carga, `hashPontos` antes e depois, proveniência e os pontos substituídos (spec `curve-audit-history`). A pasta tem política de imutabilidade do Azure (retenção por tempo), então nem o próprio engine consegue apagar.
- **Resumo em `tCurvaMercd`:** a construção atualiza `dBaseReft` (maior data-base construída) e `cUsuarCalc` (quem calculou), colunas do schema oficial que não eram usadas por ninguém (o `curve-api-legado` só lê `cUsuarCalc`). É a mesma linha que a construção já trava (D6), então não há custo extra de concorrência.

**Consistência entre Blob e banco.** O Blob não participa da transação. O registro é gravado **antes** do commit. Se o Blob falhar, a gravação dos pontos segue (o Blob nunca bloqueia construção, D26): o registro completo vai para o log (`AUDITORIA_PENDENTE`), que passa a ser a cópia de segurança, e é regravado no Blob em segundo plano quando ele voltar. Se a instância cair antes disso, o registro fica só no log. Se o commit falhar depois da gravação no Blob, fica um registro órfão, marcado com `.desfeita.json` e mostrado como `DESFEITA` no histórico.

**Alvo ideal, quando o banco puder mudar:** tabelas `tAuditCurva` e `tHistDadoCurva` na mesma transação dos pontos. **Alternativa rejeitada:** só log, que tem retenção limitada e não é consultável como histórico.

### D24. Leitura consistente sem opção nova no banco
A reconstrução apaga e insere na mesma transação. No `READ COMMITTED` padrão do SQL Server, uma leitura concorrente espera o commit em vez de ver a data vazia, então a consistência já está garantida, desde que ninguém use `NOLOCK`. A espera é limitada ao tempo de uma construção (segundos). `READ_COMMITTED_SNAPSHOT` eliminaria a espera e fica como melhoria quando o banco puder mudar.

### D25. Autenticação e papéis pelo Entra ID
Todas as rotas exigem JWT do Entra ID. O acesso é por papéis de aplicação: `Curvas.Leitura`, `Curvas.Operador`, `Curvas.Processor` (só a identidade de serviço do processor, por client credentials), `Curvas.ModelosAutor` e `Curvas.ModelosAprovador`. Quem ativa um script pode ser o próprio autor, porque muitas vezes há um só operador no horário; o `estado.json` e a auditoria registram quem ativou. A leitura também exige papel, porque a curva é dado de mercado usado em risco e precificação. O Blob é acessado por Managed Identity, sem chave em configuração. **Alternativa rejeitada:** chave de API por cliente, que não identifica o usuário para a auditoria e exige rotação manual.

### D26. Resiliência
Os tempos limite, a política de repetição, a saúde, os logs de dependência e as métricas estão na spec `curve-engine-resilience`. Decisões:
- **Só repete o que é idempotente:** leituras e gravação condicional de versão imutável. Gravação no banco e no `estado.json` devolvem o erro, para não duplicar efeito.
- **Estado de script desatualizado mantido sem prazo, só com log.** Sem isso, uma queda do Blob derrubaria todas as consultas no fechamento, porque toda resolução de modelo lê o estado. Com o Blob fora, ninguém consegue ativar ou desativar script (essas operações também escrevem no Blob), então o último estado lido continua correto. A única exceção é uma queda parcial, em que uma instância enxerga o Blob e outra não; ela aparece no aviso de log e na métrica de idade do estado, que serve para alerta.
- **Blob fora nunca bloqueia construção nem consulta.** No fechamento, construir com o que existe vale mais do que esperar o Blob. Cada dependência do Blob tem uma degradação definida: auditoria pendente no log, carga do corpo do webhook (ou não verificada na construção manual), estado de script desatualizado ou, na falta dele, modelos nativos. Toda degradação aparece na resposta, no log e na proveniência (`estadoScript`, avisos), e não em silêncio.
- **Circuit breaker no Blob.** Depois de 5 falhas seguidas, o engine para de chamar o Blob por 60 segundos, para que uma queda não some um tempo limite a cada operação no meio do fechamento.
- **Prontidão depende do banco e, na subida, espera o Blob por até 5 minutos.** Sem banco não há o que fazer. Uma instância nova espera o Blob para carregar os scripts ativos; se ele não voltar em 5 minutos, ela fica pronta com os modelos nativos, com erro no log, para não deixar o fechamento sem instância.

### D27. Calendário por planilha, gerado como Groovy
Feriado decretado de última hora não pode esperar deploy. A planilha de feriados vira um script Groovy de calendário, e não uma tabela ou um arquivo lido direto, por dois motivos: reaproveita todo o ciclo de versão, validação, ativação, propagação entre instâncias e proveniência dos scripts; e o script gerado é uma subclasse mínima de `CalendarioPorLista`, sem lógica, só com a lista de datas. A geração é determinística (mesma planilha, mesmo hash). A exportação usa o mesmo formato da importação, de modo que o fluxo de manutenção é exportar, editar e importar. A cobertura é explícita: fora dela o calendário falha, em vez de supor dia útil. **Alternativa rejeitada:** tabela de feriados no banco (o banco não pode mudar nesta fase, e ficaria fora do versionamento dos modelos).

### D28. Pacote de depuração com o código que rodou
A proveniência diz quais modelos e versões rodaram, mas investigar exige o código. O zip leva a planilha, o JSON e o código-fonte de cada script Groovy tirado da memória da instância (o texto que foi compilado, não uma releitura do Blob), mais um manifesto com os hashes. Modelos nativos são identificados pela versão do engine (artefato e commit), gravada no build. Com isso, um caso de produção pode ser reproduzido fora do ambiente, com o mesmo código.

### D29. Datas e horários de Brasília, testes em massa
- **Horário de Brasília em tudo:** os servidores do Azure rodam em UTC, e às 21h de Brasília já é o dia seguinte em UTC. O engine nunca usa o fuso padrão da JVM; "hoje" e instantes são calculados com `America/Sao_Paulo` e gravados com o deslocamento. Os testes rodam com a JVM em UTC, para pegar qualquer uso do fuso padrão.
- **Regressão com muitos pregões:** o oráculo contra a B3 roda sobre pelo menos 12 meses de `TaxaSwap.txt`, cobrindo os feriados móveis e a virada de ano, onde erros de calendário aparecem. Somam-se testes de propriedade (ponto preservado, determinismo, ida e volta taxa↔fator em valores aleatórios) e um teste de precisão do `DecimalMath` contra uma referência de alta precisão.
- **Sem contrato de API versionado nesta fase.**

## Risks / Trade-offs

- **Parâmetros em JSON numa coluna legada (`cModDado`).** Outro sistema pode usar essa coluna com outro sentido. → Confirmar com o dono do schema antes do apply; o engine rejeita qualquer conteúdo que não seja o JSON esperado, então um uso diferente aparece como `CADASTRO_INVALIDO`, nunca como curva errada.
- **Engine passa a escrever em `tCurvaMercd`** (`dBaseReft`, `cUsuarCalc`), tabela do cadastro. → Só essas duas colunas, na linha já travada; confirmar com o dono do cadastro que elas são de cálculo.
- **Curva diária não persistida.** Quem precisa da curva dia a dia (a curve-api lendo `tCurvaData`) não a encontra. → A interpolação atende por prazo; a persistência entra com o alvo ideal (D6).
- **Entidade de `tDadoCurva` do engine diverge do schema** (`dtVerticeReferencia`, `cDiaUtil`, `vDiaFator`...). → A entidade passa a ter só as quatro colunas do schema; dias e fatores são calculados.
- **Tabelas brutas ainda fora do contrato de D10.** → Testes com fixtures; os changes do conector e do processor precisam entrar antes do deploy.
- **`tBtrsCurvaPrimr.cTickerIndcd` tem FK para `tCurvaMercd`.** → O processor grava os vértices sob o nome da curva de mercado, mapeada pelo `tCurvaPrvdr`; não há linhas de "curva da fonte" em `tCurvaMercd`.
- **Calendário desatualizado bloqueia a construção B3** (checagem `DU` = `cDiaUtil`). → É intencional: é melhor falhar com o vértice nomeado do que gravar fatores errados. A correção é um feriado no calendário, inclusive via Groovy, sem deploy.
- **Groovy pode sobrescrever um nativo usado por todas as curvas.** → Validação obrigatória, fixação por curva para testar antes, e proveniência em toda resposta.
- **Janela de até 30 segundos após uma ativação em que instâncias diferentes usam versões diferentes.** → Cada resposta e cada log informam a versão usada; quem precisa de troca imediata numa curva fixa a versão no cadastro.
- **Dependência do Blob para resolver modelos.** → Cache de estado de 30 segundos, classes compiladas em memória e último estado mantido sem prazo com o Blob fora (D26).
- **`FlatForward` no início e `FlatValue` no fim não existem no QuantLib.** → Documentados como extensão; o padrão é `Disabled`.
- **Escala e unidade dos brutos ANBIMA e SOFR não confirmadas** (percentual, dias úteis). → A checagem do dia 15 (D11) pega a unidade da NTN-B. A escala aparece na primeira simulação com dado real (uma taxa de 0,06 em vez de 6 salta aos olhos na planilha).
- **Natureza zero rate e convenção (`Actual360`/`Simple`) do SOFR vêm de fonte de terceiro.** → Confirmar no Terminal antes do apply. A convenção é cadastro. Se for par rate, o modelo ganha um passo de bootstrap sem mudar D15 e D16.
- **Republicação da fonte deixa a curva gravada desatualizada até alguém recalcular.** → O webhook devolve o aviso `PONTOS_DE_CARGA_ANTERIOR`, o log registra `CARGA_REPUBLICADA` com nível `AVISO` (base para alerta), e a planilha mostra o `idCarga` da carga registrada ao lado do que gerou os pontos.
- **Processor sem retry perde o gatilho.** → A curva não é construída; um alerta de curva não construída até o horário combinado pega o caso, e a construção manual continua possível depois que a carga é registrada.
- **Auditoria no Blob fora da transação do banco.** → Gravada antes do commit; órfãos marcados como `DESFEITA`; com o Blob fora, a cópia fica no log até ser regravada (D23). Uma instância que cai antes de regravar deixa o registro só no log, que precisa ter retenção compatível.
- **Instância nova com o Blob fora usa modelos nativos.** Se houver script Groovy ativo, a curva sai com a matemática nativa. → `estadoScript` = `DESCONHECIDO` na proveniência e erro no log; a simulação depois da volta do Blob mostra a diferença, e o recálculo corrige.
- **Leitura espera o commit de uma reconstrução em andamento.** → Espera de segundos, limitada pelo tempo limite de comando; RCSI resolve quando o banco puder mudar.
- **Simulação em produção gera carga de leitura.** → É só leitura e não trava nada; o limite de 5.000 prazos por chamada contém o custo.

## Migration Plan

1. Sem migração de banco. No build: versão do artefato e commit gravados no pacote do engine. No Blob: pastas `groovy-models/`, `cargas/` e `auditoria/` (esta com política de imutabilidade) e acesso do engine por Managed Identity. No Entra ID: registro da aplicação com os cinco papéis e atribuição à identidade do processor. No cadastro: parâmetros de cada curva em `tConfgCurva.cModDado`.
2. Pré-requisitos, fora deste change: tabelas brutas no contrato de D10 (conector e processor), chamada do webhook de carga pelo processor e cadastro das 7 curvas pelo processo de cadastro do projeto, com os valores das specs.
3. Deploy do engine com as rotas novas, sem convivência com as antigas. Os clientes internos (curve-bff) migram no mesmo release, em change próprio.
4. **Rollback:** voltar o deploy do engine. Não há migração de banco para reverter.

## Open Questions

- Quando alterar `FK_tDadoCurva_tCurvaData` para o alvo ideal (D6) e qual a política de expurgo da curva diária.
- `cLingSist`, `cPreCalc`/`cPosCalc` e `cPreMotorCalc`/`cPosMotorCalc` de `tConfgCurva` sugerem ganchos pré e pós cálculo. Não são usados; podem virar scripts Groovy de gancho numa mudança futura.
- Unidade de `tAnbmaCurvaPrimr.vVertcCurva` e escala de `vPrecoTx`, a confirmar com a ingestão ANBIMA.
- Prazo de retenção da pasta `auditoria/` (exigência regulatória ou interna).
- Quando o banco puder mudar: `tParmConfgCurva` em chave/valor, tabelas de auditoria e `READ_COMMITTED_SNAPSHOT`.
- Confirmar no Bloomberg Terminal que `S0490Z ... BLC2 Curncy` é zero rate, qual a convenção de cotação e a causa do `1D` duplicado.
