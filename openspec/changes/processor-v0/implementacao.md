# Guia de implementação: processor v0

> **Jackson 3 (Spring Boot 4):** para injetar o mapper do Spring, use `tools.jackson.databind.ObjectMapper` (ou `tools.jackson.databind.json.JsonMapper`), com `tools.jackson.core.type.TypeReference` e `JsonNode` de `tools.jackson.databind`. **Nunca** `com.fasterxml.jackson.databind.ObjectMapper`/`JsonMapper`: o Boot 4 não cria esse bean, e a aplicação não sobe ("required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"). Só as anotações continuam em `com.fasterxml.jackson.annotation`. No Jackson 3, `asText()` virou `asString()`.

> **Parâmetros de rota em objeto:** controller não recebe uma fila de `@RequestParam`. Com mais de dois parâmetros, agrupe num record: `@ModelAttribute FiltroX filtro` para consultas `GET` (o Spring preenche os campos pelos nomes da query) e `@RequestBody PedidoX pedido` para comandos `POST`/`PUT`. `@PathVariable` (identificadores como `codigo` e `dataBase`) continua separado.

> **Tipagem forte (Java 21):** dentro do domínio e das portas, nada de `Map<String, Object>`, `Object[]`, `Object` genérico ou `String` com JSON dentro. Valores fechados viram `enum`; dados viram `record`; variantes viram `sealed interface` com records. JSON cru só na borda (controller, cliente HTTP, coluna `cModDado`), desserializado direto num record; consultas nativas devolvem projeção em record.

A spec (`specs/carga-arquivos-processor/spec.md`) manda no comportamento; este guia diz **onde, com que nome e como**. Ele foi escrito para ser suficiente sozinho: tudo o que você precisaria buscar em outras changes ou em outros serviços já está copiado aqui.

## 0. Antes de começar (leia isto primeiro)

### 0.0 Como gastar pouco

Cada fase é feita com **um prompt só**, sem ida e volta: não faça perguntas, decida pelo guia e, se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga. Trabalhe tarefa por tarefa pelo cartão dela (seção 0.4): abra só os arquivos do cartão e leia só as seções que ele cita. Com o `/opsx-apply`, a proposta, o design e as specs já foram lidos uma vez: não volte a eles; tudo o que o código precisa está neste guia. Não rode a aplicação; compile e rode os testes. Para tarefas mecânicas (cartões com código pronto), um modelo de custo menor costuma bastar; confira o multiplicador do modelo na sua conta.

### 0.1 O que ler e o que não ler

Leia **só**:
1. este guia (as seções do cartão da tarefa);
2. `tasks.md` (só para marcar o que terminou);
3. a spec (`specs/carga-arquivos-processor/spec.md`) só em caso de dúvida;
4. do código existente do processor, só estes arquivos, e só quando o cartão mandar alterá-los (caminhos a partir do pacote raiz; no poc, `br.com.poc.starter.srv.hex`):
   - `util/ErrorCode.java` e `util/enumerator/BusinessErrorCode.java` (molde do enum de códigos);
   - `application/exception/` (as exceções existentes: só a assinatura dos construtores);
   - `adapter/in/api/rest/exception/handler/ApplicationExceptionHandler.java` (onde acrescentar o mapeamento);
   - `src/main/resources/application.yml` e `pom.xml` (para acrescentar, nunca reescrever).

O `/opsx-apply` já carrega `proposal.md`, `design.md` e as specs: não os releia. **Não** abra: outras changes do `openspec/`, outros serviços (engine, curves, orchestrator, conector), `docs/*.pdf`, e no processor os consumidores Kafka (`adapter/in/consumer/`), `adapter/out/persistence/inmemory/`, `application/model/*CurveRaw`, `application/service/Process*`, cache Redis e Feign. Nada disso muda nem é usado aqui.

Se algo não estiver neste guia nem na spec, **não procure no repositório**: siga o padrão mais simples do Java 21, deixe `// TODO(revisao): <dúvida>` no código e registre a dúvida no resumo da pausa.

### 0.2 Regras de código

- Java 21 nativo: `java.time`, `BigDecimal`, records, sealed interfaces, `switch` com pattern matching, `HexFormat`, `MessageDigest`, `java.util.zip`, virtual threads, `java.time.Clock`.
- Records para todo dado; conversões por métodos estáticos nos records. Sem Lombok nem MapStruct no código novo.
- Spring Boot 4 e Jackson 3: JSON com o pacote `tools.jackson` (nunca `com.fasterxml.jackson`), pelo mapper que o Spring já injeta.
- HTTP de saída: `RestClient` do Spring (já vem no `spring-boot-starter-web`). Não use Feign aqui.
- Dependências novas permitidas, só estas: `com.azure:azure-storage-blob` e `com.azure:azure-identity`, pelo BOM `com.azure:azure-sdk-bom` **1.3.8** (a mesma do engine) no `dependencyManagement`. Nenhuma outra, nem de teste.
- Não crie rota, código de erro, coluna, tabela, tópico ou script de banco fora deste guia e da spec.
- Tudo o que é novo fica nos pacotes da seção 1; os arquivos existentes só recebem acréscimos (handler, `application.yml`, `pom.xml`).

### 0.3 Prompt sugerido para o agente

Fase 1 (até a pausa):

> `/opsx-apply processor-v0` (o cabeçalho do `tasks.md` já diz como trabalhar e onde parar). Sem o apply: "Implemente as tarefas 1.1 a 1.8 e 3.1 a 3.3 de `openspec/changes/processor-v0/tasks.md`, seguindo o cabeçalho dele e `implementacao.md`; pare na 3.3."

Fase 2 (um prompt por dev, depois da revisão): o mesmo, trocando as tarefas por `2.1`, ou `4.1` e `4.2`, ou `5.1` e `5.2`, e acrescentando: "copie a estrutura do provedor ANBIMA (`adapter/out/client/anbima/`)".

### 0.4 Cartões por tarefa

