# Guia de implementação: conector-b3-webhook-ingest

Guia passo a passo para aplicar esta change com o mínimo de decisões. A spec manda; este guia diz **onde** e **como**. Ordem: seção 1 (conector), seção 2 (testes, ao final), seção 3 (verificação ponta a ponta). Cada passo tem arquivo, o que fazer e o teste que prova.

Este guia é o lado do conector da divisão do antigo `conector-b3-webhook-ingest`. O conector obtém o arquivo, arquiva no Blob e publica o aviso de carga em `tp-event-b3-curve`; o processor (change `processor-carga-b3`, com guia próprio) consome esse aviso, interpreta, grava em `tBtrsCurvaPrimr` e avisa o engine. Os dois são implantados juntos.

Regras para quem implementa:
- Não invente nome, rota, variável, tabela ou coluna fora deste guia e das specs.
- Não crie tópico Kafka, fila, tabela, sequência nem índice.
- Mensagens para o usuário em pt-BR, com acentuação. Instantes no horário de Brasília (`America/Sao_Paulo`), com deslocamento.
- Arquivos de configuração existentes (`services/conector/package.json`, `jest.config.js`, `config/*/config.yaml`, `local.settings.*.example.json`): **não reescrever**. Só verificar se têm o que este guia indica e, se faltar algo, acrescentar apenas esse item.

Vetor de teste real (use em todos os testes de ponta a ponta), `docs/TaxaSwap.txt`:
- 30.481 linhas de 72 caracteres, fim de linha `\r\n`, 2.255.594 bytes, data de geração `20260914`, 114 códigos (`PRE`, `DCL`, `DPL`, `INP`, `PTX` com 278 linhas cada).
- Forma canônica: 2.225.113 bytes, SHA-256 `46a249c60bec1ac111d69934b8486213eedecdf9b772246df86a11b5f120d9e8`.
- `idCarga`: `B3-TS-20260914-46a249c60bec`.

---

## 1. Conector (`services/conector`, TypeScript, Azure Functions v4)

### 1.1 Remover (os testes são tratados na seção 2)

| Arquivo | Motivo |
|---|---|
| `src/services/b3/b3Service.ts` | lia `B3_SWAP_URL`; não há download de `.txt` |
| `src/services/b3/parserB3Service.ts`, `processB3Lines.ts`, `b3SwapParser.ts`, `shouldPublishB3Curve.ts` | o conector não interpreta o arquivo |
| `src/domain/b3/curveB3TypeCatalog.ts`, `normalizeB3CurveType.ts`, `typesB3.ts` | classificação por descrição sai |
| `src/producer/b3/curveB3Factors.ts`, `swapB3MessageMapper.ts`, `swapB3GroupMessageMapper.ts` | sem fatores e sem mensagem por vértice |
| `src/models/b3/kafkaB3GroupedSwapMessage.ts`, `kafkaB3SwapMessage.ts`, `swapB3Record.ts` | formatos antigos |
| `src/layouts/b3/swapB3Layout.json` | leiaute passa a ser só do processor |
| `src/scripts/b3/demo-B3.ts` | script do fluxo antigo |


Manter sem mudança: `b3DownloadService.ts`, `b3ExtractionService.ts`, `keyVaultService.ts` e seus testes.

### 1.2 Criar `src/models/b3/avisoCargaB3.ts`

```ts
export type OrigemCargaB3 = "DOWNLOAD" | "REPROCESSAMENTO" | "UPLOAD";

export interface AvisoCargaB3 {
  idCarga: string;          // B3-TS-AAAAMMDD-<12 hex>
  fonte: "B3";
  produto: "TS";
  dataBase: string;         // AAAA-MM-DD
  arquivo: { caminho: string; sha256: string; bytes: number };
  origem: OrigemCargaB3;
  usuario: string | null;   // só em UPLOAD
  geradoEm: string;         // ISO-8601 com -03:00
}
```

### 1.3 Criar `src/services/b3/taxaSwapCanonico.ts`

