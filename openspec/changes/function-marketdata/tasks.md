## 1. Esqueleto do serviço

- [x] 1.1 Criar `services/function-marketdata/` com Node 20, TypeScript estrito, `package.json` e scripts de build/test
- [x] 1.2 Configurar Vitest, lint e formatação alinhados ao restante do repositório
- [x] 1.3 Gerar os tipos TypeScript do envelope a partir de `contracts/events/envelope.schema.json`
- [x] 1.4 Escrever o `Containerfile` para execução sob Podman (build testado de verdade; `podman run` só funcionará de ponta a ponta quando o adaptador `main` — tarefa 5.1 — existir)

## 2. Núcleo de aquisição

- [x] 2.1 Definir a interface `Feeder` com a operação de aquisição e o resultado de três estados
- [x] 2.2 Implementar o resultado tipado `PUBLISHED` / `NO_DATA` / `FAILED` com motivo e diagnóstico
- [x] 2.3 Implementar o cálculo determinístico de `loteId` (fonte, dataset, data, hash do conteúdo) e de `eventId` (lote + sequência)
- [x] 2.3.1 Implementar a quebra do conteúdo em blocos, com corte estrutural e tamanho de bloco configurável
- [x] 2.3.2 Implementar a publicação na faixa recebida no disparo, falhando quando a faixa não é informada
- [x] 2.4 Implementar o cliente HTTP com timeout, backoff exponencial com jitter e retentativa só para falha de transporte
- [x] 2.5 Implementar a verificação de integridade: conteúdo não vazio, tamanho declarado e abertura de arquivo compactado
- [x] 2.6 Implementar o calendário de pregão B3 com os feriados reais e a decisão de dia de pregão
- [x] 2.7 Implementar a distinção entre `NOT_YET_PUBLISHED` e fonte indisponível (classificação genérica por status HTTP — provisória até existir fixture real da B3, tarefa 6.1)

## 3. Datasets B3

