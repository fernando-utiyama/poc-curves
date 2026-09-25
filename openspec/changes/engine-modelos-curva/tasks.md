## 1. Tipos QuantLib e matemática decimal

- [ ] 1.1 Criar `domain/quantlib` com `Compounding`, `Frequency` (mesmos valores numéricos do QuantLib), `BusinessDayConvention`, `TimeUnit` e `Period` (parse de `50Y`, `6M`, `21D`), com constantes idênticas às do QuantLib; verificar com teste unitário que lista as constantes e os valores de `Frequency`
- [ ] 1.2 Criar `domain/matematica` com `DecimalMath` (`pow`, `ln`, `exp` em `BigDecimal`, `MathContext.DECIMAL128`) e `Arredondamento` (casas + modo, com truncamento); verificar com testes de `pow` fracionário contra valores conhecidos e de truncamento vs arredondamento
- [ ] 1.3 Criar `DayCounter` e `Business252`, `Actual360`, `Actual365Fixed`, `Thirty360` (`dayCount` e `yearFraction`); verificar com testes de contagem entre datas com feriado, virada de mês e fim de mês (30/360)
- [ ] 1.4 Criar `InterestRate` (taxa + DayCounter + Compounding + Frequency) com `compoundFactor`, `discountFactor` e `impliedRate` a partir de fator; verificar com testes para `Compounded`/`Annual` em `Business252` e `Simple` em `Actual360`, ida e volta taxa→fator→taxa exata

## 2. Calendário

- [ ] 2.1 Criar o contrato `Calendar` (`isBusinessDay`, `businessDaysBetween`, `advance`, `adjust` por `BusinessDayConvention`) e `Brazil` com `Brazil.Market.Settlement`, migrando os feriados do `B3BusinessCalendar`; verificar com teste que conta os dias úteis de `2026-09-14` até vértices do `TaxaSwap.txt` e bate com o `cDiaUtil` publicado
- [ ] 2.2 Expor os pontos de extensão `protected` do calendário (feriados do ano, teste de dia útil); verificar com teste de subclasse Java que acrescenta um feriado e altera só a contagem daquele dia

## 3. Interpolação e extrapolação

- [ ] 3.1 Criar os interpoladores numéricos puros `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat` e `Cubic` (spline natural) em pacotes próprios sob `interpolacao/`; verificar com testes numéricos, incluindo valor exato nos nós
- [ ] 3.2 Criar as grandezas `Discount`, `ZeroYield`, `ForwardRate`, `CompoundFactor` e `Price` (conversão vértice ↔ valor interpolável usando `InterestRate`); verificar com teste de ida e volta em cada grandeza
- [ ] 3.3 Compor grandeza + interpolador + DayCounter do eixo e verificar, com testes que implementam diretamente as fórmulas do Manual de Curvas B3 em `BigDecimal`, que as combinações reproduzem 1.4.2, 1.4.3, 1.4.4, 1.4.5 e 1.4.11
- [ ] 3.4 Criar as políticas `Disabled`, `FlatForward` e `FlatValue`, aplicáveis ao início e ao fim; verificar com testes contra as fórmulas 1.4.6, 1.4.7, 1.4.8, 1.4.9 e 1.4.10, e com o erro de `Disabled` informando o prazo
- [ ] 3.5 Expor como `protected` os pontos de extensão do interpolador (valor entre vértices, extrapolação do início e do fim); verificar com teste de subclasse que troca só a extrapolação de fim

## 4. Banco e cadastro

- [ ] 4.1 Alterar `tParmConfgCurva` para PK `(cldtfdConfg, cConfgIdtfd)` com `cConfgIdtfd VARCHAR(50)`, no DDL do schema de curvas de mercado e em migração versionada com script de reversão; verificar aplicando a migração num banco local e confirmando que a reversão restaura o estado anterior
- [ ] 4.2 Criar `tScriptModlCurva`; verificar com a migração aplicada e a entidade JPA mapeando sem erro na subida
- [ ] 4.3 Cadastrar PRE, DCL, DPL, INP e PTX conforme a spec `b3-ready-curve-model`: códigos da fonte em `tCurvaMercd`, origem em `tCurvaPrvdr`, `cMotorCalc`/`cRotnaCalc` com vigência em `tConfgCurva`, e os demais parâmetros em `tParmConfgCurva`; verificar com consulta SQL que lista o cadastro completo das 5 curvas
- [ ] 4.4 Criar `CadastroCurva` e a porta/adaptador que o montam a partir de `tCurvaMercd`, `tCurvaPrvdr`, `tConfgCurva` vigente na data e `tParmConfgCurva`, falhando com o código e o item ausente; verificar com teste de integração para cadastro completo, cadastro sem interpolador e código sem cadastro

