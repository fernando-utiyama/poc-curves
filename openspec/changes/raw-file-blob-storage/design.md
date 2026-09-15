## Context

Ver proposal.md para a motivação. Estado atual, para quem for implementar:

- **Transporte hoje**: feeders de arquivo cortam o conteúdo estruturalmente (`dividirXmlEmBlocos` para XML/BVBG, `dividirLinhasEmBlocos` para texto linha a linha) e publicam N mensagens Kafka, cada uma com um subconjunto de `records[]` (fragmentos brutos). `LoteIngestao` (curve-processor) rastreia `totalBlocos`/`blocosRecebidos` e só considera o lote `COMPLETO` (disparando validação/publicação) quando todos chegam.
- **Reconstrução hoje**: `ProcessarEnvelopeIngestaoUseCase.reconstruirConteudo` reмонta o `byte[]` original juntando `payload.records[].raw` de volta com `\n`, por evento — cada evento carrega só o bloco dele, a reconstrução acontece por bloco, e a junção final entre blocos acontece na persistência (`ponto_dado_mercado`), não em memória.
- **Decisão anterior (curves-solution-architecture/design.md:45)**: claim-check foi rejeitado para não acoplar retenção do blob à da dead-letter, evitar referência pendurada, e não somar dependência de infra. Esta mudança aceita esse custo deliberadamente — Azurite já é só mais um container Podman, mesmo padrão dos demais (Kafka, SQL Server, Redis), e a POC já existe para refletir a topologia real da plataforma de produção (que usa Azure Blob Storage de verdade).
- **Escopo do "arquivo" tratado aqui**: só datasets cujo conteúdo é um arquivo baixado e desempacotado (PR/IN da B3, o TS ainda não implementado, ANBIMA mercado secundário). Datasets cuja fonte já é uma resposta de API pontual sem arquivo para arquivar (BCB séries temporais, `referenceRatesProxy` da B3 usado para a curva PRE pronta) ficam fora — não há "arquivo original" para eles no mesmo sentido.

## Goals / Non-Goals

**Goals:**
- Arquivo bruto de cada aquisição de B3/ANBIMA fica disponível em blob storage, path previsível, para auditoria e reprocessamento manual sem depender de replay de Kafka.
- Curve-processor consome exatamente o mesmo `byte[]` que consumia antes (via blob em vez de via blocos reconstruídos) — os `DatasetParser` existentes não mudam de assinatura nem de lógica interna.
- Uma aquisição bem-sucedida vira exatamente um evento Kafka (elimina a complexidade de bloco/lote para os datasets migrados).

**Non-Goals:**
- Não migra os datasets sem arquivo original (BCB CDI/SELIC, curva pronta PRE via `referenceRatesProxy`) — continuam publicando como hoje.
- Não implementa versionamento de blob (múltiplas revisões do mesmo arquivo/data ficam acessíveis por histórico do storage) — sobrescrita simples no mesmo caminho, como o Kafka já fazia implicitamente ao processar a revisão mais recente.
- Não resolve retenção/expiração do blob (ciclo de vida do container Azurite/Azure Blob) — fica para política de infraestrutura, fora do código desta mudança.
- Não migra o `B3TaxaSwapParser`/feeder de TS já em andamento (`b3-additional-curves`) para este novo padrão automaticamente — a task list de aquela mudança precisa ser atualizada para usar blob desde o início, já que o feeder de TS ainda não foi implementado (ver Migration Plan).

## Decisions

**D1. Azurite como emulador local, não sistema de arquivos direto.**
Alternativa descartada: gravar direto em um volume Podman montado como pasta. Azurite é o emulador oficial da Microsoft para Azure Blob Storage — mesma API/SDK (`@azure/storage-blob` no Node, `azure-storage-blob` ou equivalente no Java) usada contra o Azure real em produção, então o código do feeder/processor não muda ao trocar de ambiente (só a connection string). Sistema de arquivos direto exigiria uma camada de abstração própria só para esta POC, sem valor para o alvo de produção.

**D2. Caminho do blob: `<fonte>/<data-referencia>/<nome-arquivo-original>`, sem versionamento por conteúdo.**
`fonte` é o valor já usado no envelope (`B3`, `ANBIMA`), em minúsculo para o path (`b3`, `anbima`). `nome-arquivo-original` é o nome do arquivo tal como veio da fonte (ex.: `TaxaSwap.txt`, ou o XML mais recente dentre as revisões intraday do PR/IN). Reaquisição da mesma data sobrescreve — decisão consciente (Non-Goals) para manter simples; se o usuário precisar de histórico de revisões intraday, isso é uma extensão futura no path (ex.: sufixo de timestamp), não parte desta mudança.

