## 1. Tipos QuantLib e matemática decimal

- [ ] 1.1 Criar `domain/quantlib` com `Compounding`, `Frequency` (valores numéricos do QuantLib), `BusinessDayConvention`, `TimeUnit` e `Period` (parse de `nD`, `nW`, `nM`, `nY`, rejeitando outros formatos); verificar com teste que lista constantes e valores de `Frequency` e testa o parse válido e inválido
- [ ] 1.2 Criar `domain/matematica` com `DecimalMath` (`pow`, `ln`, `exp` em `BigDecimal`, `DECIMAL128`) e `Arredondamento` (`HALF_UP`, `HALF_EVEN`, `DOWN`); verificar com testes de potência fracionária contra valores conhecidos e de `DOWN` contra `HALF_UP` (ex.: 5,43219876 com 7 casas)
- [ ] 1.3 Criar `DayCounter` com `Business252` (`DU/252` pelo calendário), `Actual360`, `Actual365Fixed` e `Thirty360` (30/360 Bond Basis); verificar `DU` e `DC` pela regra `(B, d]`, incluindo `2026-09-14` → `2026-09-15` = 1 e 1, feriado e fim de mês no 30/360
- [ ] 1.4 Criar `InterestRate` (`Simple`, `Compounded`, `Continuous`; rejeitando `SimpleThenCompounded` e `CompoundedThenSimple`) com `FA`, `DF` e taxa implícita, sempre com taxa em percentual; verificar com os cenários da spec (13,9 em 252 DU → 1,139; 5 em 90 DC simples 360 → 1,0125) e ida e volta exata taxa→fator→taxa

## 2. Calendários

- [ ] 2.1 Criar a base `Calendar` (contagem `(B, d]`, `advance` por dias úteis, `adjust` por `BusinessDayConvention`) a partir de `isBusinessDay`, com o ponto de extensão `feriados(ano)`; verificar `advance` e `adjust` para as sete convenções
- [ ] 2.2 Criar `Brazil`/`Settlement` com a lista de feriados da spec (Páscoa por Meeus/Jones/Butcher, 20 de novembro a partir de 2024); verificar que o `DU` de todos os 278 vértices da `PRE` do `TaxaSwap.txt` de `2026-09-14` bate com o `cDiaUtil` publicado
- [ ] 2.3 Criar `UnitedStates`/`FederalReserve` com a lista de feriados da spec (domingo → segunda; sábado não observado); verificar com os feriados de 2026 e 2027 e um caso de 4 de julho no domingo

## 3. Interpolação e extrapolação

- [ ] 3.1 Criar a base `InterpoladorLocal` (localiza o segmento e chama `valorNoSegmento(w, yEsq, yDir)`) e `Linear`, `LogLinear` (rejeita `y <= 0`), `BackwardFlat` e `ForwardFlat`; criar `Cubic` (spline natural em `BigDecimal`); verificar valores entre nós e valor exato nos nós
- [ ] 3.2 Criar as grandezas `Discount`, `CompoundFactor`, `ZeroYield` e `Price`, com conversão ponto ↔ `y` pela cotação e pela fração de ano do próprio prazo; verificar ida e volta em cada grandeza, inclusive com eixo e cotação diferentes (DCL)
- [ ] 3.3 Verificar, com testes que implementam diretamente as fórmulas do Manual de Curvas B3 em `BigDecimal`, que as combinações da tabela da spec reproduzem 1.4.2, 1.4.3, 1.4.4, 1.4.5 e 1.4.11
- [ ] 3.4 Criar as políticas `Disabled`, `FlatForward` (via `valorNoSegmento` com `w` fora de `[0, 1]`) e `FlatValue` (repete o valor, não a grandeza), por lado; verificar contra 1.4.6, 1.4.7, 1.4.8, 1.4.9 e 1.4.10, e o erro `PRAZO_FORA_DO_DOMINIO` de `Disabled`
- [ ] 3.5 Criar `CurvaInterpolada`: domínio `[B + 1 DU, max(último ponto, B + HORIZONTE)]`, classificação `PONTO`/`INTERPOLADO`/`EXTRAPOLADO_INICIO`/`EXTRAPOLADO_FIM`, curva de um ponto só, arredondamento só do valor e fatores com 16 casas a partir do valor arredondado; verificar os cenários de domínio da spec `curve-build-pipeline`