## 5. Registro de modelos e Groovy

- [ ] 5.1 Criar `RegistroModelos<T>` único para construção, interpolação e calendário, com registro dos nativos na subida e resolução versão fixada → Groovy ativo → Java → erro; verificar com testes para cada caso da spec `curve-extension-models`
- [ ] 5.2 Criar o carregador Groovy com `SecureASTCustomizer` por lista permitida, `TimedInterrupt` e verificação de tipo implementado, substituindo `GroovyDynamicModelCompiler`; verificar com testes de script que compila, script de tipo errado, script com acesso à rede (rejeitado) e script em laço (interrompido)
- [ ] 5.3 Persistir versões em `tScriptModlCurva` (rascunho, validar, ativar, desativar, hash SHA-256) e recarregar os ativos na subida; verificar com teste de integração que reinicia o contexto e continua usando o script ativo
- [ ] 5.4 Verificar sobrescrita parcial ponta a ponta: script Groovy que estende `LogLinear` e troca só a extrapolação de fim, e script que estende `Brazil` com um feriado extra, cada um validado, ativado e refletido na construção

## 6. Construção e pipeline

- [ ] 6.1 Criar `ModeloConstrucao` e `construcao/prontatsb3`: lê `tBtrsCurvaPrimr` pelo código na fonte da origem cadastrada e pela data, e monta os pontos do dado curva como publicados; verificar com teste de integração que constrói `PRE` de `2026-09-14` com o primeiro ponto em taxa 13,9000000, e `DCL` com o primeiro ponto em -11,7960000
- [ ] 6.2 Ajustar a entidade de `tDadoCurva` ao schema oficial (`dVertcReft`, `vPrecoTx`) e reescrever `ConstruirCurvaService` no fluxo cadastro → construção → gravação, numa transação, dos pontos em `tDadoCurva` (arredondados pelo cadastro), com a proveniência na resposta e no log, sem gravar `tCurvaData`, `tDadoVertcCurva` nem `tMtrizCurva`; verificar com teste de integração que a `PRE` de `2026-09-14` grava exatamente 278 linhas em `tDadoCurva` e nenhuma nas outras três
- [ ] 6.3 Criar o serviço de interpolação sob demanda: lê os pontos de `tDadoCurva`, monta a curva com os modelos do cadastro, aplica o domínio `[1º dia útil, max(último ponto, dataBase + horizonte)]`, arredonda, calcula fatores só para `TAXA` e guarda o objeto de curva em cache por (código, data, versões dos modelos), invalidado quando os pontos da data mudam; verificar com testes para prazo interpolado, prazo coincidente com ponto (valor exato), prazo extrapolado dentro do horizonte, prazo além do horizonte (erro), horizonte menor que o último ponto (interpola até o último ponto), `INP` sem fatores e segunda consulta servida pelo cache
- [ ] 6.4 Implementar pedido repetido sem recálculo (devolve os pontos gravados) e com recálculo (pontos apagados e regravados na mesma transação, mesmos valores se nada mudou, cache da data invalidado); verificar com teste de integração dos dois casos e do determinismo
- [ ] 6.5 Verificar o oráculo B3: para PRE, DCL, DPL, INP e PTX de uma data real do `TaxaSwap.txt`, a interpolação sob demanda em cada prazo de ponto publicado devolve exatamente o valor publicado arredondado pela política da curva, e os dias úteis e corridos recalculados pelo calendário batem com os publicados
- [ ] 6.6 Remover `ComposableCurveBuilder`, `DefaultInsumoNormalizer`, `CurveBuilderRegistry`, `CurveInterpolatorRegistry`, `CurveExtrapolatorRegistry`, os interpoladores e extrapoladores antigos, `MetodoInterpolacao`, `PoliticaExtrapolacao`, `ConvencaoDias` e `CurvaInterpolacaoDomainService`; verificar com `mvn compile` limpo e busca sem referências remanescentes

