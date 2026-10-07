# Guia de implementação: curves-v1-1 (dá para fazer na mão)

> **Jackson 3 (Spring Boot 4):** para injetar o mapper do Spring, use `tools.jackson.databind.ObjectMapper` (ou `tools.jackson.databind.json.JsonMapper`), com `tools.jackson.core.type.TypeReference` e `JsonNode` de `tools.jackson.databind`. **Nunca** `com.fasterxml.jackson.databind.ObjectMapper`/`JsonMapper`: o Boot 4 não cria esse bean, e a aplicação não sobe ("required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"). Só as anotações continuam em `com.fasterxml.jackson.annotation`. No Jackson 3, `asText()` virou `asString()`.

> **Parâmetros de rota em objeto:** controller não recebe uma fila de `@RequestParam`. Com mais de dois parâmetros, agrupe num record: `@ModelAttribute FiltroX filtro` para consultas `GET` (o Spring preenche os campos pelos nomes da query) e `@RequestBody PedidoX pedido` para comandos `POST`/`PUT`. `@PathVariable` (identificadores como `codigo` e `dataBase`) continua separado.

> **Tipagem forte (Java 21):** dentro do domínio e das portas, nada de `Map<String, Object>`, `Object[]`, `Object` genérico ou `String` com JSON dentro. Valores fechados viram `enum`; dados viram `record`; variantes viram `sealed interface` com records. JSON cru só na borda (controller, cliente HTTP, coluna `cModDado`), desserializado direto num record; consultas nativas devolvem projeção em record.

A spec manda no comportamento; este guia dá o código. Linhas citadas são as do relatório de inspeção (`docs/inspecao/inspecao-curves.md` do poc); confira no código. Use os nomes reais de classes, atributos e pacotes do projeto onde o guia usa um nome de exemplo.

**Código já existe:** a v1 está aplicada. Se algo já existe e cumpre, deixe; se diverge, altere só o que diverge.

## 0. Ordem sugerida

1. `dono` (§2.1): 15 minutos.
2. Listagem (§2.2): a consulta é a única parte com cuidado.
3. Repasse ao engine (§1): arquivos novos, quase todo o código está aqui.

## 1. Repasse ao engine

### 1.1 Porta e resposta (`application/port/out/EnginePort.java`)

```java
public interface EnginePort {
    RespostaEngine construir(String codigo, LocalDate dataBase, Boolean forcarRecalculo, String fonte, String produto, String correlationId);
    RespostaEngine regravarInterpolada(String codigo, LocalDate dataBase, String correlationId);
    RespostaEngine consultarVertices(String codigo, LocalDate dataBase, String correlationId);
    RespostaEngine interpolar(String codigo, LocalDate dataBase, String queryString, String correlationId);

    record RespostaEngine(int status, String corpoJson) {}
}
```

### 1.2 Cliente HTTP (`adapter/out/client/engine/EngineHttpClient.java`)

```java
@Component
public class EngineHttpClient implements EnginePort {

    private static final Duration CONSTRUCAO = Duration.ofSeconds(120);
    private static final Duration REGRAVACAO = Duration.ofSeconds(60);
    private static final Duration CONSULTA = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;

    public EngineHttpClient(@Value("${curves.engine.url}") String baseUrl) {   // sem valor padrão: sem a variável, não sobe
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public RespostaEngine construir(String codigo, LocalDate dataBase, Boolean forcarRecalculo, String fonte, String produto, String correlationId) {
        var q = new StringJoiner("&", "?", "").setEmptyValue("");
        if (forcarRecalculo != null) q.add("forcarRecalculo=" + forcarRecalculo);
        if (fonte != null) q.add("fonte=" + enc(fonte));
        if (produto != null) q.add("produto=" + enc(produto));
        return enviar("POST", caminho(codigo, dataBase) + "/construcao" + q, CONSTRUCAO, correlationId);
    }

    @Override
    public RespostaEngine regravarInterpolada(String codigo, LocalDate dataBase, String correlationId) {
        return enviar("POST", caminho(codigo, dataBase) + "/interpolada", REGRAVACAO, correlationId);
    }

    @Override
    public RespostaEngine consultarVertices(String codigo, LocalDate dataBase, String correlationId) {
        return enviar("GET", caminho(codigo, dataBase), CONSULTA, correlationId);
    }

    @Override
    public RespostaEngine interpolar(String codigo, LocalDate dataBase, String queryString, String correlationId) {
        return enviar("GET", caminho(codigo, dataBase) + "/interpolacao?" + queryString, CONSULTA, correlationId);
    }

    private String caminho(String codigo, LocalDate dataBase) {
        return "/api/v1/curvas/" + enc(codigo) + "/" + dataBase;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private RespostaEngine enviar(String metodo, String caminho, Duration limite, String correlationId) {
        var req = HttpRequest.newBuilder(URI.create(baseUrl + caminho))
                .timeout(limite)
                .header("Accept", "application/json")
                .header("X-Correlation-Id", correlationId)
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            var r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 500) throw new EngineIndisponivelException();
            return new RespostaEngine(r.statusCode(), r.body());
        } catch (IOException e) {                       // inclui HttpTimeoutException e conexão recusada
            throw new EngineIndisponivelException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineIndisponivelException();
        }
    }
}
```

