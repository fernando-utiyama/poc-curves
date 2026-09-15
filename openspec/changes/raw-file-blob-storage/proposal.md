## Why

Hoje os feeders de arquivo (B3 PR/IN/TS, ANBIMA) transportam o conteúdo bruto adquirido embutido em mensagens Kafka, cortado estruturalmente em blocos (`payload.records[]`) só para caber no limite de tamanho de mensagem — decisão registrada em `openspec/changes/curves-solution-architecture/design.md` como rejeição deliberada do padrão "claim-check" (blob + referência), pelo custo de acoplar retenção do blob à da dead-letter e somar uma dependência de infraestrutura nova. Essa dependência (armazenamento de objeto) agora é aceita explicitamente: os feeders de arquivo B3 e ANBIMA passam a gravar o arquivo bruto completo em blob storage (Azurite, emulador local do Azure Blob Storage — a POC reproduz a topologia Azure de produção), organizado em `<fonte>/<data>/<arquivo>` (ex.: `b3/2026-09-14/TaxaSwap.txt`), e o curve-processor passa a ler o conteúdo de lá para fazer a ingestão, em vez de reconstruí-lo a partir de blocos Kafka. Isso reverte a decisão anterior — o motivo muda de "não vale o custo" para "a POC quer refletir o padrão real de arquivamento/reprocessamento de arquivo de origem que a plataforma de produção usa".

## What Changes

- **BREAKING**: os feeders de arquivo (`FeederB3ArquivoPesquisaPregao` usado por PR/IN, o novo feeder de TS, `FeederAnbimaMercadoSecundario`) deixam de cortar o conteúdo em blocos Kafka (`dividirXmlEmBlocos`/`dividirLinhasEmBlocos` do lado do feeder) e passam a: (1) gravar o arquivo bruto completo (já desempacotado do ZIP, antes de qualquer parsing) em blob storage no caminho `<fonte>/<data-referencia>/<nome-arquivo-original>`; (2) publicar **um único** evento Kafka por aquisição, com o payload trazendo a referência do blob (container, path, hash, tamanho, encoding) em vez de `records[]`.
- **BREAKING**: `contracts/events/marketdata-raw.schema.json` troca o campo obrigatório `records` por uma referência de blob (`blobContainer`, `blobPath`, mantendo `sourceUrl`/`encoding`/`contentHash`/`sizeBytes` como já existem).
- O `curve-processor` ganha uma porta de leitura de blob storage (`BlobStorageReadPort` ou similar) e passa a buscar o conteúdo pelo blob referenciado no evento antes de invocar o `DatasetParser` correspondente — os `DatasetParser` existentes (`B3CurvaProntaParser`, `Bvbg086PricRptParser`, `Bvbg028CadastroParser`, `B3TaxaSwapParser`, os parsers ANBIMA) não mudam de contrato (continuam recebendo `byte[]`), só muda de onde o `byte[]` vem.
- `LoteIngestao` deixa de precisar de múltiplos blocos por aquisição (o blob já é o arquivo inteiro) — `totalBlocos` passa a ser sempre 1 para os datasets migrados; nenhuma mudança de schema, o modelo já suporta esse caso.
- Novo serviço `azurite` no `deploy/podman/compose.core.yaml` (ou `compose.yaml`), com um container/bucket dedicado por fonte (`b3`, `anbima`).
- `openspec/changes/curves-solution-architecture/design.md` ganha uma nota registrando a reversão da decisão anterior sobre claim-check, com a justificativa desta mudança.

## Capabilities

### New Capabilities
- `raw-file-blob-archival`: feeders de arquivo (B3 e ANBIMA) gravam o conteúdo bruto adquirido em blob storage antes de publicar, com caminho previsível por fonte/data/arquivo, e publicam uma referência ao blob no evento Kafka em vez do conteúdo embutido; o curve-processor lê o blob referenciado para fazer a ingestão.

### Modified Capabilities
(nenhuma — os capabilities `b3-market-data-feeder` e `imported-curve-ingestion` ainda não foram arquivados em `openspec/specs/`, então não há uma versão canônica para declarar delta; o comportamento deles muda de fato, documentado em `raw-file-blob-archival` acima e detalhado no design.md.)

## Impact

- **function-marketdata**: `FeederB3ArquivoPesquisaPregao`, `FeederAnbimaMercadoSecundario`, e o feeder de TS (ainda não implementado, `b3-additional-curves`) passam a gravar em blob antes de publicar; `dividirXmlEmBlocos`/`dividirLinhasEmBlocos` deixam de ser usados na aquisição (podem seguir existindo se o parsing de algum dataset ainda quiser cortar em memória, mas não mais para transporte Kafka).
- **curve-processor**: nova porta + adapter de leitura de blob; `ProcessarEnvelopeIngestaoUseCase.reconstruirConteudo` é substituído pela leitura via blob; `IngestaoService`/`LoteIngestao` não mudam de schema, só passam a operar sempre com 1 bloco para os datasets migrados.
- **contracts/events**: `marketdata-raw.schema.json` (breaking change de schema — todos os consumidores do tópico precisam ser atualizados juntos, não há período de transição dentro desta POC).
- **deploy/podman**: novo serviço `azurite`, variáveis de conexão (connection string/SAS) propagadas para `function-marketdata` e `curve-processor`.
- **docs**: `curves-solution-architecture/design.md` (nota de reversão da decisão), `docs/extensao-feeders.md` (novo padrão de feeder passa a gravar em blob).
