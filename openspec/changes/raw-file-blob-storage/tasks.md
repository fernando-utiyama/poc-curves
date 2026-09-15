## 1. Infraestrutura local (Azurite)

- [ ] 1.1 Adicionar o serviço `azurite` ao compose Podman (`deploy/podman/compose.core.yaml`), com healthcheck e volume persistente, e verificar que `podman compose up` sobe o container saudável.
- [ ] 1.2 Definir a variável de conexão (connection string do emulador Azurite, já documentada publicamente com valores fixos de desenvolvimento) e propagá-la para `function-marketdata` e `curve-processor` no compose e nos scripts de execução local nativa (`scripts/start-all-local.ps1`/`.sh`).

## 2. Contrato de evento

- [ ] 2.1 Atualizar `contracts/events/marketdata-raw.schema.json`: remover `records`, adicionar `blobContainer` e `blobPath` (obrigatórios), manter `sourceUrl`/`encoding`/`contentHash`/`sizeBytes` com semântica de arquivo inteiro.
- [ ] 2.2 Atualizar os fixtures de exemplo do contrato (`contracts/events/fixtures/*.json`) e rodar a validação de schema existente para confirmar que os fixtures novos passam e os antigos (com `records`) são rejeitados de propósito.

## 3. function-marketdata: gravação em blob

- [ ] 3.1 Implementar um cliente de blob storage (`blob-storage.ts` ou similar) com uma função de escrita (`gravarBlob(fonte, dataReferencia, nomeArquivo, conteudo)`) usando o SDK oficial (`@azure/storage-blob`), com teste unitário contra um emulador/mocked client (sem depender do Azurite real rodando na suíte padrão — mesmo princípio já usado para Kafka/HTTP neste módulo).
- [ ] 3.2 Alterar `FeederB3ArquivoPesquisaPregao` para gravar o conteúdo desempacotado (após o duplo unwrap de ZIP, antes do corte estrutural) em blob no caminho `b3/<data>/<nome-arquivo>`, publicar um único evento com a referência do blob, e remover a chamada a `dividirXmlEmBlocos` nesse caminho. Verificar com teste unitário (fixture existente) que passa a publicar 1 evento em vez de N blocos.
- [ ] 3.3 Alterar `FeederAnbimaMercadoSecundario` da mesma forma, caminho `anbima/<data>/<nome-arquivo>`.
- [ ] 3.4 Implementar o feeder de TS (`b3-additional-curves`, tarefa 2, ainda pendente) já neste novo padrão desde o início — gravar `TaxaSwap.txt` em `b3/<data>/TaxaSwap.txt` uma vez, publicar N eventos (um por curva alvo configurada), cada um referenciando o MESMO blob (o path não muda por curva, só o `dataset` do evento).
- [ ] 3.5 Verificar que a suíte padrão de `function-marketdata` (`npm test`) roda sem rede/sem Azurite real, e que o teste de contrato opcional (fora da suíte padrão) cobre a gravação real contra um Azurite local.

## 4. curve-processor: leitura de blob

- [ ] 4.1 Definir `BlobStorageReadPort` (application/port) com uma operação de leitura por (container, path) retornando `byte[]` ou falha nomeada.
- [ ] 4.2 Implementar o adapter concreto (`adapter/out/blob/AzuriteBlobStorageAdapter` ou nome equivalente) usando o SDK Java oficial.
- [ ] 4.3 Alterar `ProcessarEnvelopeIngestaoUseCase`: remover `reconstruirConteudo`, ler `blobContainer`/`blobPath` do payload, buscar o conteúdo pela porta nova, verificar o hash contra `contentHash` antes de chamar o parser — falha de leitura ou de hash vai para dead-letter nomeando a causa (cenários da spec).
- [ ] 4.4 Verificar com teste que `LoteIngestao` fecha corretamente com `totalBlocos = 1` para um evento migrado, sem exigir mudança de schema de banco.
- [ ] 4.5 Rodar a suíte completa de `curve-processor` (`mvn test`) e confirmar que os parsers existentes (`B3CurvaProntaParser`, `Bvbg086PricRptParser`, `Bvbg028CadastroParser`, `B3TaxaSwapParser`) continuam funcionando sem alteração de assinatura.

## 5. Documentação e registro da decisão

- [ ] 5.1 Adicionar nota em `openspec/changes/curves-solution-architecture/design.md` registrando a reversão da decisão anti-claim-check, com link para esta mudança e a justificativa (arquivamento/reprocessamento como requisito novo, aceito conscientemente).
- [ ] 5.2 Atualizar `docs/extensao-feeders.md` com o novo padrão esperado de um feeder de arquivo (gravar em blob antes de publicar).
- [ ] 5.3 Atualizar `openspec/changes/b3-additional-curves/tasks.md` (seção 2, feeder de TS) para apontar para este novo padrão em vez do padrão de blocos Kafka descrito antes desta mudança existir.

## 6. Ponta a ponta

- [ ] 6.1 Rodar o fluxo completo local (`podman compose up`) para uma data de pregão real, disparar a aquisição de PR/IN e verificar: blob gravado no caminho esperado, um único evento no tópico, ingestão completa em `ponto_dado_mercado` idêntica ao comportamento anterior (mesma contagem de pontos, mesmos valores).
- [ ] 6.2 Repetir para ANBIMA e para o feeder de TS (quando implementado).