`application.yml`:

```yaml
curves:
  engine:
    url: ${CURVES_ENGINE_URL}
```

### 1.3 Erros

- **`ENGINE_INDISPONIVEL`:** constante nova no enum de códigos de erro da curves (o mesmo usado pelo `ApplicationExceptionHandler`), com status 503 e texto em `messages.properties`: "O engine não respondeu. Tente de novo em instantes." `EngineIndisponivelException` estende a exceção base do projeto (`application/exception/BaseException` ou a que o tratador já trata) com esse código.
- **Erro de negócio do engine (4xx):** o adaptador não lê o corpo do erro. Qualquer 4xx vira `EngineErroException(status, corpo)`, e o tratador devolve o corpo do engine como veio (Problem Details), com o mesmo status:

```java
public class EngineErroException extends RuntimeException {
    private final int status;
    public EngineErroException(int status, String corpo) {
        super(corpo);
        this.status = status;
    }
    public int status() { return status; }
}
```

No `EngineHttpClient.enviar`, depois do `if (r.statusCode() >= 500)`:

```java
if (r.statusCode() >= 400) {
    throw new EngineErroException(r.statusCode(), r.body());
}
```

No `ApplicationExceptionHandler`:

```java
@ExceptionHandler(EngineErroException.class)
public ResponseEntity<String> handleException(EngineErroException ex) {
    return ResponseEntity.status(ex.status())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ex.getMessage());
}
```

### 1.4 Serviço (`application/service/CurvaMercadoAcoesService.java`)

```java
@Service
@RequiredArgsConstructor
public class CurvaMercadoAcoesService {

    private final CurvaMercdRepositoryPort curvas;     // a porta que o CRUD da curva já usa
    private final EnginePort engine;

    public RespostaEngine construir(String codigo, LocalDate dataBase, Boolean forcar, String fonte, String produto, String cid) {
        exigirCurva(codigo);
        if ((fonte == null) != (produto == null)) throw parametroInvalido("Informe fonte e produto juntos.");
        return engine.construir(codigo, dataBase, forcar, fonte, produto, cid);
    }

    public RespostaEngine regravarInterpolada(String codigo, LocalDate dataBase, String cid) {
        exigirCurva(codigo);
        return engine.regravarInterpolada(codigo, dataBase, cid);
    }

    public RespostaEngine consultarVertices(String codigo, LocalDate dataBase, String cid) {
        exigirCurva(codigo);
        return engine.consultarVertices(codigo, dataBase, cid);
    }

    public RespostaEngine interpolar(String codigo, LocalDate dataBase, String queryString, String cid) {
        exigirCurva(codigo);
        if (queryString == null || !(queryString.contains("du=") || queryString.contains("data=")))
            throw parametroInvalido("Informe ao menos um du ou uma data.");
        return engine.interpolar(codigo, dataBase, queryString, cid);
    }

    private void exigirCurva(String codigo) {
        // use o método que o CRUD já tem para buscar por código; inexistente → a exceção de NAO_ENCONTRADO (404) que ele já lança
    }

    private RuntimeException parametroInvalido(String msg) {
        // a exceção do projeto com o código PARAMETRO_INVALIDO (400)
    }
}
```

### 1.5 Controller (`adapter/in/api/rest/controller/CurvaMercadoAcoesController.java`)

```java
@RestController
@RequestMapping("/api/v1/curvas-mercado/{codigo}/{dataBase}")
@RequiredArgsConstructor
@Tag(name = "Ações da curva", description = "Repasse ao engine para a tela Curvas")
public class CurvaMercadoAcoesController {

    private final CurvaMercadoAcoesService service;

    @PostMapping("/construcao")
    public ResponseEntity<String> construir(@PathVariable String codigo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @RequestParam(required = false) Boolean forcarRecalculo,
            @RequestParam(required = false) String fonte,
            @RequestParam(required = false) String produto) {
        return repassar(service.construir(codigo, dataBase, forcarRecalculo, fonte, produto, cid()));
    }

    @PostMapping("/interpolada")
    public ResponseEntity<String> regravar(@PathVariable String codigo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return repassar(service.regravarInterpolada(codigo, dataBase, cid()));
    }

    @GetMapping("/vertices")
    public ResponseEntity<String> vertices(@PathVariable String codigo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return repassar(service.consultarVertices(codigo, dataBase, cid()));
    }

    @GetMapping("/interpolacao")
    public ResponseEntity<String> interpolar(@PathVariable String codigo,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            HttpServletRequest request) {
        return repassar(service.interpolar(codigo, dataBase, request.getQueryString(), cid()));   // query string intacta
    }

    private ResponseEntity<String> repassar(RespostaEngine r) {
        return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).body(r.corpoJson());
    }

    private static String cid() {
        String c = MDC.get("correlationId");                 // se o projeto tiver filtro de correlação
        return c != null ? c : UUID.randomUUID().toString();
    }
}
```