```ts
import { createHash } from "crypto";

export class ArquivoInvalidoError extends Error {}

/** Forma canônica: linhas por \r\n|\n|\r, sem linhas vazias/só espaço, junção por \n, \n final, Latin-1. */
export function paraFormaCanonica(texto: string): Buffer {
  const linhas = texto.split(/\r\n|\n|\r/).filter((l) => l.trim() !== "");
  if (linhas.length === 0) throw new ArquivoInvalidoError("Arquivo sem linhas.");
  const junto = linhas.join("\n") + "\n";
  for (let i = 0; i < junto.length; i += 1) {
    if (junto.charCodeAt(i) > 0xff) {
      throw new ArquivoInvalidoError(`Caractere fora do Latin-1 na posição ${i}.`);
    }
  }
  return Buffer.from(junto, "latin1");
}

export interface IdentidadeCarga { hashArquivo: string; dataBase: string; pasta: string; idCarga: string }

/** dataBase = posições 12–19 (1-based) da primeira linha, validada como data real. */
export function identificarCarga(canonico: Buffer): IdentidadeCarga {
  const primeira = canonico.toString("latin1").split("\n")[0];
  const aaaammdd = primeira.length >= 19 ? primeira.substring(11, 19) : "";
  if (!/^\d{8}$/.test(aaaammdd) || !dataValida(aaaammdd)) {
    throw new ArquivoInvalidoError(`Data de geração inválida: '${aaaammdd}'.`);
  }
  const hashArquivo = createHash("sha256").update(canonico).digest("hex");
  const dataBase = `${aaaammdd.slice(0, 4)}-${aaaammdd.slice(4, 6)}-${aaaammdd.slice(6, 8)}`;
  return { hashArquivo, dataBase, pasta: aaaammdd, idCarga: `B3-TS-${aaaammdd}-${hashArquivo.slice(0, 12)}` };
}

function dataValida(aaaammdd: string): boolean {
  const a = +aaaammdd.slice(0, 4), m = +aaaammdd.slice(4, 6), d = +aaaammdd.slice(6, 8);
  const dt = new Date(Date.UTC(a, m - 1, d));
  return dt.getUTCFullYear() === a && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d;
}
```

Casos de teste (escritos na seção 2) para `test/b3/taxaSwapCanonico.spec.ts`:
- `docs/TaxaSwap.txt` lido como Latin-1 → canônico com 2.225.113 bytes e `idCarga` `B3-TS-20260914-46a249c60bec`;
- o mesmo texto com `\n` no lugar de `\r\n` → mesmo `idCarga`;
- texto com linha em branco no meio → mesmo resultado sem a linha;
- `"x".repeat(11) + "20260231" + "x".repeat(53)` → `ArquivoInvalidoError` citando `20260231`;
- `""` → `ArquivoInvalidoError`; texto com `"€"` → `ArquivoInvalidoError`.

### 1.4 Alterar `src/services/b3/b3BlobStorageService.ts`

Manter `getContainerClient` e `clearBlobClientCache`. Trocar `uploadSwapText` por:

```ts
export async function lerBlob(caminho: string): Promise<Buffer | null>;          // 404 → null
export async function gravarBlob(caminho: string, bytes: Buffer): Promise<void>; // sobrescreve
export async function gravarBlobSeNaoExistir(caminho: string, bytes: Buffer): Promise<void>;
// usa conditions { ifNoneMatch: "*" }; erro 409 (BlobAlreadyExists) é sucesso
export async function garantirPastaRecebidos(): Promise<void>;
// se nenhum blob com prefixo "recebidos/" (listBlobsFlat({ prefix: "recebidos/" }).next()), grava "recebidos/.keep" vazio com gravarBlobSeNaoExistir
```

`contentType` de todo `.txt`: `text/plain; charset=iso-8859-1`. Caminhos (sem `b3/` para `recebidos`):
- cópia de trabalho: `b3/{AAAAMMDD}/TaxaSwap.txt`;
- cópia imutável: `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt`;
- entrada manual: `recebidos/TaxaSwap.txt`.

Casos de teste (escritos na seção 2), com o SDK mockado: `lerBlob` devolve `null` em 404; `gravarBlobSeNaoExistir` trata 409 como sucesso; `garantirPastaRecebidos` grava `.keep` só quando a listagem vem vazia.