## 4. Cadastro

- [ ] 4.1 Ler os parâmetros do JSON de `tConfgCurva.cModDado` (tipos da tabela da spec, chave desconhecida e JSON inválido rejeitados), sem ler `tParmConfgCurva`; verificar com JSON válido, inválido, com chave desconhecida e com tipo errado
- [ ] 4.2 Criar `CadastroCurva` e o adaptador que o monta de `tCurvaMercd`, `tCurvaPrvdr` (menor `cPriorCsumo`), `tConfgCurva` vigente e o JSON de `cModDado`, validando todas as regras de `CADASTRO_INVALIDO` da spec; verificar com um teste por regra (item ausente, valor inválido, chave desconhecida, zero e duas configurações vigentes, grandeza × unidade, `FlatForward` com `Cubic`, fonte errada para o modelo)
- [ ] 4.3 Criar as fixtures de teste com o cadastro das 7 curvas exatamente como nas specs `b3-ready-curve-model`, `ntnb-anbima-curve-model` e `sofr-bloomberg-curve-model`; verificar que as 7 carregam sem `CADASTRO_INVALIDO`

## 5. Registro de modelos e Groovy

- [ ] 5.1 Criar `RegistroModelos<T>` para construção, interpolação e calendário, com a resolução versão fixada → `ATIVA` no Blob → Java nativo → `CADASTRO_INVALIDO`; verificar cada cenário de resolução da spec `curve-extension-models`
- [ ] 5.2 Criar o adaptador de Blob (`groovy-models/{tipo}/{nome}/v{n}.groovy` imutável com `If-None-Match: *`; `estado.json` com `If-Match`), com cache de estado por instância (`engine.groovy.cache-estado-segundos`, padrão 30), cache de ausência, conferência de hash na carga e a regra de Blob fora da spec `curve-engine-resilience`; verificar com Azurite: duas instâncias do registro no mesmo teste, ativação numa e uso da versão nova na outra após o vencimento do cache, duas ativações concorrentes (uma recebe `ESTADO_SCRIPT_CONCORRENTE`), conteúdo alterado (hash divergente) e Blob parado
- [ ] 5.3 Criar o carregador Groovy com `SecureASTCustomizer` por lista permitida, `TimedInterrupt` (`engine.groovy.timeout-segundos`, padrão 5) e checagem do contrato do tipo, substituindo `GroovyDynamicModelCompiler`; verificar com scripts válido, de tipo errado, com acesso à rede (reprovado) e em laço (interrompido com `MODELO_FALHOU`)
- [ ] 5.4 Implementar os estados `RASCUNHO` → `VALIDADA`/`REPROVADA` → `ATIVA` ↔ `INATIVA` e a validação por tipo (fixture de interpolação, ano corrente de calendário, simulação para construção); verificar cada transição válida e inválida
- [ ] 5.5 Verificar a sobrescrita parcial ponta a ponta: script que estende `LogLinear` e troca só `valorNoSegmento`, e script que estende `Brazil` com um feriado extra, cada um validado, ativado e refletido na interpolação e na contagem de dias

## 6. Memória de cálculo

- [ ] 6.1 Criar `MemoriaCalculo` (resumo, insumos, pontos com colunas extras declaradas pelo modelo, fluxos, prazos, eventos) e fazer pipeline, modelos, interpolação e extrapolação registrarem nela nos mesmos métodos que calculam; verificar que nenhum valor da memória é recalculado fora do caminho de cálculo (teste que compara cada valor da memória com o valor devolvido)
- [ ] 6.2 Implementar `hashPontos` (SHA-256 das linhas `AAAA-MM-DD;valor`, valor arredondado sem expoente, separadas por `\n`); verificar com um vetor de teste fixo

