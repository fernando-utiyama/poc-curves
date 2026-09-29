# Guia de implementação: conector-b3-webhook-ingest

Guia passo a passo para aplicar esta change com o mínimo de decisões. A spec manda; este guia diz **onde** e **como**. Ordem: seção 1 (conector), seção 2 (processor), seção 3 (testes, ao final), seção 4 (verificação ponta a ponta). Cada passo tem arquivo, o que fazer e o teste que prova.

Regras para quem implementa:
- Não invente nome, rota, variável, tabela ou coluna fora deste guia e das specs.
- Não crie tópico Kafka, fila, tabela, sequência nem índice.
- Mensagens para o usuário em pt-BR, com acentuação. Instantes no horário de Brasília (`America/Sao_Paulo`), com deslocamento.
- Arquivos de configuração existentes (`services/conector/package.json`, `jest.config.js`, `config/*/config.yaml`, `local.settings.*.example.json`; `services/processor/pom.xml`, `src/main/resources/application*.yml`, `KafkaConfig.java`, `FeignConfiguration.java`): **não reescrever**. Só verificar se têm o que este guia indica e, se faltar algo, acrescentar apenas esse item.

Vetor de teste real (use em todos os testes de ponta a ponta), `docs/TaxaSwap.txt`:
- 30.481 linhas de 72 caracteres, fim de linha `\r\n`, 2.255.594 bytes, data de geração `20260914`, 114 códigos (`PRE`, `DCL`, `DPL`, `INP`, `PTX` com 278 linhas cada).
- Forma canônica: 2.225.113 bytes, SHA-256 `46a249c60bec1ac111d69934b8486213eedecdf9b772246df86a11b5f120d9e8`.
- `idCarga`: `B3-TS-20260914-46a249c60bec`.

---

## 1. Conector (`services/conector`, TypeScript, Azure Functions v4)

### 1.1 Remover (os testes são tratados na seção 3)

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

Casos de teste (escritos na seção 3) para `test/b3/taxaSwapCanonico.spec.ts`:
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

Casos de teste (escritos na seção 3), com o SDK mockado: `lerBlob` devolve `null` em 404; `gravarBlobSeNaoExistir` trata 409 como sucesso; `garantirPastaRecebidos` grava `.keep` só quando a listagem vem vazia.

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

### 1.7 Criar `src/shared/b3/httpB3.ts` (autenticação, correlação, respostas)