**D3. Evento Kafka carrega referência, não conteúdo — schema `marketdata-raw.schema.json` muda de forma incompatível.**
Campos novos: `blobContainer` (nome do container/fonte), `blobPath` (caminho completo dentro do container). Campo removido: `records`. Mantidos: `sourceUrl`, `encoding`, `contentHash`, `sizeBytes` (agora descrevendo o arquivo inteiro, não um bloco). Sem período de transição dentro da POC — todos os consumidores do tópico (só o curve-processor hoje) são atualizados no mesmo commit/deploy.

**D4. `LoteIngestao` não muda de schema — `totalBlocos` fica sempre 1 para os datasets migrados.**
O modelo já suporta `totalBlocos = blocosRecebidos = 1` (lote completo no primeiro e único bloco). Evita uma segunda migração de banco só para esta mudança; datasets que eventualmente voltarem a precisar de múltiplos blocos (não previsto) continuam suportados pelo mesmo modelo.

**D5. Leitura do blob acontece em `ProcessarEnvelopeIngestaoUseCase`, atrás de uma porta nova (`BlobStorageReadPort`), no lugar de `reconstruirConteudo`.**
Mantém a mesma forma de injeção de dependência (port/adapter) já usada para as outras integrações externas do processor (Kafka, banco). O adapter concreto (`AzuriteBlobStorageAdapter` ou nome equivalente) fica em `adapter/out/`, mesma convenção hexagonal já em uso.

## Risks / Trade-offs

- **[Risco] Mudança de schema é breaking, sem tópico/versão paralela.** → Mitigação: aceitável nesta POC porque há um único produtor (function-marketdata) e um único consumidor (curve-processor) do tópico de dado bruto, ambos sob controle do mesmo deploy — não há consumidor externo para quebrar.
- **[Risco] Path de blob sobrescrito silenciosamente em reaquisição perde a revisão anterior.** → Mitigação: aceito conscientemente (D2/Non-Goals); o conteúdo publicado no Kafka (hash + path) ainda registra qual hash foi processado em cada momento, então a proveniência da versão processada continua rastreável mesmo que o blob em si não guarde histórico.
- **[Risco] Nova dependência de infraestrutura (Azurite) pode falhar independentemente do Kafka/SQL Server já monitorados.** → Mitigação: healthcheck do container Azurite no compose, mesmo padrão dos demais serviços; falha de gravação no blob impede a publicação do evento (Requirement "Falha ao gravar no blob"), então não há inconsistência entre "evento publicado" e "blob inexistente".
- **[Trade-off] Perde a resiliência a mensagem-única-grande-demais que a divisão em blocos garantia "de graça".** → Mitigação: não é mais um problema, porque o Kafka só carrega a referência (pequena) — o tamanho do arquivo original não afeta mais o tamanho da mensagem.

## Migration Plan

- Adicionar `azurite` ao compose (core ou completo — a definir na implementação, provavelmente `compose.core.yaml` já que passa a ser dependência de todo fluxo de ingestão de arquivo).
- Atualizar `contracts/events/marketdata-raw.schema.json` e os fixtures de exemplo (`contracts/events/fixtures/*.json`).
- Migrar `FeederB3ArquivoPesquisaPregao` (PR/IN) e `FeederAnbimaMercadoSecundario` para gravar em blob e publicar por referência.
- Implementar o feeder de TS (`b3-additional-curves`, tarefa 2) já usando este novo padrão desde o início, em vez do padrão de blocos Kafka descrito naquela tasks.md — **a tasks.md de `b3-additional-curves` precisa ser atualizada para refletir isso** antes da implementação do feeder prosseguir.
- Atualizar `ProcessarEnvelopeIngestaoUseCase` (remover `reconstruirConteudo`, adicionar leitura via `BlobStorageReadPort`) e registrar o novo adapter Azurite.
- Atualizar `curves-solution-architecture/design.md` com a nota de reversão da decisão de claim-check.
- Rollback: reverter para o schema anterior e os feeders antigos é possível a qualquer momento antes do merge (mudança isolada em branch), mas depois de mesclada não há caminho de rollback parcial — schema breaking exige reverter feeder e processor juntos.