Caminhos a partir do pacote raiz (`src/main/java/...`); testes em `src/test/java/...` no mesmo pacote da classe testada.

| Tarefa | Criar | Alterar | Ler no guia | Pronto quando |
|---|---|---|---|---|
| 1.1 | `application/model/carga/` (os records e enums da 2.1 e 2.2), `application/port/in/CargaArquivoUseCase`, `application/port/out/` (as 4 portas da 2.3 e `ProvedorCargaPort`), `util/enumerator/CargaErrorCode`, `application/exception/NotImplementedException` e `BadGatewayException`, `config/CargaConfig` e `config/ProcessorProperties` | `ApplicationExceptionHandler` (3 métodos), `application.yml` (seção 4), `pom.xml` (BOM do Azure e 2 dependências) | 1, 2, 3, 4 | `mvn -q compile` |
| 1.2 | `adapter/in/api/rest/controller/CargaAPI` e `CargaController`, `adapter/in/api/rest/dto/CargaResponse`, `adapter/in/api/rest/config/CorrelacaoFilter` | — | 5 (até o roteiro) | `mvn -q compile` |
| 1.3 | `adapter/out/client/b3/B3DownloadSiteProvedor`, `adapter/out/client/anbima/AnbimaDownloadSiteProvedor`, `adapter/out/client/bloomberg/BloombergDataLicenseProvedor` (provisórios); `CargaRotasTest` | — | 2.2, 12 | `CargaRotasTest` passa |
| 1.4 | `application/service/CargaArquivoService`; `CargaArquivoServiceTest` (com portas falsas no próprio teste) | — | 5 (roteiro), 12 | `CargaArquivoServiceTest` passa |
| 1.5 | `adapter/out/storage/blob/ArquivoOriginalBlobAdapter` | — | 6 | `mvn -q compile` |
| 1.6 | `adapter/out/persistence/jdbc/CurvaPrimrPersistenceAdapter`, `CurvaPrimrInsercao` e as três `*CurvaPrimrInsercao` provisórias | — | 7 | `mvn -q compile` |
| 1.7 | `application/service/AvisoEngineService`, `adapter/out/client/engine/AvisoEngineAdapter`; `AvisoEngineServiceTest` | — | 8, 12 | `AvisoEngineServiceTest` passa |
| 1.8 | — | `CargaArquivoService` (log) | 9 | `mvn -q test` |
| 3.1 | `application/model/leiaute/LeiauteAnbimaMs`; `src/test/resources/anbima/ms260928.txt` (cópia de `recursos/`); `LeiauteAnbimaMsTest` | — | 10.2, 10.3, 12 | `LeiauteAnbimaMsTest` passa |
| 3.2 | `adapter/out/client/anbima/AnbimaMsClient`; `AnbimaCargaTest` | `AnbimaDownloadSiteProvedor`, `AnbmaCurvaPrimrInsercao` | 7 (INSERT), 10.1, 12 | `mvn -q test` |
| 3.3 | resumo da pausa | — | 14 | resumo escrito; parar |
| 2.1 | `adapter/out/client/b3/B3TaxaSwapClient`, `application/model/leiaute/LeiauteTaxaSwap`; `B3CargaTest` e `LeiauteTaxaSwapTest` | `B3DownloadSiteProvedor`, `BtrsCurvaPrimrInsercao` | 11.1, 12 | `mvn -q test` |
| 4.1 | `adapter/out/client/bloomberg/BloombergDataLicenseClient`, `application/model/leiaute/LeiauteRespostaDataLicense`; `BloombergCargaTest` e `LeiauteRespostaDataLicenseTest` | `BloombergDataLicenseProvedor`, `BbergCurvaPrimrInsercao` | 11.2, 12 | `mvn -q test` |
| 4.2 | `adapter/out/client/bloomberg/TickersSofrReserva` | `CargaController.parametros`, `CargaRotasTest` | 5, 11.2 | `mvn -q test` |
| 5.1 e 5.2 | rota do bff e tela do front | — | 13 | testes do bff e conferência no navegador |

## 1. Onde fica cada coisa

Nomes no padrão do projeto: controller com interface `*API` (anotações do Swagger) e `*Controller`; caso de uso em `application/port/in`; portas de saída `*Port` (`*RepositoryPort` para banco); dados das tabelas com o nome da tabela sem o `t` (`tAnbmaCurvaPrimr` → `AnbmaCurvaPrimr`).

```
adapter/in/api/rest/controller/   CargaAPI, CargaController (as três fontes)                         (base)
adapter/in/api/rest/dto/          CargaResponse                                                      (base)
application/port/in/              CargaArquivoUseCase                                                (base)
application/port/out/             ProvedorCargaPort, ArquivoOriginalPort, CurvaPrimrRepositoryPort,
                                  AvisoEnginePort                                                    (base)
application/service/              CargaArquivoService (roteiro comum), AvisoEngineService            (base)
application/model/carga/          Fonte, OrigemCarga, ParametrosFonte, ArquivoObtido, IdentidadeCarga,
                                  CargaInterpretada, ResultadoCarga,
                                  CurvaPrimr (sealed), BtrsCurvaPrimr, AnbmaCurvaPrimr, BbergCurvaPrimr (base)
application/exception/            NotImplementedException, BadGatewayException (novas)               (base)
util/enumerator/                  CargaErrorCode                                                     (base)
config/                           CargaConfig (Clock, RestClient, BlobContainerClient, executor)     (base)
adapter/out/storage/blob/         ArquivoOriginalBlobAdapter                                         (base)
adapter/out/persistence/jdbc/     CurvaPrimrPersistenceAdapter (comum), CurvaPrimrInsercao (interface) (base)
                                  AnbmaCurvaPrimrInsercao | BtrsCurvaPrimrInsercao | BbergCurvaPrimrInsercao (um por provedor)
adapter/out/client/engine/        AvisoEngineAdapter                                                 (base)
adapter/out/client/anbima/        AnbimaDownloadSiteProvedor, AnbimaMsClient                         (ANBIMA, modelo)
application/model/leiaute/        LeiauteAnbimaMs (ANBIMA), LeiauteTaxaSwap (dev B3), LeiauteRespostaDataLicense (dev Bloomberg)
adapter/out/client/b3/            B3DownloadSiteProvedor, B3TaxaSwapClient                           (dev B3)
adapter/out/client/bloomberg/     BloombergDataLicenseProvedor, BloombergDataLicenseClient,
                                  TickersSofrReserva                                                 (dev Bloomberg)
```