- [x] 3.1 Implementar a aquisição do arquivo de Preços de Referência (PR) (`FeederB3ArquivoPesquisaPregao` com `prefixoArquivo: 'PR'` — endpoint real confirmado ao vivo nesta sessão: `https://www.b3.com.br/pesquisapregao/download?filelist=PR{AAMMDD}.zip`. Descoberta real que moldou a implementação: a resposta HTTP é um ZIP duplamente aninhado — ZIP externo com uma entrada nomeada como o arquivo pedido, cujo conteúdo é outro ZIP com as revisões intraday do pregão; usa-se a de data de modificação mais recente. Verificado com download real completo do pregão de 2026-08-21: 511 blocos publicados de verdade no Kafka local, e lidos de volta do tópico real para confirmar)
- [x] 3.2 Implementar a aquisição do BVBG.086 (preços e ajustes) (mesmo Feeder de 3.1 — confirmado nesta sessão que o conteúdo real de "PR" É o BVBG.086: o XML interno é `BVBG.086.01_*.xml`; registrado sob os dois nomes de dataset, `PR_DI1` e `BVBG.086`, mesmo padrão do parser `Bvbg086PricRptParser` do curve-processor)
- [x] 3.3 Implementar a aquisição do BVBG.028 (cadastro de instrumentos) (`FeederB3ArquivoPesquisaPregao` com `prefixoArquivo: 'IN'` — mesma forma de aquisição, muda só o prefixo do nome de arquivo. Verificado com download real completo do arquivo real (~800MB, o maior dos dois datasets) do pregão de 2026-08-21 através do pipeline completo — é este dataset que originalmente estourava `ERR_STRING_TOO_LONG` do V8 antes da tarefa 3.6 ser corrigida para operar sobre `Buffer`)
- [x] 3.4 Implementar a aquisição da curva pronta pelo endpoint de taxas de referência, montando o pedido conforme o contrato da B3 (**desbloqueado nesta sessão** — o endpoint real de curva pronta NÃO é o `pesquisapregao/download` (que só serve PR/IN/TS); é
  `https://sistemaswebb3-derivativos.b3.com.br/referenceRatesProxy/Search/GetDownloadFile/{base64}`,
  onde `{base64}` codifica `{"language":"pt-br","date":"YYYY-MM-DD","id":"PRE"}` — encontrado numa
  documentação anterior do usuário (`D:\Workspace\curve-platform`, projeto irmão, mesmo endpoint já
  validado lá com `B3ReferenceRateFetcher.java`), e reconfirmado por mim ao vivo com `curl` puro
  antes de implementar (274 vértices reais para PRE/2026-08-21). Descoberta real que molda a
  implementação: o corpo HTTP é, ele mesmo, uma string Base64 — decodificar uma vez dá o CSV real
  (ISO-8859-1, separador `;`, decimal com vírgula, 1 linha de cabeçalho `Descrição da
  Taxa;Dias Úteis;Dias Corridos;Preço/Taxa`). Precisa de headers de navegador (User-Agent/Accept/
  Accept-Language) contra o WAF do endpoint. Sinal real de "sem pregão" é HTTP 200 com corpo vazio
  (confirmado ao vivo para 2026-08-22, sábado) — mesmo padrão de "sinal real, não status HTTP" já
  usado pelo feeder B3 de arquivo. `FeederB3CurvaReferencia` (`src/feeders/b3-curva-referencia.ts`),
  parametrizado por `codigoCurva` (só `'PRE'` implementado/verificado — DIC/DOL/DOC/DCL/INP
  existem no mesmo endpoint mas não foram confirmados por mim, ficam para extensão futura, ver
  `fixtures/README.md`), registrado sob o dataset `B3_CURVA_PRE`. Delegado ao agy com todos os
  fatos ao vivo já verificados no prompt; auditado — escopo correto (4 arquivos autorizados),
  matemática/lógica de classificação bate exatamente com os fatos reais. **2 bugs reais
  encontrados e corrigidos por mim**: `Buffer.from(str, 'iso-8859-1')` não compila (TypeScript não
  aceita esse literal como `BufferEncoding` — corrigido para `'latin1'`, mesmo encoding na
  prática) e `noUncheckedIndexedAccess` reprovando acesso a `mock.calls[0][0]` sem checagem —
  corrigido com `!`. Verificado de ponta a ponta: `npm test` (169/169), `npm run test:contract`
  real contra o endpoint ao vivo (PUBLISHED real + NO_DATA real de fim de semana), e publicação
  real confirmada no Kafka local via `kafka-console-consumer` — 274 vértices reais no tópico)
- [x] 3.5 Declarar `payloadKind` (`INDIVIDUAL_QUOTES` / `READY_CURVE`) em cada evento, determinado pelo endpoint consultado (`FeederB3ArquivoPesquisaPregao` sempre declara `INDIVIDUAL_QUOTES`; `FeederB3CurvaReferencia` sempre declara `READY_CURVE` — confirmado no evento real publicado no Kafka local)
- [x] 3.6 Preservar encoding e formato originais, declarando o encoding no metadado do evento (`montarRecords`/`registros-payload.ts` usa `TextDecoder` com o encoding declarado — real e verificado: `utf-8` nos arquivos PR/IN da B3, `iso-8859-1` na curva pronta; `xml-estrutural.ts` foi migrado de `string` para `Buffer` porque o arquivo real do BVBG.028 chega a ~800MB, acima do limite de comprimento de string do V8 — `ERR_STRING_TOO_LONG` reproduzido de verdade contra o arquivo real antes da correção)
- [x] 3.7 Registrar os quatro datasets no roteador do feeder e falhar cedo para dataset não suportado (`registro-feeders-b3.ts` registra `PR_DI1`/`BVBG.086`/`BVBG.028`/`B3_CURVA_PRE`, os quatro datasets agora reais)

## 4. Publicação em Kafka