### 1.5 Alterar `src/producer/b3/sendKafkaB3.ts`

- Remover `publishRecords`, `publishGroupedRecords` e os imports de mapper e `SwapRecord`.
- Em `getProducer`: `kafka.producer({ idempotent: true, maxInFlightRequests: 1 })`.
- `sendMessages(topic, messages)` passa a aceitar `headers` por mensagem e envia com `acks: -1, timeout: 30000`.
- Criar:

```ts
export async function publicarAvisoCarga(aviso: AvisoCargaB3, correlationId: string): Promise<void> {
  const topic = process.env.KAFKA_TOPIC; // tp-event-b3-curve
  if (!topic) throw new Error("Variável de ambiente KAFKA_TOPIC não configurada.");
  const chave = `B3-TS-${aviso.dataBase.replace(/-/g, "")}`;
  await sendMessages(topic, [{ key: chave, value: JSON.stringify(aviso), headers: { "X-Correlation-Id": correlationId } }]);
}
```

`DISABLE_KAFKA=true` continua pulando o envio (uso local). Teste com `kafkajs` mockado: chave `B3-TS-20260914`, cabeçalho, valor JSON igual ao aviso, `acks: -1`.

### 1.6 Criar `src/services/b3/taxaSwapCarga.ts` (passo comum das três rotas)

```ts
export interface EntradaCarga {
  texto: string;                    // conteúdo já em string (Latin-1 decodificado)
  origem: OrigemCargaB3;
  usuario: string | null;
  correlationId: string;
  dataBaseEsperada?: string;        // AAAA-MM-DD; se vier e diferir → DataBaseDivergenteError
  gravarCopiaTrabalho: boolean;     // true em DOWNLOAD, UPLOAD e REPROCESSAMENTO sem data
}
export class DataBaseDivergenteError extends Error { constructor(public pedida: string, public doArquivo: string) { super(); } }
export async function publicarCarga(e: EntradaCarga): Promise<{ idCarga: string; dataBase: string; hashArquivo: string; origem: OrigemCargaB3 }>;
```

Ordem fixa dentro de `publicarCarga`:
1. `canonico = paraFormaCanonica(e.texto)`; `id = identificarCarga(canonico)`;
2. se `e.dataBaseEsperada` e `!== id.dataBase` → `DataBaseDivergenteError(e.dataBaseEsperada, id.dataBase)`;
3. se `e.gravarCopiaTrabalho`: `gravarBlob("b3/" + id.pasta + "/TaxaSwap.txt", canonico)`;
4. `gravarBlobSeNaoExistir("b3/" + id.pasta + "/cargas/" + id.idCarga + "/TaxaSwap.txt", canonico)`;
5. `publicarAvisoCarga({ idCarga, fonte: "B3", produto: "TS", dataBase, arquivo: { caminho: <passo 4>, sha256: hashArquivo, bytes: canonico.length }, origem, usuario: origem === "UPLOAD" ? usuario : null, geradoEm: agoraBrasilia() }, correlationId)`;
6. log JSON único: `correlationId`, `idCarga`, `origem`, `usuario`, `dataBase`, `hashArquivo`, `bytes`, caminhos, `resultado`, `duracaoMs`.

`agoraBrasilia()`: data e hora de `America/Sao_Paulo` no formato `AAAA-MM-DDTHH:mm:ss.SSS-03:00` (use `Intl.DateTimeFormat` com `timeZone`; o Brasil não tem horário de verão desde 2019, então o deslocamento é sempre `-03:00`).

Falha em 3, 4 ou 5 interrompe; nada é publicado se 3 ou 4 falharem.

### 1.7 Criar `src/shared/b3/httpB3.ts` (correlação, usuário, respostas)

```ts
export function correlationIdDe(req: HttpRequest): string;       // header X-Correlation-Id ou crypto.randomUUID()
export function usuarioDe(req: HttpRequest): string | null;     // header X-Usuario (enviado pelo bff) ou null; sem autenticação no conector
export class ErroHttp extends Error { constructor(public status: number, public codigoErro: string, msg: string, public detalhes: Detalhe[] = []) { super(msg); } }
export interface Detalhe { campo: string | null; linha: number | null; valor: string | null; motivo: string }
export function respostaErro(e: unknown, correlationId: string): HttpResponseInit;
export function respostaOk(corpo: object, correlationId: string): HttpResponseInit;
```