Cada dev de provedor mexe só na sua pasta `adapter/out/client/{fonte}/`, no seu `Leiaute*` e na sua `*CurvaPrimrInsercao`. O processor não tem pacote `domain` nem calendário: o domínio (Java puro, sem Spring) fica em `application/model`, como na `processor-carga-b3`, que reaproveita o `LeiauteTaxaSwap`, a gravação, o aviso e o Blob desta change.

## 2. Contratos

### 2.1 Porta de entrada e modelo

```java
public interface CargaArquivoUseCase {
    ResultadoCarga baixar(Fonte fonte, LocalDate data, ParametrosFonte parametros);
    ResultadoCarga reprocessar(Fonte fonte, LocalDate data);
    ResultadoCarga receber(Fonte fonte, byte[] conteudo, String nomeArquivo, String usuario);
}

public enum Fonte {
    B3("TS", "b3"), ANBIMA("MS", "anbima"), BLOOMBERG("BLC2", "bloomberg");
    private final String produto; private final String pasta;
    Fonte(String produto, String pasta) { this.produto = produto; this.pasta = pasta; }
    public String produto() { return produto; }   // cPrvdrMercd e "produto" do aviso ao engine
    public String pasta() { return pasta; }       // prefixo no Blob
}

public enum OrigemCarga { DOWNLOAD, REPROCESSAMENTO, UPLOAD }

public record ParametrosFonte(List<String> tickers) {            // só a Bloomberg usa
    public static final ParametrosFonte VAZIO = new ParametrosFonte(List.of());
}

public record IdentidadeCarga(String idCarga, String hashArquivo) {
    public static IdentidadeCarga de(Fonte fonte, LocalDate dataBase, byte[] bytes) {
        String hash = HexFormat.of().formatHex(sha256(bytes));          // MessageDigest "SHA-256"
        String id = "%s-%s-%s-%s".formatted(fonte.name(), fonte.produto(),
                dataBase.format(DateTimeFormatter.BASIC_ISO_DATE), hash.substring(0, 12));
        return new IdentidadeCarga(id, hash);
    }
}

public record ResultadoCarga(String idCarga, LocalDate dataBase, String hashArquivo, OrigemCarga origem,
                             Map<String, Integer> verticesPorCodigo, String usuario) {}
```

### 2.2 Porta de saída de cada fonte

```java
public interface ProvedorCargaPort {
    Fonte fonte();
    ArquivoObtido baixar(LocalDate data, ParametrosFonte parametros);   // 503 ARQUIVO_INDISPONIVEL, 502 FONTE_INDISPONIVEL
    ArquivoObtido preparar(byte[] conteudo, String nomeArquivo);         // upload: extrair/canonizar; 422
    LocalDate dataBase(ArquivoObtido arquivo);                           // 422 se ilegível
    CargaInterpretada interpretar(ArquivoObtido arquivo, LocalDate dataBase);   // 422 se o arquivo inteiro cai
}

public record ArquivoObtido(byte[] bytes, String nomeArquivo) {}
public record CargaInterpretada(Map<String, List<CurvaPrimr>> verticesPorCodigo, Map<String, String> codigosRejeitados) {}

public sealed interface CurvaPrimr permits BtrsCurvaPrimr, AnbmaCurvaPrimr, BbergCurvaPrimr {}
public record BtrsCurvaPrimr(int diasCorridos, int diasUteis, BigDecimal valor) implements CurvaPrimr {}
public record AnbmaCurvaPrimr(int prazoDiasCorridos, BigDecimal taxa) implements CurvaPrimr {}   // taxa pode ser null
public record BbergCurvaPrimr(String ticker, BigDecimal valor) implements CurvaPrimr {}
```

O `CargaArquivoService` recebe `List<ProvedorCargaPort>` e monta um `EnumMap<Fonte, ProvedorCargaPort>`; faltar uma fonte derruba a subida (`IllegalStateException` no construtor).

**Provedores provisórios (base):** cada fonte nasce com a sua classe `*Provedor` (`@Component`) implementando `ProvedorCargaPort` e lançando `new NotImplementedException(CargaErrorCode.PROVEDOR_NAO_IMPLEMENTADO)` em todos os métodos, menos `fonte()`.

### 2.3 Demais portas de saída

```java
public interface ArquivoOriginalPort {
    void gravar(Fonte fonte, LocalDate dataBase, String idCarga, ArquivoObtido arquivo);   // já existe = sucesso
    Optional<ArquivoObtido> maisRecente(Fonte fonte, LocalDate dataBase);
}

public interface CurvaPrimrRepositoryPort {
    /** Grava numa transação; devolve os vértices gravados por código (códigos sem curva ficam fora). */
    Map<String, Integer> substituir(Fonte fonte, LocalDate dataBase, CargaInterpretada carga);
}

public interface AvisoEnginePort {
    /** Uma chamada; devolve o status HTTP (lança exceção em erro de rede). */
    int avisar(String idCarga, Fonte fonte, LocalDate dataBase, Map<String, Integer> verticesPorCodigo, String correlationId);
}

public interface CurvaPrimrInsercao {
    Fonte fonte();
    String tabela();                                                  // tBtrsCurvaPrimr | tAnbmaCurvaPrimr | tBbergCurvaPrimr
    void inserir(JdbcTemplate jdbc, int id, String curva, LocalDate dataBase, CurvaPrimr vertice);
}
```