- [x] 4.1 Implementar o produtor Kafka com o envelope comum, `loteId`, `sequencia`, `totalBlocos` e a chave de partição do catálogo (cliente Kafka real ainda injetado como função — decisão de infraestrutura de outra tarefa)
- [x] 4.2 Validar o evento contra o schema no próprio produtor, antes do envio (ajv 8.20.0 + ajv-formats 3.0.1, `Ajv2020` para o draft 2020-12 do contrato; sincroniza contracts/events via `npm run sync-contracts`, também copiado no Containerfile — build de container agora exige contexto na raiz do monorepo, verificado com `podman build` real)
- [x] 4.3 Propagar o `correlationId` recebido para evento, log e reporte
- [x] 4.4 Implementar log estruturado em JSON com `correlationId`, `dataset` e `referenceDate`

## 5. Adaptadores de execução

- [x] 5.1 Implementar o adaptador de container (`main`) que lê parâmetros de ambiente e executa o núcleo (`src/main.ts` — lê `ACQUISITION_DATASET`/`ACQUISITION_REFERENCE_DATE`/`ACQUISITION_FAIXA`/`ACQUISITION_CORRELATION_ID`/`KAFKA_BOOTSTRAP_SERVERS`/`HEALTH_PORT`, monta o `RegistroFeeders`, roda uma aquisição e sai com código 0 (`PUBLISHED`/`NO_DATA`) ou 1 (`FAILED`). Verificado de ponta a ponta contra o Kafka real local: rodei `main.ts` de verdade, publicou 511 blocos reais em `marketdata.rotina.v1`, e confirmei lendo de volta do tópico com `kafka-console-consumer` real — 511 mensagens com o `loteId` exato)
- [x] 5.2 Implementar o handler de Azure Function sobre o mesmo núcleo, sem regra de aquisição própria (`src/azure-function-handler.ts` — reaproveita exatamente a mesma montagem de registro/feeders de `main.ts`, só troca a leitura de parâmetros (do corpo da requisição HTTP, não de variáveis de ambiente) e o formato de retorno (resposta HTTP, não código de saída de processo); sem lógica de aquisição própria, conforme a tarefa pede)
- [ ] 5.3 Implementar o reporte de início e de resultado ao `curve-orchestrator` (parcialmente resolvida por 5.5 abaixo — o reporte de RESULTADO agora vem embutido na própria resposta HTTP síncrona de `POST /acquire`, não precisa de uma chamada de retorno separada; falta ainda o reporte de INÍCIO, que exigiria resposta assíncrona/streaming para acquisições longas, e o lado curve-orchestrator que consome o resultado — ver tarefa 4.6 do backlog curve-orchestrator, em andamento)
- [x] 5.4 Expor endpoint de saúde para o compose Podman
- [x] 5.5 Implementar o adaptador HTTP local sempre de pé (`src/main-http.ts` — mesmo núcleo/contrato de `azure-function-handler.ts`, POST /acquire com o mesmo corpo JSON e mesmo formato de resposta, servido por `http.Server` puro do Node; dá ao curve-orchestrator um alvo estável para acionar o feeder localmente, já que `main.ts` roda uma aquisição e termina. Adicionado por não ser coberto por nenhuma tarefa anterior — decisão tomada junto com o usuário ao planejar a tarefa 4.6 do curve-orchestrator: emula o HTTP trigger real do Azure Function, só que servido localmente sem o Functions Core Tools; trocar a URL base por uma Function implantada não muda o contrato. Wired em `deploy/podman/compose.yaml` como serviço `function-marketdata-http`, always-up, sem profile. Verificado de ponta a ponta: build real da imagem, container rodando contra a rede `curvas-net` real, `GET /health` respondeu `{"status":"UP"}`, `POST /acquire` com `{dataset: BVBG.086, referenceDate: 2035-06-15, faixa: ROTINA}` executou uma aquisição B3 real e devolveu `{"correlationId":...,"resultado":{"kind":"NO_DATA",...}}` — mesmo contrato do handler de Azure Function)