Mapa de erros para `respostaErro` (corpo `{ codigoErro, mensagem, correlationId, detalhes }`, cabeçalhos `X-Correlation-Id`, `Content-Type: application/json; charset=utf-8`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`):

| Exceção | HTTP | `codigoErro` |
|---|---|---|
| `ErroHttp` | o dela | o dela |
| parâmetro inválido (criar `ErroHttp(400, "PARAMETRO_INVALIDO", …)` na validação) | 400 | `PARAMETRO_INVALIDO` |
| arquivo a ler ausente (`lerBlob` → `null`) | 404 | `ARQUIVO_NAO_ENCONTRADO`, `detalhes[0].valor` = caminho |
| `ArquivoInvalidoError` | 422 | `ARQUIVO_INVALIDO` |
| `DataBaseDivergenteError` | 422 | `DATA_BASE_DIVERGENTE`, `detalhes` = `[{campo:"dataBase", valor: pedida, motivo:"data pedida"}, {campo:"dataGeracao", valor: doArquivo, motivo:"data do arquivo"}]` |
| erro do download na B3 (axios) | 502 | `B3_INDISPONIVEL` |
| erro do Blob ou do Kafka | 503 | `DEPENDENCIA_INDISPONIVEL` |
| qualquer outro | 500 | `ERRO_INTERNO` |

Nunca devolver stack trace. `respostaOk` = 200 com `{ idCarga, dataBase, hashArquivo, origem, correlationId }`.

### 1.8 Rotas (renomear arquivos e registrar em `src/index.ts`)

`src/index.ts` passa a importar só:
```ts
import "./functions/b3/b3TaxaSwapDownloadHttpTrigger";
import "./functions/b3/b3TaxaSwapReprocessamentoHttpTrigger";
import "./functions/b3/b3TaxaSwapUploadHttpTrigger";
import "./functions/lseg/lsegHttpTrigger";
```

Todas as rotas: sem autenticação no conector (quem autentica o usuário é o bff) e sem chave de função; o `usuario` vem de `usuarioDe(req)` (cabeçalho opcional `X-Usuario`). Métodos: `["POST"]`.

**a) `src/functions/b3/b3TaxaSwapDownloadHttpTrigger.ts`** (a partir de `b3ContingencyHttpTrigger.ts`, que é removido)
- `app.http("b3TaxaSwapDownloadTrigger", { route: "b3/taxa-swap/download", handler: b3TaxaSwapDownloadHandler })`.
- Manter `resolveRequestedDate` (`date`) e `resolveFileName` (`file`); erro de formato → `ErroHttp(400, "PARAMETRO_INVALIDO")`.
- Fluxo: baixar como hoje (`downloadSwapExFile`/`downloadSwapExFileForDate`) → `extractSwapTextFromEx` → `publicarCarga({ texto, origem: "DOWNLOAD", usuario: null, correlationId, gravarCopiaTrabalho: true })`.
- A pasta vem da data de geração do arquivo (passo 3 de 1.6), **não** de `buildBlobDateFolder`.
- Sem o termo "contingência"/`contingency` em nomes, logs e mensagens.

**b) `src/functions/b3/b3TaxaSwapReprocessamentoHttpTrigger.ts`** (a partir de `b3HttpTrigger.ts`, que é removido)
- `app.http("b3TaxaSwapReprocessamentoTrigger", { route: "b3/taxa-swap/reprocessamento", handler: b3TaxaSwapReprocessamentoHandler })`.
- Parâmetro de query opcional `dataBase` (`^\d{4}-\d{2}-\d{2}$` e data real; senão 400).
- Com `dataBase`: `caminho = "b3/" + AAAAMMDD + "/TaxaSwap.txt"`; `bytes = lerBlob(caminho)` (null → 404); `publicarCarga({ texto: bytes.toString("latin1"), origem: "REPROCESSAMENTO", usuario: null, correlationId, dataBaseEsperada: dataBase, gravarCopiaTrabalho: false })`.
- Sem `dataBase`: `garantirPastaRecebidos()`; `caminho = "recebidos/TaxaSwap.txt"`; `lerBlob` (null → 404); `publicarCarga({ …, origem: "REPROCESSAMENTO", gravarCopiaTrabalho: true })`. Nunca apagar nem alterar `recebidos/TaxaSwap.txt`.
- Na subida da function: registrar um gatilho de inicialização que chame `garantirPastaRecebidos()` (ex.: `app.hook.appStart(async () => { await garantirPastaRecebidos(); })`; falha só gera log de erro, não impede a subida).

**c) `src/functions/b3/b3TaxaSwapUploadHttpTrigger.ts`** (novo)
- `app.http("b3TaxaSwapUploadTrigger", { route: "b3/taxa-swap/upload", handler: b3TaxaSwapUploadHandler })`.
- `const form = await req.formData(); const arquivo = form.get("arquivo")` (tipo `File`); ausente, vazio ou acima de 20 MB (20 × 1024 × 1024 bytes) → 400.
- Nome terminando em `.txt` (sem diferença de caixa): `texto = Buffer.from(await arquivo.arrayBuffer()).toString("latin1")`. Terminando em `.ex_`: `texto = extractSwapTextFromEx(Buffer.from(await arquivo.arrayBuffer()))`. Outra extensão → 400.
- `dataBase` opcional (query), validada como em b); `publicarCarga({ texto, origem: "UPLOAD", usuario: usuarioDe(req), correlationId, dataBaseEsperada, gravarCopiaTrabalho: true })`.

Casos de teste (escritos na seção 2; um arquivo por rota, dependências mockadas), cobrindo cada cenário da spec `b3-taxaswap-publicacao`:
- download: sucesso com `origem` `DOWNLOAD`; `date` inválida → 400; B3 falhando → 502.
- reprocessamento: com data e arquivo → 200 `REPROCESSAMENTO` sem regravar a cópia de trabalho; data divergente → 422 `DATA_BASE_DIVERGENTE`; arquivo ausente → 404; sem data e com `recebidos/TaxaSwap.txt` → 200 e cópia de trabalho gravada; sem data e sem arquivo → `.keep` criado e 404; `recebidos/TaxaSwap.txt` nunca apagado.
- upload: `.txt`, `.ex_`, extensão errada → 400, acima de 20 MB → 400, data divergente → 422, `usuario` do `X-Usuario` no aviso e nulo sem o cabeçalho.

### 1.9 Variáveis de ambiente do conector

| Variável | Situação |
|---|---|
| `B3_SWAP_URL`, `B3_SWAP_LOCAL_FILE` | **remover** |
| `B3_SWAP_EX_URL`, `B3_LOCAL_FILE_ENABLED`, `B3_SWAP_EX_LOCAL_FILE` | manter |
| `B3_BLOB_CONTAINER`, `B3_BLOB_ACCOUNT_URL`, `B3_BLOB_CONNECTION_STRING` (só local) | manter |
| `KAFKA_TOPIC` (= `tp-event-b3-curve` (no poc; no real, o tópico configurado em `spring.kafka.topics.b3.name`)), `KAFKA_BROKERS`, `KAFKA_CLIENT_ID`, credenciais Kafka | manter |

---

## 2. Testes (ao final da implementação)

Só depois de todo o código da seção 1 estar pronto e compilando. Os casos de teste citados na seção 1 são a lista do que cobrir; aqui está a ordem de trabalho. Ordem fixa: verificar, adaptar, criar, rodar.

### 2.1 Verificar

1. Rodar a suíte como está: `npm test` em `services/conector`.
2. Anotar cada teste que falha e o motivo: import de arquivo removido, nome ou rota renomeada, assinatura mudada ou regra mudada.
3. Buscar referências que sobraram, que não podem existir no fim: `contingency`, `swap-process`, `swap-contingency`, `B3_SWAP_URL`, `publishRecords`, `publishGroupedRecords`, `uploadSwapText`, `buildBlobDateFolder` (se não for mais usado), `SwapRecord`, `swapB3Layout`.

### 2.2 Adaptar os testes que continuam

Conector (`services/conector/test/b3/`):

| Arquivo | Ação |
|---|---|
| `b3DownloadService.spec.ts`, `b3ExtractionService.spec.ts`, `keyVaultService.spec.ts`, `setup.ts` | manter; só corrigir se quebrarem por import |
| `b3BlobStorageService.spec.ts` | adaptar: trocar os casos de `uploadSwapText` pelos de `lerBlob`, `gravarBlob`, `gravarBlobSeNaoExistir` e `garantirPastaRecebidos` (1.4) |
| `kafkaService.spec.ts` | adaptar: tirar os casos de `publishRecords`/`publishGroupedRecords`; cobrir `publicarAvisoCarga`, produtor idempotente, `acks: -1`, cabeçalho e `DISABLE_KAFKA` (1.5) |
| `b3ContingencyHttpTrigger.spec.ts` | renomear para `b3TaxaSwapDownloadHttpTrigger.spec.ts` e adaptar ao handler novo (1.8 a) |
| `httpTrigger.spec.ts` | renomear para `b3TaxaSwapReprocessamentoHttpTrigger.spec.ts` e reescrever para a leitura do Blob (1.8 b) |
| `fixtures/TS260908.ex_` | manter como entrada do download e do upload de `.ex_` |
| `b3Service.spec.ts`, `b3SwapParser.spec.ts`, `curveFactors.spec.ts`, `normalizeCurveType.spec.ts`, `parserService.spec.ts`, `processLines.spec.ts`, `shouldPublishCurve.spec.ts`, `swapB3GroupMessageMapper.spec.ts`, `swapMessageMapper.spec.ts`, `sample-curvas.txt` | remover (o código testado saiu) |

### 2.3 Criar os testes novos

Conector (`services/conector/test/b3/`):
- `taxaSwapCanonico.spec.ts`: casos de 1.3, usando `docs/TaxaSwap.txt` (caminho relativo à raiz do repositório);
- `taxaSwapCarga.spec.ts`: ordem dos passos de 1.6 (Blob e Kafka mockados); falha no passo 3 ou 4 não publica; `DataBaseDivergenteError`; `usuario` só em `UPLOAD`; `geradoEm` terminando em `-03:00`;
- `httpB3.spec.ts`: `usuarioDe` (com `X-Usuario` → o valor, sem o cabeçalho → null) e uma linha por exceção da tabela de 1.7, conferindo status, `codigoErro`, `correlationId` e cabeçalhos;
- `b3TaxaSwapUploadHttpTrigger.spec.ts`: casos de upload de 1.8.

### 2.4 Rodar e fechar

1. `npm test -- --coverage` no conector: tudo passa e a cobertura dos arquivos novos e alterados fica em 90% ou mais.
2. As buscas de 2.1 item 3 não encontram nada.
3. Se algo falhar, corrigir o código (não o teste) quando o teste reflete a spec; só mudar o teste quando ele contradiz a spec.

## 3. Verificação ponta a ponta (na homologação)

No ambiente de homologação, com Kafka e Blob do projeto (a gravação em `tBtrsCurvaPrimr` e o aviso ao engine são conferidos no guia do change `processor-carga-b3`, seção 3):
1. colocar `docs/TaxaSwap.txt` em `recebidos/TaxaSwap.txt` e chamar `POST /api/b3/taxa-swap/reprocessamento` sem data → `idCarga` `B3-TS-20260914-46a249c60bec`;
2. conferir `b3/20260914/TaxaSwap.txt` e `b3/20260914/cargas/B3-TS-20260914-46a249c60bec/TaxaSwap.txt` (2.225.113 bytes);
3. conferir no tópico `tp-event-b3-curve` uma mensagem com chave `B3-TS-20260914` e o corpo do aviso (`origem` `REPROCESSAMENTO`, `sha256` e `bytes` da cópia imutável);
4. chamar de novo com `dataBase=2026-09-14` → mesmo `idCarga`, a cópia imutável não é regravada e o aviso é publicado de novo;
5. obter o mesmo conteúdo pelo download do `.ex_` (B3 simulada) e pelo upload → o mesmo `idCarga`;
6. `openspec validate conector-b3-webhook-ingest --strict`.