A base cria as três `*CurvaPrimrInsercao` com `inserir` lançando `NotImplementedException`; a ANBIMA implementa a sua na fase 1.

## 3. Erros

Use as exceções que o processor já tem (`application/exception/`), com o código da spec num enum novo no molde do `BusinessErrorCode`:

```java
// util/enumerator/CargaErrorCode.java
public enum CargaErrorCode implements ErrorCode {
    PARAMETRO_INVALIDO("Parâmetro inválido"),
    ARQUIVO_NAO_ENCONTRADO("Nenhum arquivo original encontrado para a fonte e a data"),
    ARQUIVO_INVALIDO("Arquivo rejeitado pela validação"),
    PROVEDOR_NAO_IMPLEMENTADO("Fonte ainda sem provedor implementado"),
    FONTE_INDISPONIVEL("A fonte respondeu com erro inesperado"),
    ARQUIVO_INDISPONIVEL("Arquivo da data ainda não disponível na fonte"),
    SERVICO_INDISPONIVEL("Blob ou banco indisponível");
    // code = name(); getCode() e getMessage() como no BusinessErrorCode
}
```

| Código | HTTP | Exceção |
|---|---|---|
| `PARAMETRO_INVALIDO` | 400 | `InvalidInputException` |
| `ARQUIVO_NAO_ENCONTRADO` | 404 | `NotFoundException` |
| `ARQUIVO_INVALIDO` | 422 | `BusinessException` |
| `PROVEDOR_NAO_IMPLEMENTADO` | 501 | `NotImplementedException` (**nova**, no molde das existentes) |
| `FONTE_INDISPONIVEL` | 502 | `BadGatewayException` (**nova**, no molde das existentes) |
| `ARQUIVO_INDISPONIVEL`, `SERVICO_INDISPONIVEL` | 503 | `ServiceUnavailableException` |

- Lance com o construtor que recebe `ErrorCode` (e a mensagem com o detalhe, quando houver). Se a exceção existente não tiver esse construtor, acrescente um, sem mudar os que existem.
- No `ApplicationExceptionHandler`, acrescente só: um método para `NotImplementedException` (501), um para `BadGatewayException` (502) e um para `MaxUploadSizeExceededException` (422 `ARQUIVO_INVALIDO`).
- Corpo de erro da spec: `{ "codigoErro", "mensagem", "correlationId" }`, sem stack trace. Se o handler já monta outro formato, o teste de rotas (seção 12) decide: ajuste o handler até o corpo bater com a spec.

## 4. Configuração (acrescentar ao `application.yml`)

```yaml
processor:
  blob:
    account-url: ${PROCESSOR_BLOB_ACCOUNT_URL}       # Managed Identity (DefaultAzureCredential)
    container: ${PROCESSOR_BLOB_CONTAINER}
    connection-string: ${PROCESSOR_BLOB_CONNECTION_STRING:}   # só no perfil local; vazio = Managed Identity
  fontes:
    b3-url: https://www.b3.com.br/pesquisapregao/download?filelist=TS{AAMMDD}.ex_
    anbima-url: https://www.anbima.com.br/informacoes/merc-sec/arqs/ms{AAMMDD}.txt
    timeout: 60s
  bloomberg:
    base-url: ${BLOOMBERG_DL_BASE_URL}               # [A CONFIRMAR] host da API do Data License
    catalogo: ${BLOOMBERG_DL_CATALOGO}               # [A CONFIRMAR] catálogo da conta
    campo: PX_LAST
    faixa-pedido: 30m
    espera: 90s
    # id e segredo da credencial: Key Vault, nunca no yml
  engine:
    base-url: ${ENGINE_BASE_URL}
    timeout: 150s
    aviso-janela: 10m
    aviso-alerta: 2m
    aviso-espera-inicial: 1s
    aviso-espera-maxima: 60s
spring.servlet.multipart.max-file-size: 10MB
spring.servlet.multipart.max-request-size: 11MB
```

Ligue com um `@ConfigurationProperties(prefix = "processor")` em record (`Duration` para os tempos). `{AAMMDD}` é trocado pela data (`DateTimeFormatter.ofPattern("yyMMdd")`); nenhum outro parâmetro da chamada entra no endereço.

`CargaConfig` cria: `Clock` (`Clock.system(ZoneId.of("America/Sao_Paulo"))`, injetado onde houver "hoje", para os testes fixarem a data), um `RestClient` por destino com o tempo limite da configuração, o `BlobContainerClient` e o executor do aviso (`Executors.newVirtualThreadPerTaskExecutor()`).

## 5. Rotas e roteiro comum

| Rota | Caso de uso | `origem` |
|---|---|---|
| `POST /api/v1/cargas/{fonte}/download`, corpo `PedidoCarga` | `baixar(fonte, data, parametros)` | `DOWNLOAD` |
| `POST /api/v1/cargas/{fonte}/reprocessamento`, corpo `PedidoCarga` (`tickers` aceito e ignorado) | `reprocessar(fonte, data)` | `REPROCESSAMENTO` |
| `POST /api/v1/cargas/{fonte}/upload` (multipart `arquivo`, cabeçalho `X-Usuario`) | `receber(fonte, bytes, nome, usuario)` | `UPLOAD` |

Um só `CargaController` (`@RequestMapping("/api/v1/cargas/{fonte}")`) para as três fontes. Download e reprocessamento recebem `@RequestBody PedidoCarga pedido`:

```java
// adapter/in/api/rest/dto/PedidoCarga.java
public record PedidoCarga(String dataBase, List<String> tickers) {
    public List<String> tickersOuVazio() { return tickers == null ? List.of() : tickers; }
}
```

Corpo ausente ou JSON malformado → 400 `PARAMETRO_INVALIDO` (tratar `HttpMessageNotReadableException` no handler).