```ts
export function correlationIdDe(req: HttpRequest): string;       // header X-Correlation-Id ou crypto.randomUUID()
export function autenticar(req: HttpRequest): { usuario: string | null };
// Autenticação do App Service: header x-ms-client-principal (base64 de JSON { claims: [{ typ, val }] }).
// Sem header → ErroHttp(401, "NAO_AUTENTICADO"). Aceita se houver claim typ "roles" com val "Curvas.Operador",
// ou claim "appid" igual a process.env.B3_ORQUESTRADOR_APP_ID. Senão → ErroHttp(403, "SEM_PERMISSAO").
// usuario = claim "preferred_username" (ou null).
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

Todas as rotas: a autenticação é do Entra ID, pela autenticação do App Service (requisição sem login é recusada antes da function), e o handler chama `autenticar(req)` no início para conferir o papel. Não exigir chave de função: o front e o orquestrador mandam só o token. Métodos: `["POST"]`.

**a) `src/functions/b3/b3TaxaSwapDownloadHttpTrigger.ts`** (a partir de `b3ContingencyHttpTrigger.ts`, que é removido)
- `app.http("b3TaxaSwapDownloadTrigger", { route: "b3/taxa-swap/download", handler: b3TaxaSwapDownloadHandler })`.
- Manter `resolveRequestedDate` (`date`) e `resolveFileName` (`file`); erro de formato → `ErroHttp(400, "PARAMETRO_INVALIDO")`.
- Fluxo: baixar como hoje (`downloadSwapExFile`/`downloadSwapExFileForDate`) → `extractSwapTextFromEx` → `publicarCarga({ texto, origem: "DOWNLOAD", usuario: null, correlationId, gravarCopiaTrabalho: true })`.
- A pasta vem da data de geração do arquivo (passo 3 de 1.6), **não** de `buildBlobDateFolder`.
- Sem o termo "contingência"/`contingency` em nomes, logs e mensagens.

**b) `src/functions/b3/b3TaxaSwapReprocessamentoHttpTrigger.ts`** (a partir de `b3HttpTrigger.ts`, que é removido)
- `app.http("b3TaxaSwapReprocessamentoTrigger", { route: "b3/taxa-swap/reprocessamento", handler: b3TaxaSwapReprocessamentoHandler })`.
- Parâmetro de query opcional `dataBase` (`^\d{4}-\d{2}-\d{2}$` e data real; senão 400).
- Com `dataBase`: `caminho = "b3/" + AAAAMMDD + "/TaxaSwap.txt"`; `bytes = lerBlob(caminho)` (null → 404); `publicarCarga({ texto: bytes.toString("latin1"), origem: "REPROCESSAMENTO", usuario, correlationId, dataBaseEsperada: dataBase, gravarCopiaTrabalho: false })`.
- Sem `dataBase`: `garantirPastaRecebidos()`; `caminho = "recebidos/TaxaSwap.txt"`; `lerBlob` (null → 404); `publicarCarga({ …, origem: "REPROCESSAMENTO", gravarCopiaTrabalho: true })`. Nunca apagar nem alterar `recebidos/TaxaSwap.txt`.
- Na subida da function: registrar um gatilho de inicialização que chame `garantirPastaRecebidos()` (ex.: `app.hook.appStart(async () => { await garantirPastaRecebidos(); })`; falha só gera log de erro, não impede a subida).

**c) `src/functions/b3/b3TaxaSwapUploadHttpTrigger.ts`** (novo)
- `app.http("b3TaxaSwapUploadTrigger", { route: "b3/taxa-swap/upload", handler: b3TaxaSwapUploadHandler })`.
- `const form = await req.formData(); const arquivo = form.get("arquivo")` (tipo `File`); ausente, vazio ou acima de 20 MB (20 × 1024 × 1024 bytes) → 400.
- Nome terminando em `.txt` (sem diferença de caixa): `texto = Buffer.from(await arquivo.arrayBuffer()).toString("latin1")`. Terminando em `.ex_`: `texto = extractSwapTextFromEx(Buffer.from(await arquivo.arrayBuffer()))`. Outra extensão → 400.
- `dataBase` opcional (query), validada como em b); `publicarCarga({ texto, origem: "UPLOAD", usuario, correlationId, dataBaseEsperada, gravarCopiaTrabalho: true })`.

Casos de teste (escritos na seção 3; um arquivo por rota, dependências mockadas), cobrindo cada cenário da spec `b3-taxaswap-publicacao`:
- download: sucesso com `origem` `DOWNLOAD`; `date` inválida → 400; B3 falhando → 502; sem token → 401.
- reprocessamento: com data e arquivo → 200 `REPROCESSAMENTO` sem regravar a cópia de trabalho; data divergente → 422 `DATA_BASE_DIVERGENTE`; arquivo ausente → 404; sem data e com `recebidos/TaxaSwap.txt` → 200 e cópia de trabalho gravada; sem data e sem arquivo → `.keep` criado e 404; `recebidos/TaxaSwap.txt` nunca apagado.
- upload: `.txt`, `.ex_`, extensão errada → 400, acima de 20 MB → 400, data divergente → 422, `usuario` no aviso.

### 1.9 Variáveis de ambiente do conector

| Variável | Situação |
|---|---|
| `B3_SWAP_URL`, `B3_SWAP_LOCAL_FILE` | **remover** |
| `B3_SWAP_EX_URL`, `B3_LOCAL_FILE_ENABLED`, `B3_SWAP_EX_LOCAL_FILE` | manter |
| `B3_BLOB_CONTAINER`, `B3_BLOB_ACCOUNT_URL`, `B3_BLOB_CONNECTION_STRING` (só local) | manter |
| `KAFKA_TOPIC` (= `tp-event-b3-curve`), `KAFKA_BROKERS`, `KAFKA_CLIENT_ID`, credenciais Kafka | manter |
| `B3_ORQUESTRADOR_APP_ID` | **nova**: `appid` da identidade do orquestrador |

---

## 2. Processor (`services/processor`, Java 21, Spring Boot)

Pacote base: `br.com.poc.starter.srv.hex`. **Mesma base do engine** (guia do `engine-modelos-curva`, seção 0):
- **Hexagonal, no layout que o serviço já tem:** `application/model` é o domínio (Java puro, sem Spring, JPA, Jackson ou Azure); `application/port/in` e `application/port/out` são as portas; `application/service` implementa os casos de uso; `adapter/in` (Kafka) e `adapter/out` (Blob, banco, cliente do engine) implementam as portas. O serviço só conhece as portas, nunca o adaptador.
- **Java 21 nativo:** records para todo dado, sealed para as falhas, `switch` com pattern matching, `HexFormat` e `MessageDigest` para o SHA-256, `String.lines()`, `Thread.sleep(Duration)`, `ThreadLocalRandom`. Nada de tipo próprio de data, relógio ou "utils".
- **Virtual threads:** `spring.threads.virtual.enabled: true`; o listener do Kafka roda numa virtual thread, então as esperas das novas tentativas (`Thread.sleep`) não prendem thread do sistema. Sem `synchronized`.
- **Fuso da JVM:** o `main` faz `TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))` antes do Spring, e um `ApplicationRunner` impede a subida com outro fuso. Os instantes de log (`CARGA_FALHOU` etc.) saem de `OffsetDateTime.now()`, já com `-03:00`.
- **Sem Lombok nem MapStruct no código novo:** conversões em métodos estáticos dos records.

### 2.1 Remover

- `adapter/out/persistence/inmemory/entity/B3CurveRawEntity.java`, `repository/B3JpaRepository.java`, `adapter/B3CurveRepositoryAdapter.java`;
- `application/model/B3CurveRaw.java`, `application/port/in/ProcessB3CurveUseCase.java`, `application/port/out/B3CurveRepositoryPort.java`, `application/service/ProcessB3CurveService.java`;
- testes em `src/test/java/br/com/poc/starter/srv/hex/`: `adapter/out/persistence/inmemory/adapter/B3CurveRepositoryAdapterTest.java`, `adapter/out/persistence/inmemory/entity/B3CurveRawEntityTest.java`, `application/service/ProcessB3CurveServiceTest.java`; reescrever `adapter/in/consumer/kafka/B3KafkaConsumerTest.java` (ver 2.9).
- Os consumidores das outras fontes (Anbima, Bloomberg, CME, LCH, Treasury) ficam como estão.

### 2.2 Dependências (`pom.xml`)

Verificar se existem `com.azure:azure-storage-blob` e `com.azure:azure-identity` (versão pelo BOM `com.azure:azure-sdk-bom` **1.3.8** em `dependencyManagement`, a mesma do engine); se faltarem, acrescentar só elas. `spring-boot-starter-data-jpa` já traz `JdbcTemplate`; `spring-boot-starter-actuator` já traz o Micrometer.

### 2.3 Configuração (verificar em `application.yml`)

Conferir que as chaves abaixo existem com estes valores; acrescentar só as que faltarem, sem mexer nas demais.

```yaml
processor:
  blob:
    endpoint: ${PROCESSOR_BLOB_ENDPOINT}        # https://<conta>.blob.core.windows.net
    container: ${PROCESSOR_BLOB_CONTAINER}      # o mesmo B3_BLOB_CONTAINER do conector
  engine:
    url: ${PROCESSOR_ENGINE_URL}                # endereço do engine atrás do balanceador
    escopo: ${PROCESSOR_ENGINE_ESCOPO}          # api://<app do engine>/.default
    timeout-segundos: 150
  repeticao:
    gravacao-minutos: 5
    aviso-minutos: 10
    alerta-aviso-minutos: 2
