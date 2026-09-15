## 1. Layout do TaxaSwap.txt (engenharia reversa validada)

- [x] 1.1 ~~Documentar em fixture/nota técnica~~ — layout oficial obtido do usuário (`docs/Layout Mercado de Derivativos - Taxas de Mercado para Swaps-atual.zip`, planilha `TaxaSwap.xls` da própria B3), não foi preciso engenharia reversa. Documentado como Javadoc em `B3TaxaSwapParser` com as 13 colunas e posições exatas (72 colunas ao todo), conferido campo a campo contra `docs/TaxaSwap.txt` (geração 2026-09-14).
- [x] 1.2 Decodificador implementado como o próprio `DatasetParser` (`services/curve-processor/.../application/model/B3TaxaSwapParser.java`), sem camada isolada extra — mesmo padrão do `B3CurvaProntaParser` existente. Teste unitário (`B3TaxaSwapParserTest`, 10 casos) com linhas reais de PRE, DCL, PTX, INP e DPL capturadas de `docs/TaxaSwap.txt`, mais casos de erro (tamanho de linha, sinal inválido, encoding). 125/125 testes de `curve-processor` verdes.
- [ ] 1.3 **Ainda pendente**: rodar o decodificador contra as 278 linhas de PRE e comparar com uma aquisição real de `referenceRatesProxy` **da mesma data** (2026-09-14) — a fixture existente (`b3-curva-pre-20260821_fixture.csv`) é de 2026-08-21, data diferente, não serve de oráculo direto. Precisa de acesso ao vivo à B3 (fora deste ambiente) ou de uma nova captura fornecida pelo usuário. Até lá, a validação é só por plausibilidade de ordem de grandeza (feito na 1.4), não por comparação exata dígito a dígito.
- [x] 1.4 Confirmado por amostragem manual contra `docs/TaxaSwap.txt`: PRE=13,90% (bate com a mesma ordem de grandeza da fixture de referenceRatesProxy), DCL=-117,96% (cupom cambial negativo, plausível), PTX=5,1696 (câmbio R$/US$, plausível), INP=185.648 pontos (Ibovespa, plausível), DPL=18,59% (cupom IPCA D+1, plausível). O parser não arredonda para a escala final de publicação de cada curva (2/7 casas do Manual) — isso fica para a política de arredondamento na publicação, mesmo padrão já usado pelo `B3CurvaProntaParser` existente, que também não arredonda na ingestão.

## 2. function-marketdata: aquisição do arquivo

**Correção de desenho encontrada ao implementar** (ver design.md D1/D3): `ProcessarEnvelopeIngestaoUseCase` assume hoje 1 dataset = 1 curva (o nome do dataset vira o identificador da curva importada — `publicarCurvaImportadaSePossivel`, linha ~161). Como o `TaxaSwap.txt` traz 106 códigos no mesmo arquivo, o feeder baixa o arquivo **uma vez** mas publica **um evento Kafka por curva alvo** (`B3_TAXA_SWAP_DCL`, `B3_TAXA_SWAP_PTX`, `B3_TAXA_SWAP_INP`, `B3_TAXA_SWAP_DPL` — e `B3_TAXA_SWAP_PRE` só para o oráculo da tarefa 1.3), cada um roteado ao parser específico daquele código pelo `DatasetParserRegistry` já existente — zero mudança no pipeline genérico de ingestão/publicação.

- [ ] 2.1 Criar fixture real do `TaxaSwap.txt` recortada (mesmo padrão de `fixtures/README.md`: poucas linhas por código alvo, PRE/DCL/PTX/INP/DPL, com origem e data de captura documentadas) a partir de `docs/TaxaSwap.txt`.
- [ ] 2.2 Implementar o feeder do arquivo `TS<AAMMDD>` (`pesquisapregao/download?filelist=TS<AAMMDD>.zip` — **um só desempacotamento de ZIP**, diferente do zip-dentro-de-zip de PR/IN; confirmado com `unzip -l` contra `docs/TS260914.exe`, que é um self-extracting zip contendo só `TaxaSwap.txt`; reaproveita `verificarArquivoZip`/`lerEntradaMaisRecente` de `zip.ts` para o desempacotamento e `extrairLinhas`/`dividirLinhasEmBlocos` de `linhas.ts` para o corte estrutural, já que o conteúdo é texto de largura fixa, não XML), publicando **um evento por curva alvo configurada** (mesmo conteúdo bruto do arquivo, `dataset` diferente por evento), e verificar com teste unitário offline (contra a fixture da tarefa 2.1) que publica N eventos com `payloadKind: READY_CURVE`.
- [ ] 2.3 Registrar os datasets novos (`B3_TAXA_SWAP_PRE|DCL|PTX|INP|DPL`) em `registro-feeders-b3.ts` e verificar que os testes de `function-marketdata` (`npm test`) continuam verdes.
- [ ] 2.4 Escrever teste de contrato opcional (fora da suíte padrão, como os demais) que baixa o `TS<AAMMDD>` real da B3 ao vivo e verifica que a estrutura (um zip, uma entrada `TaxaSwap.txt`, 72 colunas por linha) ainda bate com a fixture — mesmo padrão de `b3-curva-referencia.contract.ts`.

## 3. curve-processor: parsing e extração por curva

- [x] 3.1 `B3TaxaSwapParser` implementado (`application/model/`), parametrizado por `(dataset, codigoCurva)` — mesmo padrão de instanciação de `B3CurvaProntaParser(dataset)`. Cada instância registrada filtra só o código de curva configurado e ignora silenciosamente todos os outros presentes no mesmo arquivo (testado em `parse_ignoraCodigosDeCurvaForaDoEscopoNoMesmoArquivo`, com 5 códigos fora de escopo misturados). 10 testes unitários com dados reais, 125/125 do módulo verdes.
- [x] 3.2 **Revisado**: como cada instância de parser já é 1 dataset = 1 curva (mesma invariante do `ProcessarEnvelopeIngestaoUseCase` existente), "múltiplos eventos por curva" é responsabilidade do feeder (tarefa 2.2 revisada), não do parser — nada a implementar aqui além do que a tarefa 3.1 já cobre. A "publicação independente por curva" (spec) é garantida de graça: cada evento Kafka já é processado e publicado de forma independente pelo pipeline genérico existente.
- [x] 3.3 Coberto pelo teste `parse_falhaQuandoCodigoDeCurvaNaoAparece`: arquivo sem nenhuma linha do código configurado retorna `ParseResult.Falha` nomeando o código — o roteamento genérico existente (`ParseFalhouException`) já leva isso à dead-letter, sem necessidade de lógica nova.
- [ ] 3.4 Registrar as 5 instâncias do parser (`PRE`, `DCL`, `PTX`, `INP`, `DPL`) em `ParserConfig`, uma por dataset (`B3_TAXA_SWAP_<CODIGO>`) — pendente até o feeder (tarefa 2) definir os nomes de dataset definitivos.

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
