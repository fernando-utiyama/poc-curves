## 1. Infra local (H2 compartilhado)

- [ ] 1.1 Estender `scripts/start-h2-local.ps1` para iniciar também o listener PG do H2 (`-pg -pgPort 11434 -pgAllowOthers`) no mesmo processo/banco `curvasdb`, e verificar que a porta 11434 responde após o start
- [ ] 1.2 Aplicar a mesma alteração em `scripts/start-h2-local.sh` (se existir/for usado em Linux/macOS) e verificar equivalência de comportamento com o `.ps1`
- [ ] 1.3 Validar cedo a compatibilidade da emulação PG do H2 com `MODE=MSSQLServer`: rodar manualmente um SELECT e um INSERT/DELETE de teste em `tBtrsCurvaPrimr` via `pg` (script descartável), documentando o resultado — se incompatível, acionar o plano B do design (bridge HTTP) antes de prosseguir com a Seção 2

## 2. Dependência e configuração

- [ ] 2.1 Adicionar `pg` (e `@types/pg` como dev dependency) ao `services/conector/package.json`, e verificar que `npm install` conclui sem erro
- [ ] 2.2 Adicionar `B3_DB_URL` a `services/conector/local.settings.local.example.json` (valor de exemplo apontando para `localhost:11434/curvasdb`, sem credencial real) e ao README relevante, se houver seção de variáveis de ambiente

## 3. Camada de acesso a dados

- [ ] 3.1 Criar `services/conector/src/services/b3/b3DbClient.ts`: conexão/pool `pg` lazy a partir de `B3_DB_URL`, seguindo o mesmo padrão de inicialização preguiçosa já usado em `b3BlobStorageService.ts`/`sendKafkaB3.ts`
- [ ] 3.2 Criar `services/conector/src/services/b3/b3CatalogService.ts` com `resolveTicker(cTickerIndcd): Promise<boolean>` que verifica existência em `tCurvaMercd`, e testar com fixture de banco local (ticker existente e ticker ausente)
- [ ] 3.3 Criar `services/conector/src/services/b3/b3RawVertexRepository.ts` com `replaceVerticesForDate(dBaseReft, vertices[])`: DELETE das linhas existentes de `tBtrsCurvaPrimr` para os tickers/data informados seguido de INSERT do novo conjunto, tudo em uma transação; testar que rodar duas vezes com o mesmo input não duplica linhas

## 4. Download do Blob (contraparte do upload existente)

- [ ] 4.1 Adicionar `downloadSwapText(dateFolder, fileName = "TaxaSwap.txt"): Promise<string>` a `services/conector/src/services/b3/b3BlobStorageService.ts`, simétrico ao `uploadSwapText` já existente, e testar que lê de volta exatamente o que `uploadSwapText` gravou (round-trip com Latin-1 preservado)
- [ ] 4.2 Testar o caso de blob inexistente para a data informada: erro identificando claramente a data e o caminho não encontrado

## 5. Orquestração da ingestão

- [ ] 5.1 Criar `services/conector/src/services/b3/ingestSwapFromBlob.ts` com `ingestSwapForDate(date): Promise<IngestResult>` que: baixa o texto do Blob (4.1) → reaproveita `parseFile`/`processLines` (`parserB3Service.ts`/`processB3Lines.ts`) e `normalizeCurveType` já existentes → para cada registro, resolve o ticker no catálogo (3.2) → separa registros com catálogo resolvido dos sem catálogo → grava os resolvidos via `replaceVerticesForDate` (3.3)
- [ ] 5.2 `IngestResult` deve conter contagens de linhas lidas, persistidas, descartadas por parse e sem catálogo (conforme requisito de rastreabilidade da spec); testar um arquivo fixture com uma linha malformada e um ticker desconhecido, verificando as quatro contagens

## 6. Endpoints HTTP

- [ ] 6.1 Criar `services/conector/src/functions/b3/b3WebhookHttpTrigger.ts`: `POST /api/swap-webhook`, valida payload `{ "data": "YYYY-MM-DD" }`, 400 se ausente/inválido, chama `ingestSwapForDate` e retorna o `IngestResult`
- [ ] 6.2 Criar `services/conector/src/functions/b3/b3ReprocessHttpTrigger.ts`: `POST /api/swap-reprocess?date=YYYY-MM-DD`, mesma validação e mesma chamada a `ingestSwapForDate`, independente do webhook
- [ ] 6.3 Registrar os dois novos triggers em `services/conector/src/index.ts`, seguindo o padrão dos triggers B3 já existentes
- [ ] 6.4 Verificar manualmente (ou via script local) que uma notificação/reprocessamento para uma data com arquivo já no Blob local resulta em linhas gravadas em `tBtrsCurvaPrimr`, consultáveis via `pg`

## 7. Testes automatizados

- [ ] 7.1 `test/b3/b3RawVertexRepository.spec.ts`: cobre replace idempotente (Seção 3.3) com mock do client `pg`
- [ ] 7.2 `test/b3/ingestSwapFromBlob.spec.ts`: cobre o fluxo completo com mocks de Blob/DB — sucesso total, linha malformada, ticker sem catálogo, blob ausente
- [ ] 7.3 `test/b3/b3WebhookHttpTrigger.spec.ts` e `test/b3/b3ReprocessHttpTrigger.spec.ts`: cobrem validação de payload/parâmetro (400) e delegação para `ingestSwapForDate`, seguindo o padrão dos specs já existentes em `test/b3/`

## 8. Cobertura de testes

- [ ] 8.1 Configurar `coverageThreshold` no `jest.config.js` do conector (lines/statements/functions/branches ≥ 90%), escopado aos arquivos novos desta change (`b3DbClient.ts`, `b3CatalogService.ts`, `b3RawVertexRepository.ts`, `ingestSwapFromBlob.ts`, `b3WebhookHttpTrigger.ts`, `b3ReprocessHttpTrigger.ts`, `downloadSwapText` em `b3BlobStorageService.ts`), e verificar que `npm test -- --coverage` falha se algum arquivo ficar abaixo do limite
- [ ] 8.2 Rodar `npm test -- --coverage` com todos os testes das Seções 3–7 implementados e confirmar ≥ 90% de cobertura nos arquivos listados em 8.1 — é o critério de aceite final desta change