## 7. API

- [ ] 7.1 Criar `POST /api/v1/curvas/{codigo}/{dataBase}/construcao` e `GET /api/v1/curvas/{codigo}/{dataBase}` (pontos gravados), com 400/404/422 conforme a spec `curve-engine-api`; verificar com testes de controller para cada status
- [ ] 7.2 Criar `GET /api/v1/curvas/{codigo}/{dataBase}/interpolacao` com `du` e `data` repetíveis, usando o serviço da tarefa 6.3, marcando cada valor como ponto, interpolado ou extrapolado, informando os modelos usados e rejeitando `metodo`/`politica` com 400; verificar com teste para `PRE` em 21 e 252 dias úteis, 404 para curva não construída e 422 para prazo fora do domínio
- [ ] 7.3 Criar `PUT /api/v1/curvas/{codigo}/{dataBase}/pontos`: recebe a lista completa, valida (vazia, sem data ou valor, data repetida, data não posterior à data-base, dia não útil no calendário cadastrado), substitui os pontos da data numa transação, invalida o cache, exige autenticação, registra usuário e origem manual no log, e devolve os pontos gravados ordenados com dias recalculados e valor arredondado; verificar com testes de edição de um valor, lista menor removendo pontos, criação numa data sem pontos, 422 com sábado mantendo os pontos anteriores, 401 sem credencial, e interpolação seguinte usando o valor editado
- [ ] 7.4 Criar `GET /api/v1/curvas` (catálogo com filtro `nome`) e as rotas de leitura `GET /api/v1/curvas/por-nome/{dataBase}` e `.../por-nome/{dataBase}/interpolacao`, resolvendo o nome normalizado (sem acentos e sem diferenciar maiúsculas) contra `tCurvaMercd.cTickerIndcd` e o código contra `tCurvaMercd.cTickerIdtfdUnic`; verificar com testes de consulta por nome com acento e caixa diferentes, 404 para nome ou código inexistente, 409 para nome ambíguo após normalização e para código repetido, e busca por trecho do nome no catálogo
- [ ] 7.5 Criar a gestão de scripts `/api/v1/modelos/{tipo}/{nome}` (enviar, validar, ativar, desativar, listar), com autenticação nas operações de escrita e erros sem stack trace; verificar com testes de controller, incluindo 401 sem credencial e 422 ao ativar versão não validada
- [ ] 7.6 Remover `CurvaConstrucaoController`, `CurvaCalculoController`, `ModeloUploadController` e os DTOs antigos, e atualizar os clientes internos (curve-bff) para as rotas novas; verificar com `mvn compile` dos serviços afetados e busca sem referências às rotas antigas

## 8. Conector e processor

- [ ] 8.1 Reescrever `curveB3TypeCatalog.ts` com uma entrada por código exato (incluindo `PRE`, `DCL`, `PTX`, `DPL`, `INP`, `ZUS`, `TIC`) e `normalizeB3CurveType.ts` casando primeiro pelo código; verificar com teste Vitest usando linhas reais do `TaxaSwap.txt`: `DCL`/`DPL` não viram `DOL`, `SLP`/`TFP` não viram `PRE`, `PTX`/`INP` publicados e `076` separado de `TIC`
- [ ] 8.2 Alinhar a entidade B3 do processor com `tBtrsCurvaPrimr` (`cTickerIndcd`, `cDiaCorri`, `cDiaUtil`, `dBaseReft`, `vPrecoTx`), como já feito com `tAnbmaCurvaPrimr`; verificar com `mvn compile` e teste de integração que grava uma mensagem B3 e lê a linha de volta

## 9. Verificação ponta a ponta

- [ ] 9.1 Rodar o fluxo completo com o `TaxaSwap.txt` de uma data real: conector publica, processor grava `tBtrsCurvaPrimr`, engine constrói as 5 curvas pela API nova; verificar pelas respostas da API (construção, consulta dos pontos, edição e interpolação) e pela contagem de linhas em `tDadoCurva` de cada curva
- [ ] 9.2 Rodar `openspec validate engine-modelos-curva --strict` e a suíte de testes do engine, do processor e do conector; verificar que tudo passa