## 2. Dono e listagem

### 2.1 `dono` ↔ `cPprioDado`

- `CurvaMercdEntity.java:62-63`: o atributo já existe.
- `CurvaMercdPersistenceAdapter.java:96-105` (entidade → domínio) e `:119-137` (domínio → entidade): copiar o campo nos dois sentidos.
- Domínio da curva, request de criação/alteração e `CurvaMercadoResponse.java:9-22`: acrescentar `String dono` (opcional, até 50 caracteres; validação como a de `classificacao`).

### 2.2 Listagem

**Parâmetros** em `CurvaMercadoController.java:35-58`: `@RequestParam(required = false) String provedor` e `String dono`, repassados ao caso de uso como os filtros que já existem.

**Filtros na consulta da listagem** (no mesmo lugar onde nome, código, unidade e situação já são filtrados; se for JPQL, use os nomes dos atributos reais das entidades):

```sql
-- provedor: só curvas que têm esse provedor em tCurvaPrvdr
AND (:provedor IS NULL OR EXISTS (
      SELECT 1 FROM tCurvaPrvdr p
       WHERE p.cTickerIndcd = c.cTickerIndcd
         AND RTRIM(p.iPrvdrDados) = :provedor))
-- dono: trecho, normalizado como o filtro de nome já faz
AND (:dono IS NULL OR <normalizar>(c.cPprioDado) LIKE '%' + <normalizar>(:dono) + '%')
```

**Itens da página.** Depois de buscar a página, uma consulta só para os provedores de todas as curvas dela (em `CurvaPrvdrRepository`, ao lado da `:25-26`):

```java
@Query(value = """
        SELECT RTRIM(cTickerIndcd) AS nome, RTRIM(iPrvdrDados) AS provedor
          FROM tCurvaPrvdr
         WHERE cTickerIndcd IN (:nomes)
         ORDER BY cTickerIndcd, cPriorCsumo
        """, nativeQuery = true)
List<Object[]> provedoresDasCurvas(@Param("nomes") Collection<String> nomes);
```

```java
Map<String, List<String>> provedoresPorCurva = new HashMap<>();
for (Object[] l : repo.provedoresDasCurvas(nomesDaPagina)) {
    var lista = provedoresPorCurva.computeIfAbsent((String) l[0], k -> new ArrayList<>());
    if (!lista.contains((String) l[1])) lista.add((String) l[1]);   // sem repetição, na ordem de prioridade
}
```

**Campos novos no item** (`CurvaMercadoResponse` ou o DTO do item da listagem):

```java
public record UltimaExecucao(@JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataBase, String usuario) {}

// no item:
List<String> provedores,      // provedoresPorCurva.getOrDefault(nome, List.of())
String dono,                  // cPprioDado
UltimaExecucao ultimaExecucao // dBaseReft == null ? null : new UltimaExecucao(dBaseReft, cUsuarCalc)
```

`dBaseReft` e `cUsuarCalc` continuam só leitura na entidade (`insertable = false, updatable = false`). O envelope da página (`CurvasMercadoPaginadaResponse`, `totalElementos`, `totalPaginas`) fica como está.

### 2.3 Interpoladores

No `ValidadorParametros` da curves (`:92-97` e as regras de combinação) e na lista de `/valores`: tirar `LogLinear` e a regra "`FlatForward` só com `Discount`", se existirem. O engine já não tem `LogLinear`, e o `FlatForward` é a interpolação log-linear em qualquer base (as curvas `INP` e `PTX` usam `Price` + `FlatForward`).

## 3. Conferir

| O quê | Como |
|---|---|
| Repasse | no Swagger da curves, `POST /api/v1/curvas-mercado/PRE/{data}/construcao?forcarRecalculo=true` devolve o JSON do engine; `fonte=B3` sem `produto` → 400; código `XYZ` → 404; com o engine parado → 503 `ENGINE_INDISPONIVEL` |
| Erro do engine | uma data sem bruto → 422 com `code` = `INSUMO_AUSENTE` no formato da curves |
| Dono | criar e consultar uma curva com `dono` |
| Listagem | `GET /api/v1/curvas-mercado?provedor=B3` traz só curvas da B3, cada item com `provedores`, `dono` e `ultimaExecucao` |
| Teste | `CurvaMercadoAcoesServiceTest` com a porta simulada: 404, os dois 400, repasse do status e 503 |