## 7. Modelos de construção

- [ ] 7.1 Criar `ModeloConstrucao`, `ContextoConstrucao` e `LeitorInsumos` (único acesso a `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr` e `mkt.SofrCurveRaw`), e `construcao/pontosprontos`; verificar com teste de datas repetidas rejeitadas
- [ ] 7.2 Criar `PRONTA_TS_B3` com as regras da spec `b3-ready-curve-model` (data = `B + cDiaCorri`, `DU` = `cDiaUtil`, sem descarte) e as colunas de memória; verificar com o `TaxaSwap.txt` de `2026-09-14` carregado por fixture: primeiro ponto da `PRE` 13,9000000, da `DCL` -117,9600000, último da `PRE` em `2060-08-16`, e falha `INSUMO_INVALIDO` com um feriado removido do calendário
- [ ] 7.3 Criar `NTNB_BOOTSTRAP_ANBIMA`: leitura e regras da spec (`SEM_TAXA`, dia 15, duplicidade), fluxo com cupom fixo, cotação, bootstrap por bisseção com as origens de DF, colunas de memória e aba `Fluxos`; verificar com fixture sintética de 4+ títulos com cupons em datas resolvidas, entre títulos e antes do primeiro, `z_1 = y_1`, soma dos valores presentes = cotação a menos do resíduo, falha sem troca de sinal e falha de prazo inconsistente
- [ ] 7.4 Criar `SOFR_ZERO_BLOOMBERG`: leitura de `mkt.SofrCurveRaw`, conversão de tenor (`D` em dias úteis; `W`, `M`, `Y` com ajuste), duplicidade (idêntico descarta, divergente falha), datas repetidas entre tenores e colunas de memória; verificar com fixture de 21 tenores mais um `1D` duplicado, `15M` → `2027-12-14`, e um tenor caindo em feriado americano

## 8. Pipeline

- [ ] 8.1 Ajustar a entidade de `tDadoCurva` às quatro colunas do schema e reescrever `ConstruirCurvaService`: trava `PESSIMISTIC_WRITE` na linha de `tCurvaMercd` (30 segundos, `CONSTRUCAO_EM_ANDAMENTO`), cadastro, modelo, arredondamento, gravação, situações `CONSTRUIDA`/`RECONSTRUIDA`/`EXISTENTE`, proveniência e `hashPontos`; verificar que a `PRE` grava 278 linhas e nenhuma em `tCurvaData`, `tDadoVertcCurva` e `tMtrizCurva`, que duas construções simultâneas da mesma curva não misturam pontos, e o determinismo pelo `hashPontos`
- [ ] 8.2 Implementar a simulação no mesmo serviço, com a gravação desligada, sem trava, com `status` `OK`/`ERRO`, prazos `FORA_DO_DOMINIO` e comparação com os pontos gravados (`SO_SIMULADO`/`SO_GRAVADO`); verificar que simular e construir dão o mesmo `hashPontos`, que a simulação não grava nada, e a diferença de um ponto editado
- [ ] 8.3 Reescrever `CalcularCurvaService` (consulta e interpolação lendo `tDadoCurva` a cada chamada, sem cache); verificar `CURVA_NAO_CONSTRUIDA` e o oráculo B3: para as 5 curvas, cada prazo de vértice devolve exatamente o `vPrecoTx` publicado
- [ ] 8.4 Emitir os eventos de log da spec (`CONSTRUCAO_CONCLUIDA`, `CONSTRUCAO_FALHOU`, `INSUMO_DESCARTADO`, `PONTOS_EDITADOS`, `SIMULACAO_EXECUTADA`) em JSON com `correlationId`; verificar os campos de cada evento com um appender de teste
- [ ] 8.5 Remover `ComposableCurveBuilder`, `DefaultInsumoNormalizer`, `CurveBuilderRegistry`, `CurveInterpolatorRegistry`, `CurveExtrapolatorRegistry`, os interpoladores e extrapoladores antigos, `MetodoInterpolacao`, `PoliticaExtrapolacao`, `ConvencaoDias` e `CurvaInterpolacaoDomainService`; verificar com `mvn compile` limpo e busca sem referências

