## 1. Layout do TaxaSwap.txt (engenharia reversa validada)

- [ ] 1.1 Documentar em uma fixture/nota técnica (`services/function-marketdata/fixtures/README.md` ou similar) a posição de cada campo do layout de 72 colunas inferido nesta sessão (sequencial, timestamp, código de 3 letras, descrição, contadores de prazo, sinal, valor escalado, flag final) — usando `docs/TaxaSwap.txt` como amostra real.
- [ ] 1.2 Escrever um decodificador isolado (sem Spring/Kafka) que extrai (prazo em dias úteis, prazo em dias corridos, valor) por código de curva a partir de uma linha do layout, e verificar com teste unitário que decodifica corretamente as linhas de exemplo de PRE, DCL, PTX e INP capturadas de `docs/TaxaSwap.txt`.
- [ ] 1.3 Rodar o decodificador contra todas as 278 linhas de PRE em `docs/TaxaSwap.txt` e comparar, vértice a vértice, com os vértices de PRE da fixture já validada (`services/function-marketdata/fixtures/b3-curva-pre-20260821_fixture.csv`) para uma data equivalente — se não houver fixture da mesma data, capturar uma nova aquisição real de `referenceRatesProxy` para a mesma data do `TaxaSwap.txt` e comparar contra ela; divergência bloqueia as próximas tarefas até ser resolvida.
- [ ] 1.4 Confirmar, a partir do Manual de Curvas (`docs/Manual de Curvas_V21.pdf`, seções 4.6, 4.1 e 5.2), a escala e o número de casas decimais esperado por curva (DCL: taxa, 2 casas; PTX: preço, 7 casas truncado; INP: pontos de índice, 2 casas) e verificar que os valores decodificados de `docs/TaxaSwap.txt` para essas curvas são plausíveis nessa escala (ordem de grandeza correta, sem overflow/underflow de casas decimais).

## 2. function-marketdata: aquisição do arquivo

- [ ] 2.1 Criar fixture real do `TaxaSwap.txt` recortada (mesmo padrão de `fixtures/README.md`: poucas linhas por código alvo, PRE/DCL/PTX/INP/DPL, com origem e data de captura documentadas) a partir de `docs/TaxaSwap.txt`.
- [ ] 2.2 Implementar o feeder do dataset `B3_TAXA_SWAP` (`pesquisapregao/download?filelist=TS<AAMMDD>.zip`), reaproveitando o cliente HTTP e a lógica de dia de pregão/retentativa já usados pelos feeders `PR`/`IN`, e verificar com teste unitário offline (contra a fixture da tarefa 2.1) que publica evento com `payloadKind: READY_CURVE`.
- [ ] 2.3 Registrar o novo dataset em `registro-feeders-b3.ts` e verificar que `openspec/changes/function-marketdata` build/testes (`npm test` no `function-marketdata`) continuam verdes.
- [ ] 2.4 Escrever teste de contrato opcional (fora da suíte padrão, como os demais) que baixa o `TaxaSwap.txt` real da B3 ao vivo e verifica que a estrutura ainda bate com a fixture — mesmo padrão de `b3-curva-referencia.contract.ts`.

## 3. curve-processor: parsing e extração por curva

- [ ] 3.1 Implementar o `DatasetParser` do layout de largura fixa (usando o decodificador da tarefa 1.2), extraindo apenas os códigos de curva com `definicao_curva` cadastrada e ignorando os demais, e verificar com teste unitário contra a fixture da tarefa 2.1.
- [ ] 3.2 Implementar a divisão de uma aquisição em múltiplos eventos de curva pronta (um por código de curva encontrado), preservando proveniência individual por curva, e verificar com teste que a falha ao publicar uma curva não impede a publicação das demais (cenário "Publicação independente por curva" da spec).
- [ ] 3.3 Implementar a rejeição para dead-letter quando um código de curva mapeado não aparece na publicação do dia, nomeando a curva e a data, e verificar com teste dedicado (cenário "Curva alvo ausente na publicação do dia" da spec).
- [ ] 3.4 Registrar o novo parser em `ParserConfig` e verificar que a suíte de testes do `curve-processor` (`mvn test`) continua verde.

## 4. Cadastro das curvas (migração e catálogo)

- [ ] 4.1 Escrever migração Flyway aditiva cadastrando as quatro `definicao_curva` (DCL, PTX, INP, DPL), modo `IMPORTED`, fonte `B3`, e verificar que a migração aplica limpa em banco local (`podman compose up` + Flyway) sem alterar dados existentes.
- [ ] 4.2 Verificar, via API de consulta de curva já existente (`curve-api`), que as quatro definições aparecem no catálogo e podem ser consultadas antes de qualquer versão publicada (estado inicial sem vértices).

## 5. Validação por oráculo cruzado (PRE)

- [ ] 5.1 Implementar a checagem automática que compara, para a mesma data de pregão, os vértices de PRE extraídos do `TaxaSwap.txt` contra os vértices de PRE já publicados via `referenceRatesProxy`, falhando a execução (sem publicar nada) em caso de divergência — conforme spec "Validação do layout por oráculo cruzado".
- [ ] 5.2 Rodar essa checagem contra uma aquisição real (mesma data para as duas fontes) e verificar que passa sem divergência antes de considerar o parser confiável para DCL/PTX/INP/DPL.

## 6. Ponta a ponta

- [ ] 6.1 Rodar o fluxo completo local (`podman compose up`) para uma data de pregão real e verificar que as quatro curvas novas aparecem publicadas (`versao_curva` em estado `PUBLISHED`) com vértices e proveniência completos, consultáveis pela API.
- [ ] 6.2 Verificar reprocessamento idempotente: rodar a mesma data duas vezes e confirmar que não duplica versão para nenhuma das quatro curvas (mesmo comportamento já garantido pelo `imported-curve-ingestion` genérico).

## 7. Reorganização de pacote no curve-engine (refactor isolado)

- [ ] 7.1 Criar o pacote `application/construcao` no `curve-engine` e mover `CurveBootstrapper`, `Interpolador` e todas as implementações, `InterpoladorRegistry`, `ConfiguracaoInterpolacao`, `RateHelper`, `SplineCubicaNatural`, `PoliticaExtrapolacao*`, `ConvencaoContagemDias`, `RoundingPolicy`, `Vertice` e `CurvaJuros` para lá, ajustando `package` e imports em todo o módulo.
- [ ] 7.2 Verificar que o build do `curve-engine` (`mvn test`) permanece 100% verde após a movimentação, sem nenhuma classe de domínio (`ModeloCurva`, `VersaoCurva`, `ProcedenciaCurva`, etc.) movida por engano.
- [ ] 7.3 Conferir que nenhum outro módulo do monorepo importa essas classes pelo pacote antigo (`grep` por `application.model.Interpolador`, `application.model.CurveBootstrapper` etc. fora do `curve-engine`) — se importar, ajustar também.