```java
private static Fonte fonte(String texto) {        // b3 | anbima | bloomberg
    return Arrays.stream(Fonte.values()).filter(f -> f.name().equalsIgnoreCase(texto)).findFirst()
        .orElseThrow(() -> new InvalidInputException(CargaErrorCode.PARAMETRO_INVALIDO));
}

private static ParametrosFonte parametros(Fonte fonte, List<String> informados) {
    if (fonte != Fonte.BLOOMBERG) {
        if (!informados.isEmpty()) throw new InvalidInputException(CargaErrorCode.PARAMETRO_INVALIDO);
        return ParametrosFonte.VAZIO;
    }
    List<String> tickers = informados.isEmpty() ? TickersSofrReserva.TICKERS : informados; // TODO(retirar) reserva; log TICKERS_RESERVA
    return new ParametrosFonte(tickers);
}
```

O reprocessamento também passa os `tickers` por `parametros(...)`, só para recusar `tickers` com `b3` ou `anbima`; na Bloomberg, o valor é ignorado (valem os tickers do original). Na base (antes da Bloomberg), `TickersSofrReserva` ainda não existe: use `new ParametrosFonte(informados)` e deixe o `TODO(retirar)` para a tarefa 4.2.

Validação no controller, lançando `InvalidInputException(PARAMETRO_INVALIDO)`:
- data `AAAA-MM-DD` (`LocalDate.parse`) e não futura (`LocalDate.now(clock)`);
- `tickers` (lista JSON): cada um com `^[A-Za-z0-9]+( [A-Za-z0-9]+)*$`, até 50 caracteres, até 1.000 tickers; os repetidos são removidos;
- `X-Usuario` opcional no upload (`required = false`; ausente = usuário nulo). Sem autenticação na v0 e na v1.

`X-Correlation-Id`: lido da requisição ou gerado (`UUID.randomUUID()`), posto no MDC (`correlationId`) e devolvido no cabeçalho da resposta, num `OncePerRequestFilter` em `adapter/in/api/rest/config/`. O `CargaResponse` é o `ResultadoCarga` mais o `correlationId`; datas em ISO.

Roteiro do `CargaArquivoService`, igual para as três fontes:

1. obter o arquivo: `provedor.baixar(...)`, `provedor.preparar(...)` (upload) ou `arquivoOriginal.maisRecente(...)` (reprocessamento; vazio = 404 `ARQUIVO_NAO_ENCONTRADO` com o prefixo);
2. `dataBase = provedor.dataBase(arquivo)`:
   - download: diferente da data pedida = 503 `ARQUIVO_INDISPONIVEL`, sem arquivar; se lançar 422, arquivar sob a **data pedida** e relançar o 422;
   - upload: futura = 422; se lançar 422, relançar sem arquivar (log com usuário e nome do arquivo);
3. `IdentidadeCarga.de(fonte, dataBase, arquivo.bytes())`; `arquivoOriginal.gravar(...)` (no reprocessamento, pular: já existe);
4. `provedor.interpretar(arquivo, dataBase)` (422 se o arquivo inteiro cai);
5. `curvaPrimrRepository.substituir(...)`;
6. se algum código foi gravado, `avisoEngine.avisarEmSegundoPlano(...)`; devolver o `ResultadoCarga`.

"Ainda não saiu" (503 antes de arquivar) é decidido dentro do `baixar` de cada provedor.

## 6. Blob (`ArquivoOriginalBlobAdapter`)

- Caminho: `{fonte.pasta()}/{AAAAMMDD}/cargas/{idCarga}/{arquivo.nomeArquivo()}`.
- Gravação: `blobClient.uploadWithResponse(new BlobParallelUploadOptions(BinaryData.fromBytes(bytes)).setRequestConditions(new BlobRequestConditions().setIfNoneMatch("*")), null, Context.NONE)`; `BlobStorageException` com status 409 = já existe = sucesso; qualquer outra falha = `ServiceUnavailableException(SERVICO_INDISPONIVEL)`.
- Reprocessamento: `listBlobs(new ListBlobsOptions().setPrefix("{pasta}/{AAAAMMDD}/cargas/"), null)` e pegar o de maior `getProperties().getCreationTime()`.
- Só originais: nada de estado, resultado ou log no Blob.

## 7. Gravação (`CurvaPrimrPersistenceAdapter`)

Uma transação por carga (`@Transactional`), `JdbcTemplate`:

```sql
SELECT cTickerPrvdr, cTickerIndcd FROM tCurvaPrvdr WHERE iPrvdrDados = ? AND cPrvdrMercd = ?;   -- B3/TS, ANBIMA/MS, BLOOMBERG/BLC2
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;           -- cada curva, em ordem do nome
DELETE FROM {tabela} WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT ISNULL(MAX(cIdtfdUnic), 0) FROM {tabela} WITH (UPDLOCK, HOLDLOCK);
INSERT ...;                                                                                      -- feito pela *CurvaPrimrInsercao
SELECT COUNT(*) FROM {tabela} WHERE cTickerIndcd = ? AND dBaseReft = ?;                         -- = vértices do código, senão desfaz
```

- `{tabela}` vem da `CurvaPrimrInsercao` da fonte, nunca da requisição.
- `trim()` nas colunas `CHAR` lidas; um código pode ligar a mais de uma curva (grava para cada uma).
- Tempo limite de 60 s nos comandos de trava e de `MAX` (`jdbc.setQueryTimeout` num `JdbcTemplate` próprio do adapter); estouro ou banco fora = `ServiceUnavailableException(SERVICO_INDISPONIVEL)`.
- Código sem curva ligada: fica fora do resultado e vai para o log como ignorado.

`INSERT` da ANBIMA (`AnbmaCurvaPrimrInsercao`):

```sql
INSERT INTO tAnbmaCurvaPrimr (cIdtfdUnic, cTickerIndcd, dBaseReft, vPrecoTx, vVertcCurva) VALUES (?, ?, ?, ?, ?);
```

## 8. Aviso ao engine