## 6. Testes

- [x] 6.1 Gravar as fixtures reais dos quatro datasets, com data de captura, URL e encoding (`fixtures/BVBG.086.01_fixture.xml`, `fixtures/BVBG.028.02_fixture.xml`, e agora `fixtures/b3-curva-pre-20260821_fixture.csv` — arquivo real completo (274 vértices, 6.790 bytes decodificados, pequeno o bastante para versionar por inteiro, mesmo padrão da fixture ANBIMA), byte a byte idêntico ao conteúdo real publicado no Kafka local nesta sessão. Ver `fixtures/README.md` para URL, data de captura e encoding de cada um)
- [x] 6.2 Testar os três resultados: publicação, ausência de dado e falha de fonte (`b3-arquivo-pesquisa-pregao.test.ts` — PUBLISHED com ZIP duplamente aninhado real, NO_DATA com ZIP externo vazio, FAILED para transporte/5xx/ZIP corrompido/ZIP interno corrompido, todos com fixtures sintéticas fiéis à forma real confirmada nesta sessão)
- [x] 6.3 Testar o `eventId` determinístico — mesmo conteúdo gera o mesmo id, conteúdo diferente gera id diferente
- [x] 6.4 Testar retentativa só para falha de transporte e ausência de retentativa para erro de cliente
- [x] 6.5 Testar feriado B3 em dia de semana resultando em `NO_DATA` sem chamada à fonte (`FeederB3ArquivoPesquisaPregao` — teste confirma `fetchImpl` nunca chamado)
- [x] 6.6 Testar download truncado e arquivo compactado corrompido
- [x] 6.7 Testar que encoding e separador decimal não são convertidos (`ExrcPric`/ponto vs. `Desc`/vírgula, mesmo valor real 20,23 coexistindo no mesmo registro do BVBG.028.02 real, mais comparação binária byte a byte do trecho extraído contra o arquivo original)
- [x] 6.7.1 Testar que `payloadKind` reflete o endpoint consultado, para os dois tipos de insumo (`b3-arquivo-pesquisa-pregao.test.ts` cobre `INDIVIDUAL_QUOTES` para PR/IN; `b3-curva-referencia.test.ts` cobre `READY_CURVE` para a curva pronta — teste "deve retornar PUBLISHED e publicar bloco corretamente" verifica `envelope.payloadKind === 'READY_CURVE'` no evento real capturado)
- [x] 6.7.2 Testar a quebra em blocos: mesmo `loteId`, sequências distintas, `totalBlocos` correto
- [x] 6.7.3 Testar arquivo que cabe em um bloco único
- [x] 6.7.4 Testar falha no meio do arquivo deixando lote incompleto identificável
- [x] 6.7.5 Testar que nenhum valor numérico é convertido pelo feeder (mesmo teste de 6.7 — identificador `FinInstrmId` real sem reformatação, mais o caso de 20.23/20,23)
- [x] 6.7.6 Testar a publicação em cada uma das três faixas e a falha quando a faixa não é informada
- [x] 6.7.7 Medir o tamanho real dos arquivos B3 e calibrar o tamanho do bloco (medido nos 2 arquivos reais do pregão 2026-08-21 do zip fornecido pelo usuário: BVBG.086 175.506.347 bytes/76.015 elementos, BVBG.028 800.282.039 bytes/223.700 elementos; `TAMANHO_BLOCO_PADRAO = 150` em `src/tamanho-bloco.ts`, com a derivação documentada)
- [x] 6.8 Escrever o teste de contrato opcional contra a B3 real, fora do build padrão (`src/feeders/b3-pesquisa-pregao.contract.ts`, config dedicada `vitest.contract.config.ts`, `npm run test:contract` — nunca coletado por `npm test`. Rodei de verdade contra a B3 ao vivo: baixa e processa o PR260821.zip real (511 blocos) e o IN260821.zip real (~800MB, mais de 1000 blocos), e confirma NO_DATA para uma data futura sem arquivo publicado — foi este teste que pegou o bug real do ZIP duplamente aninhado, corrigido nesta sessão)
- [x] 6.9 Escrever o teste de fronteira que falha se o serviço declarar dependência de banco de dados
- [x] 6.10 Verificar que a suíte padrão passa em máquina sem acesso à internet (auditado: único `fetchImpl` real do projeto é sempre injetado nos testes — `http-client.test.ts` nunca chama o `fetch` global; a única chamada real a `fetch` na suíte é em `servidor-saude.test.ts`, contra `localhost`, que não depende de rota de internet)