## 9. Carga concluída

- [ ] 9.1 Criar o registro de carga no Blob (`cargas/{fonte}/{produto}/{dataBase}.json`, escrita por ETag, `historico` na republicação); com o Blob fora, construir pela carga do corpo, registrar `CARGA_NAO_REGISTRADA` e regravar em segundo plano; verificar com Azurite: carga nova, repetida, republicada, gravações concorrentes e Blob parado
- [ ] 9.2 Exigir carga registrada em toda construção (`CARGA_NAO_CONCLUIDA`) e conferir a quantidade lida contra `linhasPorCodigo` antes do modelo (`INSUMO_INCOMPLETO`), deixando a simulação rodar sem carga e mostrar isso no `Resumo`, e seguindo sem conferência (`CARGA_NAO_VERIFICADA`) quando o Blob não puder ser lido; verificar construção antes do aviso, código fora da carga, leitura de 150 de 278 linhas, simulação sem carga e construção manual com o Blob fora
- [ ] 9.3 Criar `POST /api/v1/cargas` com credencial de serviço: validação do corpo, construção síncrona e independente só das curvas da origem sem pontos na data, nunca recalculando, aviso `PONTOS_DE_CARGA_ANTERIOR` na republicação, `idCarga` que gerou os pontos gravado no registro (também nas construções pela API), eventos `CARGA_RECEBIDA`, `CARGA_REPUBLICADA` e `CARGA_PROCESSADA`; verificar os cenários da spec `curve-load-trigger` (retry, republicação sem recálculo, recálculo forçado depois da republicação, uma curva falhando, instância diferente construindo depois)

## 10. Auditoria, leitura consistente e segurança

- [ ] 10.1 Gravar o registro de auditoria no Blob antes do commit (construção, reconstrução, edição; com os pontos substituídos), seguir com a gravação e registrar `AUDITORIA_PENDENTE` completo no log se o Blob falhar, regravando em segundo plano, marcar `.desfeita.json` se o commit falhar depois, exigir motivo em recálculo e edição, e atualizar `tCurvaMercd.dBaseReft` (sem retroceder) e `cUsuarCalc` na construção; verificar construção, reconstrução de data antiga, edição, recálculo e edição sem motivo (400), Blob fora (pontos gravados, auditoria no log e depois no Blob) e commit forçado a falhar (registro marcado como `DESFEITA`)
- [ ] 10.2 Criar `GET .../historico` e `GET .../historico/{idAuditoria}` (JSON e `xlsx`); verificar o cenário "Quem alterou a curva" e a recuperação do valor anterior de um ponto editado
- [ ] 10.3 Garantir leituras só em `READ COMMITTED` (teste de arquitetura que proíbe `NOLOCK` e `READ_UNCOMMITTED` no código) e testar a consulta durante uma reconstrução: em SQL Server de teste (Testcontainers), com a transação de reconstrução pausada entre o apagar e o inserir, a consulta espera e devolve os pontos novos inteiros
- [ ] 10.4 Configurar a validação de JWT do Entra ID (emissor, audiência, assinatura, validade) e a autorização por papel conforme a tabela de rotas, com `NAO_AUTENTICADO` e `SEM_PERMISSAO`, usuário da auditoria a partir de `preferred_username` ou `appid`, e autenticação impossível de desligar no perfil de produção; verificar um teste por rota com token sem papel (403), sem token (401) e com o papel certo, e o webhook recusado para `Curvas.Operador`
- [ ] 10.5 Gravar no `estado.json` quem ativou cada versão (`aprovador`, podendo ser o autor); verificar ativação pelo próprio autor e por outro usuário
- [ ] 10.6 Acessar o Blob por Managed Identity (`DefaultAzureCredential`), com connection string só no perfil local (Azurite); verificar que o perfil de produção não aceita connection string

