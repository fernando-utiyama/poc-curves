## 1. Conectividade e configuração (decidir na implementação)

- [ ] 1.1 Localizar o script `.sql` real da fonte (schema oficial H2, banco `curvasdb`) — não presumir um caminho deste repositório; resolver como o Node.js do conector conecta nesse H2 (protocolo TCP nativo do H2 só tem driver Java; validar alternativa, ex.: expor protocolo PG no mesmo servidor compartilhado e usar o driver `pg`) antes de prosseguir com a Seção 2
- [ ] 1.2 Adicionar o driver escolhido ao `services/conector/package.json`, e verificar que `npm install` conclui sem erro
- [ ] 1.3 Adicionar a variável de ambiente de conexão (`B3_DB_URL` ou equivalente) a `services/conector/local.settings.local.example.json`, sem credencial real

## 2. Camada de acesso a dados

- [ ] 2.1 Criar `services/conector/src/services/b3/b3DbClient.ts`: conexão/pool lazy a partir da variável de ambiente de conexão, seguindo o mesmo padrão de inicialização preguiçosa já usado em `b3BlobStorageService.ts`/`sendKafkaB3.ts`
- [ ] 2.2 Criar `services/conector/src/services/b3/b3CatalogService.ts` com `resolveTicker(cTickerIndcd): Promise<boolean>` que verifica existência em `tCurvaMercd`, e testar com fixture de banco local (ticker existente e ticker ausente)
- [ ] 2.3 Criar `services/conector/src/services/b3/b3RawVertexRepository.ts` com `replaceVerticesForDate(dBaseReft, vertices[])`: DELETE das linhas existentes de `tBtrsCurvaPrimr` para os tickers/data informados seguido de INSERT do novo conjunto, tudo em uma transação; testar que rodar duas vezes com o mesmo input não duplica linhas

## 3. Download do Blob (contraparte do upload existente)

- [ ] 3.1 Adicionar `downloadSwapText(dateFolder, fileName = "TaxaSwap.txt"): Promise<string>` a `services/conector/src/services/b3/b3BlobStorageService.ts`, simétrico ao `uploadSwapText` já existente, e testar que lê de volta exatamente o que `uploadSwapText` gravou (round-trip com Latin-1 preservado)
- [ ] 3.2 Testar o caso de blob inexistente para a data informada: erro identificando claramente a data e o caminho não encontrado

## 4. Orquestração da ingestão

- [ ] 4.1 Criar `services/conector/src/services/b3/ingestSwapFromBlob.ts` com `ingestSwapForDate(date): Promise<IngestResult>` que: baixa o texto do Blob (3.1) → reaproveita `parseFile`/`processLines` (`parserB3Service.ts`/`processB3Lines.ts`) e `normalizeCurveType` já existentes → para cada registro, resolve o ticker no catálogo (2.2) → separa registros com catálogo resolvido dos sem catálogo → grava os resolvidos via `replaceVerticesForDate` (2.3)
- [ ] 4.2 `IngestResult` deve conter contagens de linhas lidas, persistidas, descartadas por parse e sem catálogo (conforme requisito de rastreabilidade da spec); testar um arquivo fixture com uma linha malformada e um ticker desconhecido, verificando as quatro contagens

## 5. Endpoints HTTP

- [ ] 5.1 Criar `services/conector/src/functions/b3/b3WebhookHttpTrigger.ts`: `POST /api/swap-webhook`, valida payload `{ "data": "YYYY-MM-DD" }`, 400 se ausente/inválido, chama `ingestSwapForDate` e retorna o `IngestResult`
- [ ] 5.2 Criar `services/conector/src/functions/b3/b3ReprocessHttpTrigger.ts`: `POST /api/swap-reprocess?date=YYYY-MM-DD`, mesma validação e mesma chamada a `ingestSwapForDate`, independente do webhook
- [ ] 5.3 Registrar os dois novos triggers em `services/conector/src/index.ts`, seguindo o padrão dos triggers B3 já existentes
- [ ] 5.4 Verificar manualmente (ou via script local) que uma notificação/reprocessamento para uma data com arquivo já no Blob local resulta em linhas gravadas em `tBtrsCurvaPrimr`, consultáveis no banco

## 6. Testes automatizados

- [ ] 6.1 `test/b3/b3RawVertexRepository.spec.ts`: cobre replace idempotente (Seção 2.3) com mock do client de banco
- [ ] 6.2 `test/b3/ingestSwapFromBlob.spec.ts`: cobre o fluxo completo com mocks de Blob/DB — sucesso total, linha malformada, ticker sem catálogo, blob ausente
- [ ] 6.3 `test/b3/b3WebhookHttpTrigger.spec.ts` e `test/b3/b3ReprocessHttpTrigger.spec.ts`: cobrem validação de payload/parâmetro (400) e delegação para `ingestSwapForDate`, seguindo o padrão dos specs já existentes em `test/b3/`

## 7. Cobertura de testes

- [ ] 7.1 Configurar `coverageThreshold` no `jest.config.js` do conector (lines/statements/functions/branches ≥ 90%), escopado aos arquivos novos desta change, e verificar que `npm test -- --coverage` falha se algum arquivo ficar abaixo do limite
- [ ] 7.2 Rodar `npm test -- --coverage` com todos os testes das Seções 2–6 implementados e confirmar ≥ 90% de cobertura nos arquivos novos — é o critério de aceite final desta change