- `AvisoEngineService.avisarEmSegundoPlano(...)`: chamado depois do commit (fora da transação), submete ao executor de virtual threads e retorna na hora.
- `AvisoEngineAdapter.avisar(...)`: `POST {engine.base-url}/api/v1/cargas` com `{ "idCarga", "fonte", "produto", "dataBase", "verticesPorCodigo" }`, cabeçalho `X-Correlation-Id`, sem `Authorization`.
- Repetir em erro de rede, 5xx e 409: espera `aviso-espera-inicial` dobrando até `aviso-espera-maxima`, com variação de até 20%, por até `aviso-janela`; ao passar `aviso-alerta`, log `AVISO_ATRASADO` (uma vez); no fim da janela, ou em 4xx diferente de 409, log de erro `CARGA_FALHOU` e `meterRegistry.counter("processor.carga.falhou", "fonte", fonte.name()).increment()`.
- 2xx: logar o corpo devolvido (resultado por curva).

## 9. Log da carga

Um log JSON por carga, no fim do roteiro (sucesso ou erro), com `logstash-logback-encoder` (já no `pom.xml`) via `StructuredArguments.kv`: `fonte`, `dataBase`, `idCarga`, `origem`, `usuario`, `caminhoOriginal`, `codigosGravados` (código → curvas), `codigosIgnorados`, `codigosRejeitados` (código → motivo), `duracaoMs` por etapa (`obter`, `blob`, `interpretar`, `gravar`), `resultado`. Horário de Brasília. Nunca token, segredo ou conteúdo do arquivo.

## 10. ANBIMA (modelo dos provedores)

### 10.1 `AnbimaDownloadSiteProvedor`

- `baixar`: `AnbimaMsClient.baixar(data)` faz `GET` no `anbima-url`; 404 = 503 `ARQUIVO_INDISPONIVEL`; outro status de erro = 502 `FONTE_INDISPONIVEL`; falha de rede ou tempo esgotado = 503 `ARQUIVO_INDISPONIVEL`. Sem `X-Correlation-Id` nem outro cabeçalho interno. Nome do arquivo: `ms{AAMMDD}.txt`. Depois, `dataBase(...)` diferente da data pedida = 503 (passo 2 do roteiro).
- `preparar` (upload): devolve os bytes como vieram, com o nome recebido.
- `dataBase` e `interpretar`: `LeiauteAnbimaMs`.

### 10.2 `LeiauteAnbimaMs` (`application/model/leiaute/`, Java puro)

- Texto `StandardCharsets.ISO_8859_1`, separado por `@`, vírgula decimal.
- Cabeçalho: a primeira linha que começa com `Titulo@`. Colunas localizadas pelo nome: `Titulo`, `Data Referencia`, `Codigo SELIC`, `Data Vencimento`, `Tx. Indicativas`.
- Lê todos os títulos. Descarta os de `Codigo SELIC` na lista `processor.anbima.selic-excluidos` (configuração, hoje `760198`), com log. Não há filtro fixo de `Titulo`: cada grupo de `Titulo` vira um código na fonte, e o `tCurvaPrvdr` decide o que é gravado (seção 7).
- Datas `AAAAMMDD` (`DateTimeFormatter.BASIC_ISO_DATE`). `dataBase(...)` = `Data Referencia` dos títulos (todas iguais, senão 422).
- Taxa: `,` → `.` e `new BigDecimal(texto)`; vazia ou `--` = `null`.
- Prazo: `prazoDiasCorridos = (int) ChronoUnit.DAYS.between(dataBase, vencimento)`, sem ajuste de dia útil e sem calendário (o engine ajusta e conta os dias úteis).
- Código na fonte: o valor de `Titulo` (`verticesPorCodigo` = `{ "NTN-B": [ ... ], "LTN": [ ... ], ... }`). Na seção 7, a consulta ao `tCurvaPrvdr` é uma por grupo: `SELECT cTickerIndcd FROM tCurvaPrvdr WHERE iPrvdrDados = ? AND cPrvdrMercd = ? AND cTickerPrvdr = ?`; grupo sem curva ligada é ignorado, com log.
- 422 `ARQUIVO_INVALIDO`: sem cabeçalho, sem alguma das colunas, sem nenhum título depois dos excluídos, vencimento ilegível.

### 10.3 Vetores reais (`recursos/ms260928.txt` desta change)

Copie `openspec/changes/processor-v0/recursos/ms260928.txt` para `src/test/resources/anbima/ms260928.txt` sem alterar nenhum byte (6.698 bytes, Latin-1).

| Item | Valor esperado |
|---|---|
| `hashArquivo` | `ba9fe9dda8d522b7a298531a12c287314ed1302cf0afe8ce2ae8a6eb7c3ffb85` |
| `idCarga` | `ANBIMA-MS-20260928-ba9fe9dda8d5` |
| `dataBase` | `2026-09-28` |
| títulos | 50 no arquivo, nenhum excluído (não há `760198`); com só a NTN-B cadastrada em `tCurvaPrvdr`, gravam 14 NTN-B (SELIC `760199`) e ficam no log, sem curva ligada, 17 LFT, 12 LTN, 1 NTN-C, 6 NTN-F |
| 1ª NTN-B | vencimento `2027-05-15` (sábado), prazo **229** dias corridos, taxa `5.5415` |
| `2032-08-15` | prazo **2148**, taxa `7.6863` |
| `2040-08-15` | prazo **5070**, taxa `7.4` (BigDecimal `7.4`, não `7.4000`) |
| `2055-05-15` | prazo **10456**, taxa `7.1956` |
| última NTN-B | `2060-08-15`, prazo **12375**, taxa `7.1668` |
| todos os prazos | 229, 687, 960, 1417, 1690, 2148, 2421, 3151, 3882, 5070, 6804, 8722, 10456, 12375 |

## 11. B3 e Bloomberg (depois da pausa)

### 11.1 B3 (`adapter/out/client/b3/` e `application/model/leiaute/LeiauteTaxaSwap`)