## 11. Resiliência

- [ ] 11.1 Configurar os tempos limite da tabela da spec `curve-engine-resilience` (pool e comando do banco, trava, Blob, Groovy, requisição e webhook) com os erros correspondentes; verificar com testes que simulam lentidão em cada dependência e confirmam o erro, a transação desfeita e o evento `TEMPO_ESGOTADO`
- [ ] 11.2 Implementar a repetição só de operações idempotentes (3 tentativas, espera exponencial com variação) e o circuit breaker do Blob (5 falhas, 60 segundos aberto); verificar que leitura do Blob com uma falha transitória funciona, que gravação no banco e no `estado.json` não é repetida, e que com o circuito aberto nenhuma chamada ao Blob é feita
- [ ] 11.3 Manter o último estado de script lido com o Blob fora, sem prazo, com aviso limitado a um por minuto; sem estado ou sem a versão em memória, usar o nativo com `ESTADO_SCRIPT_DESCONHECIDO`; informar `estadoScript` na proveniência; ligar a prontidão ao banco e à espera de subida pelo Blob (até `engine.groovy.espera-subida-segundos`, padrão 300); verificar Blob fora por horas durante construções, Blob voltando durante a espera (instância pronta com as versões ativas), Blob fora além da espera (instância pronta com nativos), e o `health` mostrando o Blob `DOWN`
- [ ] 11.4 Emitir os logs `REQUISICAO_CONCLUIDA`, `DEPENDENCIA_CHAMADA`, `DEPENDENCIA_LENTA`, `DEPENDENCIA_FALHOU` e `TEMPO_ESGOTADO`, sem token, connection string, script, corpo inteiro ou listas de pontos; verificar os campos com um appender de teste e uma busca por dados proibidos no log gerado pela suíte
- [ ] 11.5 Publicar as métricas da spec pelo Micrometer; verificar os contadores e histogramas depois de construções com sucesso e com falha

## 12. API

- [ ] 12.1 Criar o tratamento de erros padronizado (tabela de `codigoErro` e HTTP da spec `curve-engine-api`) e o filtro de `X-Correlation-Id`; verificar um teste por código de erro e o cabeçalho em resposta de sucesso, de erro e de `xlsx`
- [ ] 12.2 Criar as rotas de curva (catálogo, construção, consulta, edição de pontos, interpolação, simulação; por código e por nome), com validação dos parâmetros comuns (400 para parâmetro desconhecido, limite de 5.000 prazos, `data` não útil), `motivo` obrigatório no recálculo e o papel exigido em cada rota; verificar com testes de controller para cada rota e os cenários da spec
- [ ] 12.3 Criar `PUT .../pontos` com `motivo` obrigatório, todas as validações de `PONTOS_INVALIDOS`, a mesma trava da construção, a auditoria `EDICAO` e o evento `PONTOS_EDITADOS`; verificar edição, lista menor, data sem pontos, sábado (nada alterado), valor não conversível, sem motivo (400), 401, e interpolação seguinte usando o valor editado
- [ ] 12.4 Criar as rotas `/api/v1/modelos/{tipo}/{nome}` sobre o Blob; verificar envio, validação, ativação, desativação, listagem, 401 e `ESTADO_SCRIPT_CONCORRENTE`
- [ ] 12.5 Remover `CurvaConstrucaoController`, `CurvaCalculoController`, `ModeloUploadController` e os DTOs antigos; verificar com `mvn compile` e busca sem referências às rotas antigas

## 13. Planilha

- [ ] 13.1 Criar `PlanilhaMemoriaCalculo` com Apache POI (`poi-ooxml`): as seis abas na ordem, cabeçalho congelado, datas `aaaa-mm-dd`, números como células numéricas, colunas extras do modelo, nota de precisão no `Resumo`; verificar lendo o arquivo gerado no teste e conferindo abas, cabeçalhos e tipos de célula
- [ ] 13.2 Ligar `formato=xlsx` às rotas de consulta, interpolação e simulação, com `Content-Type`, `Content-Disposition` e nome de arquivo da spec; verificar os dois cenários de planilha da spec `curve-calculation-memory` (consulta gravada da `PRE` com `du=21`, e simulação da `NTN-B` com prazo inconsistente)