spring:
  threads:
    virtual:
      enabled: true                            # listener e requisições em virtual threads
  kafka:
    consumer:
      enable-auto-commit: false
      max-poll-records: 1
      properties:
        max.poll.interval.ms: 1200000
    listener:
      ack-mode: manual
```

Verificar em `adapter/common/json/config/KafkaConfig.java` que o bean `kafkaListenerContainerFactory` usa `AckMode.MANUAL`; se não usar, ajustar só o `AckMode`.

### 2.4 Modelo `application/model/AvisoCargaB3.java`

```java
public record AvisoCargaB3(String idCarga, String fonte, String produto, LocalDate dataBase,
                           Arquivo arquivo, String origem, String usuario, OffsetDateTime geradoEm) {
    public record Arquivo(String caminho, String sha256, long bytes) {}
}
```

Validação (classe `application/model/ValidadorAvisoCargaB3.java`, Java puro, método `static void validar(AvisoCargaB3 a)` que lança `FalhaDefinitiva("VALIDACAO", motivo)`): todas as regras da spec (campos obrigatórios; `fonte` `B3`; `produto` `TS`; `idCarga` casa `^B3-TS-\d{8}-[0-9a-f]{12}$` e a data do `idCarga` = `dataBase`; `caminho` = `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt`; `sha256` casa `^[0-9a-f]{64}$`; `origem` em `DOWNLOAD`, `REPROCESSAMENTO`, `UPLOAD`). JSON inválido → a mesma exceção.

Falhas novas em `application/exception/` (ao lado de `BusinessException` e `InfrastructureException`, que continuam para o resto do serviço), fechadas por `sealed`:

```java
public abstract sealed class FalhaCarga extends RuntimeException permits FalhaDefinitiva, FalhaTransitoria {
  private final String etapa;                                   // LEITURA, VALIDACAO, GRAVACAO, AVISO
  protected FalhaCarga(String etapa, String mensagem, Throwable causa) { super(mensagem, causa); this.etapa = etapa; }
  public String etapa() { return etapa; }
}
public final class FalhaDefinitiva extends FalhaCarga { public FalhaDefinitiva(String etapa, String motivo) { super(etapa, motivo, null); } }
public final class FalhaTransitoria extends FalhaCarga { public FalhaTransitoria(String etapa, Throwable causa) { super(etapa, causa.getMessage(), causa); } }
```

Nas seções abaixo, `FalhaDefinitivaException` e `FalhaTransitoriaException` significam `FalhaDefinitiva` e `FalhaTransitoria`.

Portas de saída (`application/port/out/`), uma por dependência:

```java
public interface ArquivoCargaPort { byte[] ler(String caminho); }                                   // Blob
public interface B3CurvaPrimariaPort {                                                               // tBtrsCurvaPrimr
  Map<String, List<String>> curvasPorCodigo();
  void gravar(LocalDate dataBase, Map<String, List<String>> curvasPorCodigo, Map<String, List<B3CurvaPrimaria>> porCodigo);
}
public interface EngineCargaPort { RespostaEngine avisar(AvisoEngine corpo, String correlationId); }  // engine
public record AvisoEngine(String idCarga, String fonte, String produto, LocalDate dataBase, Map<String, Integer> linhasPorCodigo) {}
public record RespostaEngine(int status, String corpo) {}
```

### 2.5 Leitura do arquivo: `adapter/out/blob/ArquivoCargaBlobAdapter.java` (implementa `ArquivoCargaPort`)

`byte[] ler(String caminho)` com um único `BlobContainerClient` criado na subida (bean), com `BlobServiceClientBuilder().endpoint(endpoint).credential(new DefaultAzureCredentialBuilder().build())`. 404 → `FalhaDefinitivaException("LEITURA", "arquivo inexistente: " + caminho)`; outras falhas de rede/5xx/408/429 → `FalhaTransitoriaException("LEITURA", e)`. Depois de ler, no serviço: `bytes.length == arquivo.bytes` e `HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(arquivo.sha256)`; senão `FalhaDefinitivaException("LEITURA", "divergência de tamanho|hash")`.

### 2.6 Parse e validação: `application/model/LeiauteTaxaSwap.java` (Java puro)

```java
public record B3CurvaPrimaria(String codigo, int diasCorridos, int diasUteis, BigDecimal valor, int linha) {}
public record ResultadoParse(LocalDate dataBase, Map<String, List<B3CurvaPrimaria>> porCodigo, Map<String, String> invalidos /* código → "linha N: motivo" */) {}
public static ResultadoParse interpretar(byte[] arquivo, LocalDate dataBaseEsperada);
```

Regras exatas (posições 1-based → `substring(inicio-1, fim)`):
- `var linhas = new String(arquivo, StandardCharsets.ISO_8859_1).lines().filter(l -> !l.isBlank()).toList();` (`String.lines()` já trata `\r\n`, `\n` e `\r`).
- Arquivo sem linhas, linha com tamanho ≠ 72, data (12–19) diferente entre linhas ou de `dataBaseEsperada`, ou código (22–26, `strip()`) vazio → `FalhaDefinitivaException("VALIDACAO", "linha N: <motivo>")`.
- Por linha: `dc = 42–46`, `du = 47–51`, `sinal = 52`, `taxa = 53–66`. Código fica inválido (registrar em `invalidos`, parar de ler as linhas daquele código) se: `dc` ou `du` não são só dígitos; `dc < 1`, `du < 1` ou `du > dc`; `sinal` não é `+` nem `-`; `taxa` não é só dígitos; `dc` repetido no código.
- `valor = new BigDecimal(taxa).movePointLeft(7)` com o sinal (`negate()` se `-`); escala 7. Nunca `double`.

Casos de teste (escritos na seção 3): linha da spec `0049060010120260914T1DCL  CUPOM LIMPO - S0000100001-00001179600000F00001` → `DCL`, 1, 1, `-117.9600000`. Com `docs/TaxaSwap.txt`: 114 códigos, 278 vértices em `PRE`/`DCL`/`DPL`/`INP`/`PTX`, nenhum inválido; primeiro vértice da `PRE` = `13.9000000`.

### 2.7 Gravação: `adapter/out/persistence/jdbc/B3CurvaPrimariaJdbcAdapter.java` (implementa `B3CurvaPrimariaPort`)

Consultas com o `JdbcClient` do Spring (mapeia direto para records); inclusões em lote com `JdbcTemplate.batchUpdate`. Ambos já vêm com o `spring-boot-starter-data-jpa`.

Colunas conforme o `001_SCRIPT_INICIAL.sql`: `tBtrsCurvaPrimr(cldtfdUnic int NOT NULL, cTickerIndcd varchar(50) NOT NULL, cDiaCorri int, cDiaUtil int, dBaseReft date, vFatorAcum decimal(28,16), vPrecoTx decimal(28,12), vFatorDia decimal(28,16))` e `tCurvaPrvdr(cldtfdUnic, cPriorCsumo, cPrvdrMercd varchar(50), cTickerIndcd varchar(50), cTickerPrvdr varchar(1024), iPrvdrDados varchar(1024))`. `vPrecoTx` recebe o `BigDecimal` de escala 7 sem arredondar.

SQL exato:
```sql
-- mapeamento
SELECT cTickerPrvdr, cTickerIndcd FROM tCurvaPrvdr WHERE iPrvdrDados = 'B3' AND cPrvdrMercd = 'TS';
-- por curva, dentro da transação única
DELETE FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT ISNULL(MAX(cldtfdUnic), 0) FROM tBtrsCurvaPrimr WITH (UPDLOCK, HOLDLOCK);
INSERT INTO tBtrsCurvaPrimr (cldtfdUnic, cTickerIndcd, dBaseReft, cDiaCorri, cDiaUtil, vPrecoTx, vFatorAcum, vFatorDia)
VALUES (?, ?, ?, ?, ?, ?, NULL, NULL);                 -- batchUpdate, ids = max+1, max+2, …
SELECT COUNT(*) FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;  -- tem de ser = vértices do código
```

- `gravar` é `@Transactional` (uma transação para a carga inteira). Contagem divergente → exceção que desfaz tudo (`FalhaDefinitivaException("GRAVACAO", …)`).
- Geração de `cldtfdUnic`: `MAX + 1` com `UPDLOCK, HOLDLOCK` é o padrão até o dono do schema confirmar a forma do sistema real (Open Question do design); isolar num método `proximoId()` para trocar depois sem mexer no resto.
- Nunca escrever em `tCurvaMercd` nem `tCurvaPrvdr`.
- Deadlock, timeout de comando ou perda de conexão → `FalhaTransitoriaException("GRAVACAO", e)`; violação de FK/chave → `FalhaDefinitivaException("GRAVACAO", …)`.

### 2.8 Aviso ao engine: `adapter/out/client/EngineCargaClient.java` (implementa `EngineCargaPort`)

`RespostaEngine avisar(AvisoEngine corpo, String correlationId)` com `RestClient` (um só, criado na subida; o `DefaultAzureCredential` também é um bean único, que já guarda o token até perto de expirar):
- `POST {processor.engine.url}/api/v1/cargas`, JSON `{ idCarga, fonte:"B3", produto:"TS", dataBase:"AAAA-MM-DD", linhasPorCodigo:{ código: quantidade } }`;
- cabeçalhos `Authorization: Bearer <token>` (`DefaultAzureCredential.getTokenSync(new TokenRequestContext().addScopes(escopo))`) e `X-Correlation-Id`;
- tempo limite de leitura `timeout-segundos`;
- 2xx → sucesso (guardar o corpo para o log); rede, timeout, 409, 429, 5xx → `FalhaTransitoriaException("AVISO", e)`; outro 4xx → `FalhaDefinitivaException("AVISO", corpo da resposta)`.

`linhasPorCodigo` = só os códigos gravados em pelo menos uma curva. Se vazio, não chamar o engine e registrar no log.

### 2.9 Orquestração: `application/service/ProcessarCargaB3Service.java` e o consumidor

```java
public void processar(String mensagem, String correlationId);
```
1. `AvisoCargaB3 aviso` (Jackson) + `ValidadorAvisoCargaB3.validar`;
2. `repetir("LEITURA", gravacaoMinutos, () -> blob.ler(caminho))` + conferência de tamanho e hash;
3. `LeiauteTaxaSwap.interpretar(bytes, aviso.dataBase())`;
4. `repetir("GRAVACAO", gravacaoMinutos, () -> jdbc.gravar(...))` (códigos sem curva: ignorados e contados);
5. `repetir("AVISO", avisoMinutos, () -> engine.avisar(...))`, com `AVISO_ATRASADO` (log `ERRO` + métrica) se passar de `alertaAvisoMinutos` desde o commit;
6. log JSON da carga (campos da spec) e métricas.

`repetir(etapa, minutos, acao)`: repete só em `FalhaTransitoria`, esperando 1 s, 2 s, 4 s… até 60 s, com ±10% aleatório, até a janela acabar; cada nova tentativa gera log `AVISO`. Janela esgotada → `FalhaDefinitiva(etapa, "janela esgotada")`. Só Java:

```java
static <T> T repetir(String etapa, Duration janela, Supplier<T> acao) {
  var fim = System.nanoTime() + janela.toNanos();
  var espera = Duration.ofSeconds(1);
  for (int tentativa = 1; ; tentativa++) {
    try { return acao.get(); }
    catch (FalhaTransitoria f) {
      if (System.nanoTime() + espera.toNanos() > fim) throw new FalhaDefinitiva(etapa, "janela esgotada após " + tentativa + " tentativas");
      log.warn(...);                                                  // AVISO com etapa, tentativa e causa
      long ms = espera.toMillis();
      sleep(Duration.ofMillis(ms + ThreadLocalRandom.current().nextLong(-ms / 10, ms / 10 + 1)));
      espera = espera.multipliedBy(2).compareTo(Duration.ofSeconds(60)) > 0 ? Duration.ofSeconds(60) : espera.multipliedBy(2);
    }
  }
}
// sleep: Thread.sleep(Duration); InterruptedException → reinterrompe a thread e lança FalhaDefinitiva(etapa, "interrompido")
```

O tratamento final usa `switch` com pattern matching sobre a falha (`case FalhaDefinitiva d -> ...`), e o `sealed` garante que não sobra caso.

Qualquer `FalhaDefinitivaException` → log `CARGA_FALHOU` (nível `ERRO`, campos: `idCarga`, `dataBase`, `motivo`, `etapa`, `estado` = `GRAVADA_SEM_AVISO` se a etapa for `AVISO`, senão `NAO_GRAVADA`, `tentativas`, instante de Brasília) + métrica, e **retornar normalmente** (a mensagem é confirmada).

`B3KafkaConsumer`: mantém `@KafkaListener(topics = "tp-event-b3-curve", groupId = "tp-event-extraction-group", containerFactory = "kafkaListenerContainerFactory")`; recebe `String message`, o cabeçalho `X-Correlation-Id` (`@Header(name = "X-Correlation-Id", required = false) byte[]`, na falta `UUID.randomUUID()`) e `Acknowledgment`; chama `processar` e depois `ack.acknowledge()` sempre, a não ser que `processar` lance exceção inesperada (aí não confirma e relança).

Métricas (Micrometer): `processor_b3_carga_total{resultado}` (`SUCESSO`, `SUCESSO_COM_CODIGOS_INVALIDOS`, `FALHOU`), `processor_b3_carga_falhou_total{etapa,estado}`, `processor_b3_aviso_atrasado_total`, `processor_b3_carga_duracao_segundos`.

Casos de teste (escritos na seção 3; JUnit + Mockito, sem banco real; o que só o banco prova fica para a homologação, seção 4): um por cenário da spec `b3-carga-processor`, incluindo mensagem malformada, hash divergente, linha de 60 caracteres, `DPL` com taxa `0000ABC1859000`, 110 códigos com 5 mapeados (278 linhas por curva), `SLP` sem mapeamento, `PRE` ligado a duas curvas, engine fora 3 min (`AVISO_ATRASADO`), 409 seguido de 200, janela de aviso esgotada (`GRAVADA_SEM_AVISO`), mensagem duplicada, banco fora 5 min (`NAO_GRAVADA`), e nada gravado em `mkt.B3CurveRaw`.

---

## 3. Testes (ao final da implementação)

Só depois de todo o código das seções 1 e 2 estar pronto e compilando. Os casos de teste citados nas seções 1 e 2 são a lista do que cobrir; aqui está a ordem de trabalho. Ordem fixa: verificar, adaptar, criar, rodar.

### 3.1 Verificar

1. Rodar as suítes como estão: `npm test` em `services/conector` e `mvn test` em `services/processor`.
2. Anotar cada teste que falha e o motivo: import de arquivo removido, nome ou rota renomeada, assinatura mudada ou regra mudada.
3. Buscar referências que sobraram, que não podem existir no fim:
   - conector: `contingency`, `swap-process`, `swap-contingency`, `B3_SWAP_URL`, `publishRecords`, `publishGroupedRecords`, `uploadSwapText`, `buildBlobDateFolder` (se não for mais usado), `SwapRecord`, `swapB3Layout`;
   - processor: `B3CurveRaw`, `B3JpaRepository`, `B3CurveRepositoryPort`, `ProcessB3CurveUseCase`, `ProcessB3CurveService`, `mkt.B3CurveRaw`.

### 3.2 Adaptar os testes que continuam

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

Processor (`services/processor/src/test/java/br/com/poc/starter/srv/hex/`):

| Arquivo | Ação |
|---|---|
| `adapter/in/consumer/kafka/B3KafkaConsumerTest.java` | reescrever para o consumidor novo: chama `processar` com a mensagem e o `X-Correlation-Id`, confirma com `acknowledge()`, gera correlação quando o cabeçalho falta, não confirma em exceção inesperada |
| `adapter/common/json/config/KafkaConfigTest.java` | verificar; acrescentar o caso do `AckMode.MANUAL` se faltar |
| `adapter/out/persistence/inmemory/adapter/B3CurveRepositoryAdapterTest.java`, `.../entity/B3CurveRawEntityTest.java`, `application/service/ProcessB3CurveServiceTest.java` | remover |
| testes das outras fontes (Anbima, Bloomberg, CME, LCH, Treasury) e os demais | não mexer; têm de continuar passando |

### 3.3 Criar os testes novos

Conector (`services/conector/test/b3/`):
- `taxaSwapCanonico.spec.ts`: casos de 1.3, usando `docs/TaxaSwap.txt` (caminho relativo à raiz do repositório);
- `taxaSwapCarga.spec.ts`: ordem dos passos de 1.6 (Blob e Kafka mockados); falha no passo 3 ou 4 não publica; `DataBaseDivergenteError`; `usuario` só em `UPLOAD`; `geradoEm` terminando em `-03:00`;
- `httpB3.spec.ts`: `autenticar` (sem cabeçalho → 401, sem papel → 403, papel `Curvas.Operador` → ok, `appid` do orquestrador → ok) e uma linha por exceção da tabela de 1.7, conferindo status, `codigoErro`, `correlationId` e cabeçalhos;
- `b3TaxaSwapUploadHttpTrigger.spec.ts`: casos de upload de 1.8.

Processor (mesmo pacote base, em `src/test/java`):
- `application/service/ValidadorAvisoCargaB3Test.java`: um caso por regra de 2.4;
- `application/service/LeiauteTaxaSwapTest.java`: linha da spec, `docs/TaxaSwap.txt` completo e um caso por regra de linha inválida de 2.6;
- `adapter/out/blob/ArquivoCargaBlobAdapterTest.java`: 404 definitivo, 503 transitório;
- `adapter/out/persistence/jdbc/B3CurvaPrimariaJdbcAdapterTest.java` (`JdbcTemplate` simulado): SQL e parâmetros enviados, ids `max+1, max+2...`, contagem divergente lança a falha que desfaz a transação;
- `adapter/out/client/EngineCargaClientTest.java` (servidor HTTP simulado): 2xx, 409/429/5xx transitórios, 400 definitivo, cabeçalhos;
- `application/service/ProcessarCargaB3ServiceTest.java`: cenários da spec `b3-carga-processor` listados em 2.9, com as janelas reduzidas por configuração.

### 3.4 Rodar e fechar

1. `npm test -- --coverage` no conector: tudo passa e a cobertura dos arquivos novos e alterados fica em 90% ou mais.
2. `mvn verify` no processor: tudo passa, inclusive os testes das outras fontes.
3. As buscas de 3.1 item 3 não encontram nada.
4. Se algo falhar, corrigir o código (não o teste) quando o teste reflete a spec; só mudar o teste quando ele contradiz a spec.

## 4. Verificação ponta a ponta (na homologação)

No ambiente de homologação, com Kafka, Blob e SQL Server do projeto (é aqui que se confere o que os testes simulados não provam: a transação no banco, `cldtfdUnic` sem colisão em duas cargas simultâneas e a troca de linhas numa carga nova da mesma data):
1. cadastrar em `tCurvaMercd` e `tCurvaPrvdr` as curvas `DIxPRE`, `Cupom limpo de dólar`, `Cupom Limpo DI X IPCA`, `IBOVESPA`, `PTAX - USD` ligadas a `B3`/`TS`/`PRE`, `DCL`, `DPL`, `INP`, `PTX`;
2. colocar `docs/TaxaSwap.txt` em `recebidos/TaxaSwap.txt` e chamar `POST /api/b3/taxa-swap/reprocessamento` sem data → `idCarga` `B3-TS-20260914-46a249c60bec`;
3. conferir `b3/20260914/TaxaSwap.txt` e `b3/20260914/cargas/B3-TS-20260914-46a249c60bec/TaxaSwap.txt` (2.225.113 bytes);
4. conferir 278 linhas por curva em `tBtrsCurvaPrimr` com `dBaseReft` = `2026-09-14`, primeiro `vPrecoTx` da `DIxPRE` = `13.9000000`;
5. chamar de novo com `dataBase=2026-09-14` → mesmo `idCarga`, mesmas linhas, aviso repetido ao engine (simulado);
6. `openspec validate conector-b3-webhook-ingest --strict`.