- Download (`B3TaxaSwapClient`): `GET` no `b3-url`; 404, corpo vazio ou sem a assinatura de zip (`PK\x03\x04`) = 503 `ARQUIVO_INDISPONIVEL`; outro status de erro = 502.
- Extração: `ZipInputStream` no `.ex_`; dentro, o primeiro nível é outro zip; dentro dele, o único `TaxaSwap.txt`. Mais de dois níveis ou mais de um `TaxaSwap.txt` = 422. O upload aceita o `.ex_` (reconhecido pela assinatura) ou o `TaxaSwap.txt`.
- `LeiauteTaxaSwap` (Java puro; a `processor-carga-b3` usa a mesma classe):

```java
public final class LeiauteTaxaSwap {
    /** Forma canônica: linhas por \r\n|\n|\r, sem linhas vazias ou só com espaços, cada linha como está, junção por \n e \n final, Latin-1.
     *  Caractere fora do Latin-1 → 422. O hash, o idCarga e o Blob usam estes bytes. */
    public static byte[] canonizar(byte[] texto);
    /** Posições 12–19 da primeira linha (AAAAMMDD), data válida; senão 422. */
    public static LocalDate dataBase(byte[] canonico);
    /** verticesPorCodigo: código → List<BtrsCurvaPrimr>; codigosRejeitados: código → "linha N: motivo". */
    public static CargaInterpretada interpretar(byte[] canonico, LocalDate dataBaseEsperada);
}
```

- Regras do `interpretar` (posições 1-based → `substring(inicio - 1, fim)`):
  - `new String(bytes, ISO_8859_1).lines().filter(l -> !l.isBlank())`;
  - arquivo sem linhas, linha com tamanho ≠ 72, data (12–19) diferente entre linhas ou de `dataBaseEsperada`, ou código (22–26, `strip()`) vazio → 422, o arquivo inteiro;
  - por linha: `dc` = 42–46, `du` = 47–51, `sinal` = 52, `taxa` = 53–66. O código é rejeitado (vai para `codigosRejeitados` e para de ser lido) se `dc` ou `du` não são só dígitos, `dc < 1`, `du < 1`, `du > dc`, `sinal` não é `+` nem `-`, `taxa` não é só dígitos, ou `dc` repete no código;
  - `valor = new BigDecimal(taxa).movePointLeft(7)`, com `negate()` se o sinal é `-`; escala 7; nunca `double`.
- `B3DownloadSiteProvedor`: `baixar` = cliente + extração + `canonizar`, nome `TaxaSwap.txt`; `preparar` = extração (se `.ex_`) + `canonizar`; `dataBase` e `interpretar` = `LeiauteTaxaSwap`.
- `INSERT INTO tBtrsCurvaPrimr (cIdtfdUnic, cTickerIndcd, dBaseReft, cDiaCorri, cDiaUtil, vPrecoTx, vFatorAcum, vFatorDia) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)`; `vPrecoTx` recebe o `BigDecimal` de escala 7 sem arredondar.
- Vetores (`docs/TaxaSwap.txt`, copiado para `src/test/resources/b3/`): 30.481 linhas de 72 caracteres, data `20260914`, 114 códigos, 278 vértices em `PRE`, `DCL`, `DPL`, `INP` e `PTX`, nenhum rejeitado; forma canônica com 2.225.113 bytes e SHA-256 `46a249c60bec1ac111d69934b8486213eedecdf9b772246df86a11b5f120d9e8` → `idCarga` `B3-TS-20260914-46a249c60bec`; primeiro vértice da `PRE` = `13.9000000`; a linha `0049060010120260914T1DCL  CUPOM LIMPO - S0000100001-00001179600000F00001` → `DCL`, 1, 1, `-117.9600000`.

### 11.2 Bloomberg (`adapter/out/client/bloomberg/`)

Pedido de histórico (`BloombergDataLicenseClient`; endpoints exatos [A CONFIRMAR] com a documentação da conta):
1. Token OAuth2 com a credencial do Key Vault, em memória até expirar.
2. Identificador: `sofr` + `AAAAMMDD` + 8 primeiros do SHA-256 hexa da lista ordenada de tickers unida por `,` + faixa (`HHmm` de Brasília arredondado para baixo em `faixa-pedido`). Ex.: `sofr20260914a1b2c3d41800`.
3. Criar o pedido (universo = tickers, campo = `campo`, datas = data-base, saída CSV); "identificador já existe" = seguir para o passo 4.
4. Consultar a cada 5 s até `espera`; pronta, baixar. Não pronta = 503 `ARQUIVO_INDISPONIVEL` com o identificador.

Resposta (`LeiauteRespostaDataLicense`, colunas [A CONFIRMAR]): para cada ticker pedido, um vértice com a data-base e valor numérico; faltou algum = 503 no download, 422 no upload, citando os tickers. `INSERT INTO tBbergCurvaPrimr (cIdtfdUnic, cTickerIndcd, dBaseReft, cTickerBberg, vPrecoUlt) VALUES (?, ?, ?, ?, ?)`, com `cTickerBberg` = ticker completo (`VARCHAR(50)`); código na fonte = primeiro termo (`S0490Z`).

Reserva temporária (`TickersSofrReserva`), usada só no `parametros(...)` do `CargaController` (seção 5):

```java
// TODO(retirar): reserva até todas as tarefas do orquestrador mandarem "tickers".
// Para retirar: apagar esta classe e, no CargaController.parametros, trocar o uso por InvalidInputException(PARAMETRO_INVALIDO).
public final class TickersSofrReserva {
    private TickersSofrReserva() {}
    public static final List<String> TICKERS = List.of(
        "S0490Z 1D BLC2 Curncy", "S0490Z 1M BLC2 Curncy", "S0490Z 3M BLC2 Curncy", "S0490Z 6M BLC2 Curncy",
        "S0490Z 9M BLC2 Curncy", "S0490Z 1Y BLC2 Curncy", "S0490Z 15M BLC2 Curncy", "S0490Z 2Y BLC2 Curncy",
        "S0490Z 3Y BLC2 Curncy", "S0490Z 4Y BLC2 Curncy", "S0490Z 5Y BLC2 Curncy", "S0490Z 7Y BLC2 Curncy",
        "S0490Z 10Y BLC2 Curncy", "S0490Z 12Y BLC2 Curncy", "S0490Z 15Y BLC2 Curncy", "S0490Z 20Y BLC2 Curncy",
        "S0490Z 25Y BLC2 Curncy", "S0490Z 30Y BLC2 Curncy", "S0490Z 40Y BLC2 Curncy", "S0490Z 50Y BLC2 Curncy");
}
```