## 14. Calendário por planilha e pacote de depuração

- [ ] 14.1 Criar `CalendarioPorLista` (fins de semana, lista, cobertura com `MODELO_FALHOU` fora dela, mercado fixo); verificar data dentro, fora da cobertura e mercado errado
- [ ] 14.2 Criar `POST /api/v1/calendarios/{nome}/importacao`: validação da planilha (aba, colunas, datas, repetidas, cobertura), geração determinística do script, gravação da versão em `RASCUNHO` com a planilha original; verificar cada rejeição, mesma planilha gerando o mesmo hash, e o cenário do feriado decretado até a ativação
- [ ] 14.3 Validar versões importadas percorrendo toda a cobertura; verificar aprovação de uma planilha correta e reprovação de um script adulterado
- [ ] 14.4 Criar `GET /api/v1/calendarios/{nome}` (json e xlsx, resolução como na construção ou por `versao`, limite de 150 anos); verificar a ida e volta do `Brazil` nativo de 2001 a 2100
- [ ] 14.5 Gravar no build a versão do artefato e o commit e incluí-los na proveniência, na auditoria e no `Resumo`; verificar numa resposta de construção
- [ ] 14.6 Guardar em memória o código-fonte de cada versão compilada e criar `formato=zip` (planilha, JSON, `.groovy` usados, planilha de calendário importado, manifesto com SHA-256); verificar o cenário da spec `curve-calculation-memory` (hash do `.groovy` igual ao da proveniência) e um pacote gerado com o Blob fora

## 15. Datas, horários e testes em massa

- [ ] 15.1 Criar um relógio único do engine com `America/Sao_Paulo` e usá-lo em todo "hoje" e instante (respostas, planilhas, auditoria, carga, `estado.json`, logs, nomes de arquivo); verificar com a suíte rodando com a JVM em UTC e um teste do cenário das 22h30, e com um teste de arquitetura que proíbe `LocalDate.now()`, `Instant.now()` e `ZoneId.systemDefault()` fora do relógio
- [ ] 15.2 Montar a massa de regressão: `TaxaSwap.txt` de todos os pregões de pelo menos 12 meses consecutivos (baixados da B3), com Carnaval, Sexta-feira Santa, Corpus Christi, virada de ano e 20 de novembro, guardados compactados nos recursos de teste; verificar o oráculo das 5 curvas em todos os arquivos
- [ ] 15.3 Criar testes de propriedade (jqwik ou equivalente) para: ponto preservado em qualquer curva gerada; determinismo; ida e volta taxa→fator→taxa em cada cotação; monotonicidade de `DU` e `DC`; `advance` e contagem de dias úteis coerentes em qualquer par de datas; verificar com pelo menos 1.000 casos por propriedade
- [ ] 15.4 Criar o teste de precisão do `DecimalMath` (`pow`, `ln`, `exp`) contra valores de referência de alta precisão (ex.: 50 dígitos) em entradas típicas de curva; verificar erro relativo menor que `10^−30`
- [ ] 15.5 Criar testes de bootstrap da NTN-B com títulos sintéticos cuja taxa zero é conhecida por construção (gerar as taxas indicativas a partir de uma curva zero dada e verificar que o bootstrap recupera essa curva dentro da tolerância)

## 16. Verificação final

- [ ] 16.1 Com as tabelas brutas e o cadastro carregados por fixture, construir as 7 curvas pela API, consultar, interpolar, editar um ponto, simular e baixar as planilhas; verificar pelas respostas, pela contagem de linhas em `tDadoCurva` e pelos `hashPontos`
- [ ] 16.2 Rodar `openspec validate engine-modelos-curva --strict` e a suíte de testes do engine; verificar que tudo passa