## 7. Integração no ambiente local

- [x] 7.1 Adicionar o feeder ao `compose.yaml` do Podman com healthcheck e variáveis de ambiente
- [x] 7.2 Executar o feeder contra o Kafka local e confirmar o evento no tópico de dado bruto (rodei `main.ts` de verdade contra o Kafka local, publicou 511 blocos reais do PR260821.zip em `marketdata.rotina.v1`; confirmei lendo de volta com `kafka-console-consumer` real — exatamente 511 mensagens com o `loteId` esperado)
- [x] 7.3 Documentar em `services/function-marketdata/README.md` os datasets, os parâmetros e como rodar isolado
- [x] 7.4 Registrar em `docs/` o ponto de extensão para novas fontes (ANBIMA já implementada; Bloomberg e LSEG continuam como pontos de extensão hipotéticos), com o que um feeder novo precisa implementar (`docs/extensao-feeders.md`)

## 8. Fonte ANBIMA (generalização do feeder além da B3)

Pedido explícito do usuário nesta sessão: generalizar o serviço (antes
`feeder-b3-marketdata`, renomeado para `function-marketdata` — pacote,
diretório, mudança OpenSpec, todas as referências cruzadas) para não ser
exclusivo da B3, e implementar a ANBIMA como segunda fonte real, não só
como ponto de extensão documentado.

- [x] 8.1 Investigar e confirmar o formato real do arquivo de mercado secundário da ANBIMA antes de escrever qualquer parser (baixei e inspecionei `ms260821.txt` ao vivo: texto `@`-delimitado, ISO-8859-1, CRLF, 3 linhas de cabeçalho — título institucional, linha em branco, nomes de coluna —, dados reais de LTN/LFT/NTN-B/NTN-C/NTN-F com taxas indicativas e PU. Arquivo pequeno o bastante (6.812 bytes) para versionar por inteiro como fixture: `fixtures/ms260821_fixture.txt`)
- [x] 8.2 Acrescentar `'ANBIMA'` ao contrato de evento nos três lugares que espelham o enum `source` (`contracts/events/envelope.schema.json`, `services/common` `EventSource.java`, `services/function-marketdata` `envelope.ts`) — verificado que os três módulos (Java `services/common`, Java `curve-processor`, TypeScript feeder) continuam compilando/passando depois da mudança
- [x] 8.3 Implementar `FeederAnbimaMercadoSecundario` (`src/feeders/anbima-mercado-secundario.ts`) — corte estrutural por linha (`src/linhas.ts`, novo módulo genérico, byte a byte como `xml-estrutural.ts`, nunca decodifica o arquivo inteiro para string antes de cortar), registrado sob `ANBIMA_MERCADO_SECUNDARIO`
- [x] 8.4 Testar os três resultados (publicação/sem dado/falha) contra o formato real, incluindo decodificação ISO-8859-1 correta e detecção de tamanho divergente do `Content-Length` real (a ANBIMA, ao contrário da B3, declara `Content-Length` de verdade)
- [x] 8.5 Escrever o teste de contrato opcional contra a ANBIMA real (`src/feeders/anbima-mercado-secundario.contract.ts`) — rodei de verdade: baixa e processa o `ms260821.txt` real, e confirma `NO_DATA` (HTTP 404 real, diferente do comportamento da B3) para uma data futura
- [x] 8.6 Unificar o registro de datasets das duas fontes num composition root único (`src/registro-feeders-completo.ts`, chamado por `main.ts` e `azure-function-handler.ts`) — acrescentar uma fonte nova não muda nenhum adaptador de execução
- [x] 8.7 Executar `main.ts` de verdade contra o Kafka local com o dataset ANBIMA e confirmar o evento real no tópico (2 blocos publicados de `ms260821.txt` real, confirmados lendo de volta do tópico com `kafka-console-consumer` real)