## 12. Testes (poucos e amplos)

Sem dependência nova de teste: JUnit 5, Mockito e Spring Test do `spring-boot-starter-test`, e servidor HTTP falso com `com.sun.net.httpserver.HttpServer` (JDK). **Nenhum teste sobe o contexto do Spring** (`@SpringBootTest`, `@WebMvcTest`): o processor tem Kafka, Redis e JPA configurados, e o contexto não sobe sem eles. Rotas com `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler).addFilters(filtroCorrelacao)`; o resto, objetos montados à mão. Banco e Blob reais ficam para a homologação (7.x); nos testes, eles são falsos em memória (classes de teste que implementam as portas). Em todo teste com data, `Clock.fixed(...)` em `America/Sao_Paulo`.

| Classe de teste | Tipo | Cobre |
|---|---|---|
| `CargaRotasTest` | MockMvc `standaloneSetup` do `CargaController` (com `Clock.fixed`), `CargaArquivoUseCase` mockado | `@ParameterizedTest` com as 3 rotas × 3 fontes chegando ao caso de uso com fonte, data, origem e tickers certos; os 400 (fonte desconhecida; data ausente, inválida, futura; ticker fora da regra; `tickers` com `b3` ou `anbima`; `X-Usuario` ausente); cada exceção → status e corpo `{codigoErro, mensagem, correlationId}` (400, 404, 422, 501, 502, 503); `X-Correlation-Id` recebido e gerado; 11 MB → 422 |
| `CargaArquivoServiceTest` | unitário, portas falsas em memória | `@ParameterizedTest` do roteiro: download ok; 503 sem arquivar; data divergente (503); 422 com original arquivado sob a data pedida; upload 422 sem arquivar; upload com data futura; reprocessamento sem original (404); Blob fora (503, nada gravado); carga sem código mapeado (200, sem aviso); ordem das etapas (Blob antes de interpretar, gravar antes de avisar) |
| `AvisoEngineServiceTest` | unitário, engine falso (`HttpServer`) | 500 → 409 → 200 (3 chamadas, mesmo `idCarga`); 400 → `CARGA_FALHOU` sem repetir; engine fora até a janela → `AVISO_ATRASADO` e `CARGA_FALHOU`; com `aviso-espera-inicial` = 1 ms, `aviso-alerta` = 50 ms e `aviso-janela` = 200 ms (nada de esperar minutos) |
| `AnbimaCargaTest` | montado à mão: `CargaArquivoService` real com o `AnbimaDownloadSiteProvedor` real apontando para uma ANBIMA falsa (`HttpServer`), `AvisoEngineAdapter` real apontando para um engine falso, Blob e banco falsos em memória | ponta a ponta com o `ms260928.txt`: download de `2026-09-28` → 200 com o `idCarga` e `{"NTN-B": 14}`, original no Blob falso, 14 vértices com os prazos e taxas da seção 10.3, engine avisado; download repetido = mesmo `idCarga`; upload do mesmo arquivo = mesmo `idCarga`; reprocessamento não chama a ANBIMA; ANBIMA 404 → 503; ANBIMA 500 → 502 |
| `LeiauteAnbimaMsTest` | unitário | o arquivo real (seção 10.3 inteira) e variações geradas no próprio teste a partir dele: sem cabeçalho, sem a coluna `Tx. Indicativas`, só títulos excluídos, vencimento ilegível (422); taxa `--` e vazia (null); NTN-B Principal `760198` (excluída pela lista); LTN lida como grupo `LTN`; duas `Data Referencia` diferentes (422) |

Depois da pausa, cada provedor acrescenta um `*CargaTest` no molde do `AnbimaCargaTest` e um `Leiaute*Test`. Um teste de rota que já cobre um cenário não precisa de outro teste unitário para o mesmo cenário.

## 13. bff e front (depois da pausa)

- bff: `POST /api/v1/cargas/upload`, sem autenticação nem perfil na v0 e na v1; repassar, com o cliente HTTP que o bff já usa, para `POST {processor}/api/v1/cargas/{fonte em minúsculas}/upload`; `X-Usuario` = nome do usuário do token; não repassar `Authorization`; tempo limite de 120 s.
- Front: o envio fica no bloco "Enviar arquivo da fonte" da tela "Dados de mercado", especificado e implementado pela change `fed-dados-mercado` (guia dela, §3.3); textos e datas em pt-BR (`dd/mm/aaaa`); mensagens de erro vindas do processor.

## 14. Ordem

1. **Base comum (uma pessoa):** seções 1 a 9 (tarefas 1.1 a 1.8). Entregar com as rotas das três fontes respondendo 501.
2. **ANBIMA, provedor modelo (a mesma pessoa):** seção 10 (tarefas 3.1 e 3.2). Entregar com as rotas da ANBIMA gravando e B3 e Bloomberg ainda respondendo 501.
3. **PAUSA (tarefa 3.3):** rodar `mvn compile` e `mvn test` (os testes da seção 12), escrever o resumo e parar até a revisão. Não começar a B3, a Bloomberg nem o bff e o front antes disso.
4. **Provedores restantes, em paralelo (um dev cada), copiando a estrutura da ANBIMA:** B3 (11.1; tarefa 2.1) e Bloomberg (11.2; 4.1 e 4.2).
5. **bff e front (em paralelo, depois da pausa):** seção 13 (5.1 e 5.2).
6. Fechamento (6.x) e homologação (7.x).