## 9. Fonte BCB (CDI/SELIC anualizados base 252, para desbloquear curve-engine 4.2)

Pedido explícito do usuário nesta sessão: `services/curve-engine`'s `RateHelper.taxaCdi()` lança
`UnsupportedOperationException` por falta de feeder de CDI (tarefa 4.2 do backlog do
curve-engine) — esta seção implementa a QUARTA fonte do function-marketdata (Banco Central do
Brasil) para desbloquear isso. Pedido explícito adicional do usuário: a taxa buscada tem que ser
a ANUALIZADA (base 252), não a diária — moldou a escolha dos códigos de série abaixo.

- [x] 9.1 Investigar e confirmar ao vivo os códigos de série reais do BCB SGS para CDI e SELIC anualizados base 252, antes de escrever qualquer código (verificado ao vivo nesta sessão via `curl` real contra `api.bcb.gov.br`: série `12`/`11` são as taxas DIÁRIAS — descartadas, não é o que o usuário pediu. Série `4389` = "Taxa de juros - CDI anualizada base 252" (confirmado o nome oficial via busca), série `1178` = "Taxa de juros - Selic anualizada base 252" — ambas publicadas diariamente, valor real observado "13.90" (%a.a.) para 2026-08-20 nas duas séries. **Descoberta crítica que moldou a implementação**: o status HTTP de transporte desta API NÃO é confiável — fiz a MESMA requisição (mesma data sem dado) três vezes seguidas e o status variou entre `404` e `200` "mentiroso" (corpo idêntico `{"erro":{"statusCode":404,...}}` nos três casos) — confirmado reproduzindo isso ao vivo antes de delegar. Por isso a classificação PUBLISHED/NO_DATA/FAILED é feita inspecionando a FORMA do corpo JSON, nunca só `response.status` — diferente do padrão `classificarRespostaFonte` usado pelos feeders B3/ANBIMA, deliberadamente não reaproveitado aqui)
- [x] 9.2 Implementar `FeederBcbSerieTemporal` (`src/feeders/bcb-serie-temporal.ts`) parametrizado por código de série — mesmo padrão de reaproveitamento de uma classe para múltiplos datasets já usado pelo feeder B3 (`prefixoArquivo`). Registrado sob `CDI` (série 4389) e `SELIC` (série 1178) via `src/registro-feeders-bcb.ts`, unificado em `registro-feeders-completo.ts`. `payload.records` carrega o valor bruto sem conversão (`{ raw: JSON.stringify(elemento) }`, string percentual tipo "13.90" — a conversão para decimal é responsabilidade de quem consome o evento), mesma disciplina de "feeder nunca converte valor numérico" já estabelecida nos outros dois feeders. Acrescentado `'BCB'` ao contrato de evento nos três lugares que espelham o enum `source` (`contracts/events/envelope.schema.json`, `services/common` `EventSource.java`, `services/function-marketdata` `envelope.ts`), mesmo padrão da tarefa 8.2 — verificado que os três módulos continuam compilando/passando depois da mudança (`mvn test` real em `services/common`+`services/curve-processor`, verde)
- [x] 9.3 Testar os quatro resultados de classificação (publicação, sem dado com 404 real, sem dado com 200 "mentiroso" — o caso que prova a descoberta de 9.1, falha com código embutido >= 500) mais os casos de array vazio, JSON inválido, falha de transporte, dia sem pregão, e os dois datasets usando a mesma classe com URLs diferentes (`bcb-serie-temporal.test.ts`, 9 testes)
- [x] 9.4 Escrever o teste de contrato opcional contra o BCB real (`src/feeders/bcb-serie-temporal.contract.ts`) — rodei de verdade via `npm run test:contract`: PUBLISHED real para CDI e para SELIC em 2026-08-20 (dado real confirmado), NO_DATA real para fim de semana (2026-08-22) — 3/3 passando contra a API ao vivo
- [x] 9.5 Executar `main.ts` de verdade contra o Kafka local com os datasets CDI e SELIC e confirmar os dois eventos reais no tópico (`ACQUISITION_DATASET=CDI` e depois `SELIC`, ambos `referenceDate=2026-08-20`, `KAFKA_BOOTSTRAP_SERVERS=localhost:19092`; os dois `PUBLISHED`, confirmados lendo de volta do tópico `marketdata.rotina.v1` com `kafka-console-consumer` real — mensagem CDI com `"valor":"13.90"` e mensagem SELIC igualmente presentes, `loteId` batendo exatamente)

  **Nota sobre escopo desta rodada**: delegado ao agy (10 arquivos: 6 novos + 4 edições
  mínimas e localizadas) com todos os fatos da API do BCB (endpoints, formato de resposta,
  a descoberta do status HTTP não confiável, regra de classificação exata) verificados ao vivo
  por mim antes do prompt — nada deixado para o agy descobrir. Auditoria pós-execução real:
  escopo correto (exatamente os 10 arquivos autorizados, `find -newermt` confirmou), as 4
  edições mínimas conferidas por diff contra a linha de base congelada no scratchpad antes de
  delegar, bate exatamente com o especificado. **3 bugs reais encontrados e corrigidos por mim,
  não pelo agy** (segui a regra da skill agy: nunca aceitar autorrelato sem `npm run build`/
  `lint`/`test` reais): (1) `tsc --noEmit` reprovou `enviar.mock.calls[0][0]`/
  `fetchImpl.mock.calls[0][0]` sem checagem de índice possivelmente indefinido (o projeto usa
  `noUncheckedIndexedAccess`) — corrigido com `!` não-nulo nos 3 pontos; (2) `eslint` reprovou
  `any` explícito em `parsed`/`catch (err: any)` — corrigido com `unknown` + um type guard
  próprio (`ehCorpoDeErro`), seguindo o idioma já estabelecido nos outros feeders
  (`erro instanceof Error ? erro.message : String(erro)`); (3) dois testes usavam
  `correlationId: 'corr-123'`/`'corr-contract'`, que não é um UUID válido — o schema exige
  formato UUID, então `publicarBloco` lançava `EnvelopeInvalidoError` de verdade ao rodar —
  corrigido para um UUID real (mesmo usado no teste de contrato da ANBIMA); (4) o teste "falha
  de transporte" usava a config HTTP padrão (`maxRetries: 3`, `baseBackoffMs: 1000` reais, sem
  `esperarImpl` injetado) e estourava o timeout padrão de 5s do Vitest — corrigido com a mesma
  `HTTP_CONFIG_RAPIDO` já usada no teste equivalente da ANBIMA; (5) o teste dos dois datasets
  reusava a MESMA instância de `Response` mockada nas duas chamadas — corpo de `Response` só
  pode ser lido uma vez, a segunda chamada quebrava com "Body is unusable" — corrigido trocando
  `mockResolvedValue` por `mockImplementation` (`Response` nova a cada chamada). Depois das
  correções: `npm run build`/`lint`/`test` verdes (161/161), `npm run test:contract` verde
  (3/3, contra a API real do BCB), `mvn test` verde em `services/common`+`services/curve-processor`,
  e publicação real de ponta a ponta contra Kafka local confirmada para os dois datasets.
