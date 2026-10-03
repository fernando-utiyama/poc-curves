# Guia de implementação: orquestrador v0, disparo manual

A spec (`specs/disparo-manual-carga/spec.md`) manda no comportamento; este guia diz **onde, com que nome e como**. Ele foi escrito para ser suficiente sozinho: o que você precisaria buscar no engine, na v1 (`orquestrador-curvas`) ou no processor já está copiado aqui.

## 0. Antes de começar (leia isto primeiro)

### 0.0 Como gastar pouco

Cada fase é feita com **um prompt só**, sem ida e volta: não faça perguntas, decida pelo guia e, se faltar algo, deixe `// TODO(revisao): <dúvida>` e siga. Trabalhe tarefa por tarefa pelo cartão dela (seção 0.4): abra só os arquivos do cartão e leia só as seções que ele cita. A spec é para consulta em caso de dúvida: tudo o que o código precisa dela já está neste guia. Não rode a aplicação; compile e rode os testes. Para tarefas mecânicas (cartões com código pronto), um modelo de custo menor costuma bastar; confira o multiplicador do modelo na sua conta.

### 0.1 O que ler e o que não ler

Leia **só**:
1. este guia (as seções do cartão da tarefa);
2. `tasks.md` (só para marcar o que terminou);
3. a spec (`specs/disparo-manual-carga/spec.md`) só em caso de dúvida;
4. do `services/orchestrator` (caminhos a partir do pacote raiz; no poc, `br.com.poc`), só os arquivos que o cartão manda alterar; o que você precisa saber deles já está na seção 0.5. São estes:
   - `Application.java`, `src/main/resources/application.yml`;
   - `adapter/out/persistence/jpa/mapper/TarefaJpaMapper.java`;
   - `adapter/out/action/HttpTaskActionAdapter.java` e `application/port/out/TaskActionPort.java`;
   - `application/port/out/TaskRepositoryPort.java` e `adapter/out/persistence/jpa/boundary/TaskJpaPersistenceAdapter.java`;
   - `application/port/in/SchedulerUseCase.java`, `application/service/SchedulerService.java` (só `executeTask`, `executarTarefa` e `prepararNovoCicloParaExecucaoManual`);
   - `adapter/in/api/rest/controller/SchedulerAPI.java` e `SchedulerController.java` (só `executar`);
   - `application/exception/BaseException.java`, `BusinessException.java`, `InvalidInputException.java` (assinatura dos construtores) e `adapter/in/api/rest/exception/handler/ApplicationExceptionHandler.java`;
   - `adapter/infrastructure/scheduler/SpringSchedulerAdapter.java` e `application/service/TarefaCrudService.java` (só as constantes de fuso).

**Não** abra: `proposal.md` e `design.md` desta change, outras changes (inclusive `orquestrador-curvas` e `engine-construcao-curvas`: o que importa delas está aqui), outros serviços, `TarefaService`, webhook (`adapter/out/client/feign/`), Kafka, `scheduler.*` e os DTOs que não aparecem neste guia.

Se algo não estiver neste guia nem na spec, **não procure no repositório**: siga o padrão mais simples do Java 21, deixe `// TODO(revisao): <dúvida>` e registre a dúvida no resumo da pausa.

### 0.2 Regras de código

- Java 21 nativo (`java.time`, records, sealed, `switch` com pattern matching, `URLEncoder`, `java.net.http.HttpClient`); sem Lombok nem MapStruct no código novo.
- Nenhuma dependência nova, nem de teste.
- Spring Boot 4 e Jackson 3: JSON com o pacote `tools.jackson` (nunca `com.fasterxml.jackson`), pelo mapper que o Spring já injeta.
- Arquivos existentes só recebem o que este guia manda; o resto do `SchedulerService`, do `TarefaService` e do agendamento fica como está até a v1.
- Não crie tabela, coluna, tópico, rota ou código de erro fora deste guia e da spec.

### 0.3 Prompt sugerido para o agente

Fase 1:

> Implemente as tarefas 1.1 a 1.6 de `openspec/changes/orquestrador-v0-disparo-manual/tasks.md`, seguindo `implementacao.md` da mesma pasta. Leia só os arquivos da seção 0.1 do guia; não explore o resto do repositório. Ao terminar cada tarefa, rode `mvn -q compile` e corrija; ao fim, rode `mvn -q test` e corrija até passar. Pare na tarefa 1.6 e escreva o resumo pedido nela.

Fase 2 (depois da revisão): o mesmo com as tarefas 2.1 e 2.2; bff e front (2.3 e 2.4) num prompt separado.

### 0.4 Cartões por tarefa

| Tarefa | Criar | Alterar | Ler no guia | Pronto quando |
|---|---|---|---|---|
| 1.1 | `ApplicationFusoTest` | `Application`, `application.yml` (uma linha), `SpringSchedulerAdapter`, `TarefaCrudService` | 2 | `mvn -q compile` e o teste passa |
| 1.2 | `TarefaJpaMapperTest` | `TarefaJpaMapper` | 3 | o teste passa |
| 1.3 | `adapter/out/client/feign/config/DestinosProperties`, `adapter/out/client/feign/ChamadaSaidaClient`, `adapter/out/client/feign/boundary/RestClientChamadaSaidaClient`, `application/exception/ExecucaoErrorCode` e `ConflictException`; `ChamadaSaidaClientTest` | `HttpTaskActionAdapter`, `ApplicationExceptionHandler` (1 método), `InvalidInputException` (1 construtor), `application.yml` (destinos) | 4, 0.5 | o teste passa e os testes existentes da action `http` foram ajustados |
| 1.4 | — | `TaskRepositoryPort` (3 métodos), `TaskJpaPersistenceAdapter` (3 métodos e o `EntityManager` no construtor) | 5, 0.5 | `mvn -q compile` |
| 1.5 | `domain/calendario/Calendario`, `Brazil`, `UnitedStates` e `Calendarios` (código pronto); `CalendariosTest` | — | 6 | o teste passa |
| 1.6 | resumo da pausa | — | 7 | resumo escrito; parar |
| 2.1 | `application/port/out/ExecucaoManualActionPort`, `application/model/scheduler/ResultadoExecucao` e `TipoResultado`, `adapter/out/action/CargaFonteTaskActionAdapter`; `CargaFonteTaskActionAdapterTest` | — | 8, 0.5 | o teste passa |
| 2.2 | `adapter/in/api/rest/dto/scheduler/ExecucaoManualResponseDto`; `ExecucaoManualServiceTest` e `SchedulerExecutarRotaTest` | `SchedulerUseCase`, `SchedulerService` (campo novo e `executeTask`), `SchedulerAPI`, `SchedulerController` | 9, 0.5 | `mvn -q test` |
| 2.3 e 2.4 | rota do bff e tela do front | — | 10 | testes do bff e conferência no navegador |

### 0.5 O que já existe (copiado do código, para não precisar abrir)

```java
// application/exception/BaseException: já tem construtores com ErrorCode: (ErrorCode), (ErrorCode, Object[]), (ErrorCode, Throwable).
// BusinessException(ErrorCode) já existe.
// InvalidInputException só tem (String origin, String method, String message[, Throwable]), (Throwable) e (): acrescentar
public InvalidInputException(ErrorCode errorCode) { super(errorCode); }

// ApplicationExceptionHandler: um método por exceção, todos neste formato
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Object> handleException(BusinessException ex, WebRequest request) {
    return handleBaseException(ex, HttpStatus.UNPROCESSABLE_ENTITY, request);
}

// application/port/out/TaskActionPort
public interface TaskActionPort {
    String getActionName();
    default List<String> getSupportedActionNames() { return List.of(getActionName()); }
    void execute(Tarefa tarefa);
}

// application/service/TaskActionExecutor: construtor (List<TaskActionPort>), mapa por getSupportedActionNames()
// normalizado (sem espaços, minúsculas); execute(Tarefa) lança InvalidInputException para action ausente ou não suportada.

// application/port/out/TaskRepositoryPort
Tarefa save(Tarefa tarefa); List<Tarefa> findAllTarefas(); List<Tarefa> findAllTarefasResumidas();
List<Tarefa> findPersistedScheduledTarefas(); Optional<Tarefa> findTarefaById(Long id);
Optional<Tarefa> findTarefaByNome(String nome); void deleteTarefaById(Long id); Optional<Tarefa> buscarPorId(Long id);

// adapter/out/persistence/jpa/boundary/TaskJpaPersistenceAdapter (@Component)
public TaskJpaPersistenceAdapter(TarefaJpaRepository tarefaJpaRepository, TarefaJpaMapper tarefaJpaMapper)   // acrescentar EntityManager

// application/port/in/SchedulerUseCase: void executeTask(Long tarefaId);   // passa à assinatura da seção 9

// application/service/SchedulerService: @Service @AllArgsConstructor; campos final:
//   TaskRepositoryPort taskRepositoryPort, SchedulerPort schedulerPort, TaskActionExecutor taskActionExecutor,
//   TarefaStatusTransitionService transitionService
//   acrescentar: private final List<ExecucaoManualActionPort> execucaoManualActions;
//   método privado existente: Tarefa buscarPorId(Long id)  (NotFoundException se não existe)

// application/model/scheduler/Tarefa (@Data): Long id; String nome, descricao, action, regraCron, regraIntervalo, status;
//   List<ParametroTarefa> parametros; List<LogTarefa> logs; LocalDateTime dataCriacao, dataAtualizacao; ...
// application/model/scheduler/ParametroTarefa: String nome, valor (getNome(), getValor())

// adapter/in/api/rest/controller/SchedulerAPI (hoje)
@PostMapping("/tarefas/{id}/executar")
ResponseEntity<ApiMessageDto> executar(@PathVariable Long id);

// Tabelas: tTrefaAgnda (cIdtfdTrefa, cAcaoOperSist, cSit, rTrefa, ...); tLogTrefa (cIdtfdEntrd identidade,
//   cIdtfdTrefa, dAtaCriac, cSitExcuc, rLogTrefa VARCHAR(MAX))
// Spring Boot 4.1 (Jackson 3, pacote tools.jackson)
```

## 1. Onde fica cada coisa

```
Application.java                                   fixarFuso() no main                                 (1.1)
adapter/out/persistence/jpa/mapper/                TarefaJpaMapper (corrigir)                          (1.2)
adapter/out/client/feign/config/                   DestinosProperties                                  (1.3)
adapter/out/client/feign/                          ChamadaSaidaClient (interface)                      (1.3)
adapter/out/client/feign/boundary/                 RestClientChamadaSaidaClient                        (1.3)
adapter/out/action/                                HttpTaskActionAdapter (alterar)                     (1.3)
application/exception/                             ConflictException (nova), ExecucaoErrorCode (novo)  (1.3/1.4)
application/port/out/                              TaskRepositoryPort (+ reivindicar, devolver, registrarLog) (1.4)
adapter/out/persistence/jpa/boundary/              TaskJpaPersistenceAdapter (+ os três métodos)       (1.4)
domain/calendario/                                 Calendario, Brazil, UnitedStates, Calendarios       (1.5)
application/port/out/                              ExecucaoManualActionPort                            (2.1)
application/model/scheduler/                       ResultadoExecucao, TipoResultado                    (2.1)
adapter/out/action/                                CargaFonteTaskActionAdapter                         (2.1)
application/port/in/                               SchedulerUseCase.executeTask (nova assinatura)      (2.2)
application/service/                               SchedulerService.executeTask (reescrito)            (2.2)
adapter/in/api/rest/controller/                    SchedulerAPI/SchedulerController.executar           (2.2)
adapter/in/api/rest/dto/scheduler/                 ExecucaoManualResponseDto                           (2.2)
```

## 2. Fuso (tarefa 1.1)

```java
public static void main(String[] args) {
    fixarFuso();
    SpringApplication.run(Application.class, args);
}

static void fixarFuso() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    if (!ZoneId.systemDefault().getId().equals("America/Sao_Paulo"))
        throw new IllegalStateException("Fuso da JVM diferente de America/Sao_Paulo");
}
```

- `application.yml`: remover só a linha `spring.jackson.time-zone: UTC`.
- Remover `APP_ZONE` do `SpringSchedulerAdapter` (usar `new CronTrigger(regra)` e `LocalDateTime.now()`) e `UTC_3`/`APP_ZONE` do `TarefaCrudService` (usar `LocalDateTime.now()`).

## 3. Mapper (tarefa 1.2)

No `TarefaJpaMapper`, os dois sentidos estão trocados nos três métodos (linhas com `setDescricao(entity.getAcaoOperSistema())` e `setAcaoOperSistema(tarefa.getDescricao())`). Deve ficar:

```java
tarefa.setAction(entity.getAcaoOperSistema());      // cAcaoOperSist
tarefa.setDescricao(entity.getDescricao());         // rTrefa
entity.setAcaoOperSistema(tarefa.getAction());
entity.setDescricao(tarefa.getDescricao());
```

## 4. Destinos e chamada de saída (tarefa 1.3)

```yaml
orquestrador:
  http:
    destinos:
      conector-b3:        { base-url: ${DESTINO_CONECTOR_B3_URL},        timeout-segundos: 120 }
      conector-anbima:    { base-url: ${DESTINO_CONECTOR_ANBIMA_URL},    timeout-segundos: 120 }
      conector-bloomberg: { base-url: ${DESTINO_CONECTOR_BLOOMBERG_URL}, timeout-segundos: 120 }
      engine:             { base-url: ${DESTINO_ENGINE_URL},             timeout-segundos: 30 }   # usado pela v1
```

Na v0, as três `DESTINO_CONECTOR_*` são a base-URL do `services/processor`.

```java
@ConfigurationProperties("orquestrador.http")
public record DestinosProperties(Map<String, Destino> destinos) {
    public record Destino(URI baseUrl, Integer timeoutSegundos) {}   // timeoutSegundos ausente = 120
}

public interface ChamadaSaidaClient {
    /** Não lança exceção por status HTTP; lança ResourceAccessException em rede ou tempo esgotado. */
    ResponseEntity<String> chamar(HttpMethod metodo, String destino, String caminho, String correlationId);
}
```

`RestClientChamadaSaidaClient`:
- na subida, um `RestClient` por destino: `RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica)` com `JdkClientHttpRequestFactory(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(timeoutSegundos)).build())` e `setReadTimeout(Duration.ofSeconds(timeoutSegundos))`. Sem seguir redirecionamento: um 3xx volta como resposta e vira `ERRO`;
- destino ausente do mapa → `BusinessException(ExecucaoErrorCode.DESTINO_NAO_CADASTRADO)`, sem chamada;
- caminho que não começa com `/`, ou contém `://`, `//`, `..`, `\` ou `@` → `BusinessException(ExecucaoErrorCode.CAMINHO_INVALIDO)`, sem chamada;
- cabeçalho só `X-Correlation-Id`; nunca `Authorization` nem outro;
- `.retrieve().onStatus(s -> true, (req, res) -> {}).toEntity(String.class)`: quem classifica o status é quem chamou.

`HttpTaskActionAdapter`: parâmetros `destino`, `caminho` e `method` (opcional, padrão `GET`); remover `url`, `header.*`, `body` e `contentType`; chamar pelo `ChamadaSaidaClient` com um `UUID` novo de correlação; não 2xx continua lançando a mesma exceção de hoje.

Códigos novos (no molde do `BusinessErrorCode`):

```java
public enum ExecucaoErrorCode implements ErrorCode {
    PARAMETRO_INVALIDO("Parâmetro inválido"),
    DESTINO_NAO_CADASTRADO("Destino não cadastrado em orquestrador.http.destinos"),
    CAMINHO_INVALIDO("Caminho de chamada inválido"),
    TAREFA_EM_EXECUCAO("A tarefa já está em execução");
    // code = name(); getCode()/getMessage() como no BusinessErrorCode
}
```

`ConflictException` (nova, no molde da `BusinessException`, com o construtor `(ErrorCode)`) e, no `ApplicationExceptionHandler`, um método `@ExceptionHandler(ConflictException.class)` → `handleBaseException(ex, HttpStatus.CONFLICT, request)`. Se a `InvalidInputException` não tiver construtor com `ErrorCode`, acrescentar `InvalidInputException(ErrorCode errorCode)` chamando o `super(errorCode)` da `BaseException`, sem mexer nos outros.

## 5. Reivindicação (tarefa 1.4)

Três métodos novos no `TaskRepositoryPort`, implementados no `TaskJpaPersistenceAdapter` com SQL nativo (`EntityManager.createNativeQuery`), cada um na sua transação curta (`@Transactional(propagation = REQUIRES_NEW)`):

```java
/** Devolve a situação de origem. 409 se EXECUTANDO ou se outra instância passou na frente; 400 se DESABILITADA/REMOVIDA. */
String reivindicar(Long id, String jsonInicio);
void devolver(Long id, String origem);
void registrarLog(Long id, int codigo, String json);
```

```sql
-- reivindicar
SELECT RTRIM(cSit) FROM tTrefaAgnda WHERE cIdtfdTrefa = ?;                       -- nenhuma linha: NotFoundException
UPDATE tTrefaAgnda SET cSit = 'EXECUTANDO' WHERE cIdtfdTrefa = ? AND RTRIM(cSit) = ?;   -- ? = origem lida; 0 linhas → 409
INSERT INTO tLogTrefa (cIdtfdTrefa, dAtaCriac, cSitExcuc, rLogTrefa) VALUES (?, ?, 102, ?);   -- jsonInicio

-- devolver
UPDATE tTrefaAgnda SET cSit = ? WHERE cIdtfdTrefa = ? AND RTRIM(cSit) = 'EXECUTANDO';

-- registrarLog
INSERT INTO tLogTrefa (cIdtfdTrefa, dAtaCriac, cSitExcuc, rLogTrefa) VALUES (?, ?, ?, ?);
```

- Origem `EXECUTANDO` → `ConflictException(TAREFA_EM_EXECUCAO)` sem tentar o `UPDATE`; `UPDATE` com 0 linhas → a mesma; `DESABILITADA` ou `REMOVIDA` → `InvalidInputException(PARAMETRO_INVALIDO)`.
- `cIdtfdEntrd` é identidade (não entra no `INSERT`); `dAtaCriac` = `LocalDateTime.now()`.
- `jsonInicio` = `{ "origem", "dataBase", "usuario", "instancia", "correlationId" }`, com `instancia` = `System.getenv("HOSTNAME")` ou, ausente, `InetAddress.getLocalHost().getHostName()`. Monte os JSON com o `ObjectMapper` do Spring.
- Não use o `TarefaStatusTransitionService` aqui: a volta de `EXECUTANDO` para a origem é feita só pelo `UPDATE`.

## 6. Calendários (tarefa 1.5)

Cópia da regra do engine (mesmas datas); o orquestrador só precisa saber se o dia é útil.

```java
public abstract class Calendario {
    private final ConcurrentHashMap<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();
    protected abstract Set<LocalDate> feriados(int ano);
    public boolean isBusinessDay(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> !cache.computeIfAbsent(d.getYear(), a -> Set.copyOf(feriados(a))).contains(d);
        };
    }
}

public class Brazil extends Calendario {          // Settlement
    protected Set<LocalDate> feriados(int y) {
        var p = pascoa(y);
        var s = new HashSet<>(List.of(LocalDate.of(y, 1, 1), p.minusDays(48), p.minusDays(47), p.minusDays(2),
            LocalDate.of(y, 4, 21), LocalDate.of(y, 5, 1), p.plusDays(60), LocalDate.of(y, 9, 7),
            LocalDate.of(y, 10, 12), LocalDate.of(y, 11, 2), LocalDate.of(y, 11, 15), LocalDate.of(y, 12, 25)));
        if (y >= 2024) s.add(LocalDate.of(y, 11, 20));
        return s;
    }
    static LocalDate pascoa(int y) {   // Meeus/Jones/Butcher
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451, x = h + l - 7 * m + 114;
        return LocalDate.of(y, x / 31, (x % 31) + 1);
    }
}

public class UnitedStates extends Calendario {    // FederalReserve
    protected Set<LocalDate> feriados(int y) {
        var s = new HashSet<LocalDate>();
        Stream.of(LocalDate.of(y, 1, 1), LocalDate.of(y, 7, 4), LocalDate.of(y, 11, 11), LocalDate.of(y, 12, 25))
            .flatMap(f -> observado(f).stream()).forEach(s::add);
        if (y >= 2022) observado(LocalDate.of(y, 6, 19)).ifPresent(s::add);
        if (y >= 1998) s.add(LocalDate.of(y, 1, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY)));
        s.add(LocalDate.of(y, 2, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY)));
        s.add(LocalDate.of(y, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)));
        s.add(LocalDate.of(y, 9, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)));
        s.add(LocalDate.of(y, 10, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY)));
        s.add(LocalDate.of(y, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)));
        return s;
    }
    private static Optional<LocalDate> observado(LocalDate f) {   // domingo → segunda; sábado → não observado
        return switch (f.getDayOfWeek()) {
            case SUNDAY -> Optional.of(f.plusDays(1));
            case SATURDAY -> Optional.empty();
            default -> Optional.of(f);
        };
    }
}
```

`Calendarios` (classe utilitária final, sem bean):

```java
public final class Calendarios {
    private static final Map<String, Calendario> POR_NOME =
        Map.of("Brazil/Settlement", new Brazil(), "UnitedStates/FederalReserve", new UnitedStates());

    /** "Brazil/Settlement,UnitedStates/FederalReserve"; nulo ou vazio = Brazil/Settlement; desconhecido = PARAMETRO_INVALIDO. */
    public static List<Calendario> de(String parametro) { ... }

    public static LocalDate dataBasePadrao(LocalDate hoje, int defasagem, List<Calendario> calendarios) {
        LocalDate d = hoje;
        for (int restantes = defasagem; restantes > 0; ) {
            d = d.minusDays(1);
            LocalDate x = d;
            if (calendarios.stream().allMatch(c -> c.isBusinessDay(x))) restantes--;
        }
        return d;
    }
}
```

Vetores (a sincronização de feriados decretados é da v1):

| Item | Esperado |
|---|---|
| Páscoa 2026 / 2027 | `04-05` (Carnaval `02-16`/`02-17`, Sexta-feira Santa `04-03`, Corpus Christi `06-04`) / `03-28` (Carnaval `02-08`/`02-09`, Sexta `03-26`, Corpus `05-27`) |
| `UnitedStates` 2026 | `01-01`, `01-19`, `02-16`, `05-25`, `06-19`, `09-07`, `10-12`, `11-11`, `11-26`, `12-25` |
| `UnitedStates` 2027 | `01-01`, `01-18`, `02-15`, `05-31`, `07-05`, `09-06`, `10-11`, `11-11`, `11-25` |
| defasagem 0 no sábado `2026-09-12` | `2026-09-12` |
| defasagem 1 na segunda `2026-09-14`, Brazil+US | `2026-09-11` |
| defasagem 2 na segunda `2026-09-14`, Brazil | `2026-09-10` |
| defasagem 1 em `2026-11-03`, Brazil (`11-02` feriado) | `2026-10-30` |
| defasagem 1 em `2026-11-23`, Brazil (`11-20` feriado) | `2026-11-19` |
| defasagem 1 em `2026-11-12`, Brazil / Brazil+US (`11-11` só americano) | `2026-11-11` / `2026-11-10` |
| defasagem 1 em `2026-11-27`, Brazil+US (`11-26` Thanksgiving) | `2026-11-25` |

## 7. PAUSA depois da base (tarefa 1.6)

Concluídas as tarefas 1.1 a 1.5, **parar**. Rodar `mvn compile` e `mvn test`, escrever o resumo (arquivos criados, arquivos alterados, testes e resultado, `TODO(revisao)` deixados) e aguardar a revisão. Não adiantar nada das tarefas 2.x.

## 8. Actions de carga (tarefa 2.1)

```java
public interface ExecucaoManualActionPort {           // na v0, implementada só pelas actions de carga
    String getActionName();
    default List<String> getSupportedActionNames() { return List.of(getActionName()); }
    ResultadoExecucao executar(Tarefa tarefa, LocalDate dataBase, boolean incluirDownload, String correlationId);
}

public enum TipoResultado { SUCESSO, NAO_RECEBIDA, NAO_IMPLEMENTADA, ERRO }

public record ResultadoExecucao(TipoResultado resultado, String fonte, LocalDate dataBase, boolean incluirDownload,
                                String idCarga, Integer statusHttp, String detalhe) {
    public static ResultadoExecucao erro(String fonte, LocalDate dataBase, boolean incluirDownload, String detalhe) {
        return new ResultadoExecucao(TipoResultado.ERRO, fonte, dataBase, incluirDownload, null, null, detalhe);
    }
}
```

`CargaFonteTaskActionAdapter implements TaskActionPort, ExecucaoManualActionPort` (`@Component`), com `getActionName()` = `carga-download-site` e `getSupportedActionNames()` = `["carga-download-site", "carga-data-license"]`. O `execute(Tarefa)` da `TaskActionPort` chama `executar(tarefa, null, false, UUID)` e lança `BusinessException` se o resultado não for `SUCESSO` (para o agendamento antigo, que a v0 não usa).

`executar(...)`, passo a passo:
1. Parâmetros da tarefa (`tarefa.getParametros()`, por `nome`, sem diferenciar maiúsculas): `fonte`, `destino`, `caminhoDownload`, `caminhoReprocessamento`, `tickers`, `defasagemDiasUteis`, `calendarios` (`inicioHorario` e `limiteHorario` são ignorados).
2. `defasagemDiasUteis` ausente = 0; não inteiro ou fora de 0 a 10 → `InvalidInputException(PARAMETRO_INVALIDO)`; `calendarios` por `Calendarios.de(...)`.
3. `hoje = LocalDate.now()`; `padrao = Calendarios.dataBasePadrao(hoje, defasagem, calendarios)`; `dataBase` nula → `padrao`; `dataBase.isAfter(hoje)` → `InvalidInputException(PARAMETRO_INVALIDO)`.
4. Caminho: `!dataBase.isBefore(padrao)` → `caminhoDownload`; senão, `incluirDownload` → `caminhoDownload`; senão `caminhoReprocessamento` ou, ausente, `caminhoDownload`.
5. Em `carga-data-license` sem `tickers` (ausente ou vazio), ou caminho com `{tickers}` e sem o parâmetro → `ResultadoExecucao.erro(..., "parâmetro tickers ausente")`, sem chamada.
6. Trocar `{dataBase}` por `dataBase.toString()` e `{tickers}` por `URLEncoder.encode(tickers, UTF_8).replace("+", "%20")`.
7. `chamadaSaida.chamar(GET, destino, caminho, correlationId)`; `ResourceAccessException` → `NAO_RECEBIDA`; `BusinessException` de destino ou caminho → `ERRO` com o código no `detalhe`.
8. Classificar:

| Resposta | Resultado |
|---|---|
| 200 com `dataBase` igual à pedida | `SUCESSO`, com o `idCarga` do corpo |
| 200 com outra `dataBase`; 502; 503 | `NAO_RECEBIDA` |
| 501 | `NAO_IMPLEMENTADA` |
| 200 com corpo ilegível; qualquer outro status | `ERRO` |

9. `detalhe`: `codigoErro` e `mensagem` do corpo de erro (`{ "codigoErro", "mensagem", "correlationId" }`, o contrato do processor), unidos por `: ` e cortados em 500 caracteres; nunca o corpo inteiro.

Respostas do processor (para os testes): 200 `{ "idCarga": "B3-TS-20260914-1a2b3c4d5e6f", "dataBase": "2026-09-14", "hashArquivo": "...", "origem": "DOWNLOAD", "verticesPorCodigo": { "PRE": 278 }, "correlationId": "..." }`; erro `{ "codigoErro": "ARQUIVO_INDISPONIVEL", "mensagem": "...", "correlationId": "..." }`.

## 9. Rota de execução manual (tarefa 2.2)

```java
// SchedulerAPI
@PostMapping("/tarefas/{id}/executar")
ResponseEntity<ExecucaoManualResponseDto> executar(
    @PathVariable Long id,
    @RequestParam(required = false) LocalDate dataBase,
    @RequestParam(defaultValue = "false") boolean incluirDownload,
    @RequestHeader(name = "X-Usuario", required = false) String usuario,
    @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId);

public record ExecucaoManualResponseDto(String resultado, String fonte, LocalDate dataBase, boolean incluirDownload,
                                        String idCarga, Integer statusHttp, String detalhe, String usuario, String correlationId) {}
```

`SchedulerUseCase.executeTask(Long id)` passa a ser `ResultadoExecucao executeTask(Long id, LocalDate dataBase, boolean incluirDownload, String usuario, String correlationId)`. No `SchedulerService`:

```java
public ResultadoExecucao executeTask(Long id, LocalDate dataBase, boolean incluirDownload, String usuario, String correlationId) {
    String cid = correlationId != null ? correlationId : UUID.randomUUID().toString();
    Tarefa tarefa = buscarPorId(id);
    String origem = taskRepositoryPort.reivindicar(id, jsonInicio(dataBase, usuario, cid));
    ResultadoExecucao r = null;
    String erro = null;
    try {
        r = executarAction(tarefa, dataBase, incluirDownload, cid);       // fora de transação
    } catch (InvalidInputException e) {                                    // data futura, defasagem ou calendário inválido
        erro = mensagemCurta(e);
        throw e;                                                           // a rota responde 400; o finally registra e devolve
    } catch (RuntimeException e) {
        r = ResultadoExecucao.erro(fonte(tarefa), dataBase, incluirDownload, mensagemCurta(e));
    } finally {
        boolean sucesso = r != null && r.resultado() == TipoResultado.SUCESSO;
        taskRepositoryPort.registrarLog(id, sucesso ? 200 : 500, jsonResultado(r, erro, usuario, cid));
        taskRepositoryPort.devolver(id, origem);
    }
    return r;
}
```

- `executarAction`: se a `action` da tarefa está num `ExecucaoManualActionPort` (mapa por `getSupportedActionNames()`), chamar `executar(...)`; senão `taskActionExecutor.execute(tarefa)` e `SUCESSO`.
- `jsonResultado` monta o JSON da spec (`resultado`, `fonte`, `dataBase`, `incluirDownload`, `idCarga`, `statusHttp`, `detalhe`, `usuario`, `correlationId`); com `r` nulo, `resultado` = `ERRO` e `detalhe` = `erro`.
- O caminho manual não usa mais `runningTasks`, `prepararNovoCicloParaExecucaoManual` nem `executarTarefa(tarefa, true)`; não apague esses métodos (o agendamento antigo ainda os usa).
- O controller devolve 200 com `ExecucaoManualResponseDto` montado do resultado, com `usuario` e `correlationId`, e o cabeçalho `X-Correlation-Id`.

## 10. bff e front (tarefas 2.3 e 2.4)

- bff: `POST /api/v1/tarefas/{id}/executar?dataBase=&incluirDownload=` (opcionais), segurança e perfil como as outras ações de operação do bff; repassa ao orquestrador `POST /api/v1/agendador/tarefas/{id}/executar` com a mesma `dataBase` (ou sem ela) e o `incluirDownload`, `X-Usuario` = usuário do token, `X-Correlation-Id`, sem `Authorization`; tempo limite de 150 s.
- Front: na lista de tarefas, "Executar" abre o campo "Data-base" (`dd/mm/aaaa`, opcional, dica "Vazio: data-base padrão da tarefa"), a caixa "Baixar de novo da fonte" (desmarcada; habilitada só com data) e "Confirmar"; botão desabilitado enquanto espera; resultado em pt-BR (`SUCESSO` → "Sucesso", `NAO_RECEBIDA` → "Arquivo ainda não recebido", `NAO_IMPLEMENTADA` → "Fonte ainda não implementada", `ERRO` → "Erro"), data-base usada em `dd/mm/aaaa`, identificador da carga e detalhe.

## 11. Testes (poucos e amplos)

Sem dependência nova: JUnit 5, Mockito e Spring Test do `spring-boot-starter-test`; destino falso com `com.sun.net.httpserver.HttpServer` (JDK). **Nenhum teste sobe o contexto do Spring** (`@SpringBootTest`, `@WebMvcTest`): o orquestrador tem Kafka, Feign e JPA configurados. Rota com `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(handler)`; o resto, objetos montados à mão (dependências que o teste não usa, com `Mockito.mock`). Sem banco nos testes (o SQL da reivindicação é conferido na homologação, 3.2); o repositório é falso em memória. Em teste com "hoje", passe a data por parâmetro ou fixe o relógio do teste.

| Classe de teste | Tipo | Cobre |
|---|---|---|
| `CalendariosTest` | unitário | todos os vetores da seção 6; `calendarios` desconhecido → `PARAMETRO_INVALIDO` |
| `ChamadaSaidaClientTest` | unitário, destino falso | destino não cadastrado e caminhos inválidos (`http://x`, `//x`, `/a/../b`, `a`, `/a\b`, `/@x`) sem nenhuma chamada; `X-Correlation-Id` presente e `Authorization` ausente; 3xx não seguido; tempo esgotado → `ResourceAccessException` |
| `CargaFonteTaskActionAdapterTest` | unitário, destino falso | `@ParameterizedTest`: as três fontes; caminho escolhido (padrão, data passada com e sem `incluirDownload`, sem `caminhoReprocessamento`); `{tickers}` com espaço → `%20`; `carga-data-license` sem `tickers` sem chamada; classificação de 200 igual, 200 outra data, 200 ilegível, 404, 422, 500, 501, 502, 503 e tempo esgotado; defasagem 11 e data futura → `InvalidInputException` |
| `ExecucaoManualServiceTest` | unitário, repositório e action falsos | `SchedulerService.executeTask`: logs `102` e `200`/`500` com o JSON da spec; volta à origem com sucesso, `NAO_RECEBIDA` e exceção; `EXECUTANDO` → 409; `DESABILITADA` → 400; duas threads ao mesmo tempo → uma 409 e uma chamada só (o repositório falso faz o "UPDATE condicional" com `compareAndSet`); usuário nulo |
| `SchedulerExecutarRotaTest` | MockMvc `standaloneSetup` do `SchedulerController`, caso de uso mockado | `dataBase` e `incluirDownload` opcionais e repassados; `X-Usuario` e `X-Correlation-Id`; 400 e 409 do handler; resposta 200 com o JSON em todos os resultados |
| `TarefaJpaMapperTest` e `ApplicationFusoTest` | unitários | ida e volta de `action`/`descricao`; `fixarFuso()` com o padrão da JVM em UTC passa a `America/Sao_Paulo` |

Ao fim, rode a suíte inteira: os testes existentes que usavam `url` na action `http` passam a usar `destino` e `caminho`.

## 12. Tarefas para a homologação (3.2)

Parâmetros das três tarefas (todas `PRONTA`, sem agendar):

| Tarefa | `action` | `destino` | `caminhoDownload` | `caminhoReprocessamento` | Outros |
|---|---|---|---|---|---|
| B3 | `carga-download-site` | `conector-b3` | `/api/v1/cargas/b3/download?dataBase={dataBase}` | `/api/v1/cargas/b3/reprocessamento?dataBase={dataBase}` | `fonte` = `B3`, `calendarios` = `Brazil/Settlement`, `defasagemDiasUteis` = 0 |
| ANBIMA | `carga-download-site` | `conector-anbima` | `/api/v1/cargas/anbima/download?dataBase={dataBase}` | `/api/v1/cargas/anbima/reprocessamento?dataBase={dataBase}` | `fonte` = `ANBIMA`, `calendarios` = `Brazil/Settlement`, `defasagemDiasUteis` = 0 |
| Bloomberg | `carga-data-license` | `conector-bloomberg` | `/api/v1/cargas/bloomberg/download?dataBase={dataBase}&tickers={tickers}` | `/api/v1/cargas/bloomberg/reprocessamento?dataBase={dataBase}` | `fonte` = `BLOOMBERG`, `calendarios` = `Brazil/Settlement,UnitedStates/FederalReserve`, `defasagemDiasUteis` = 0 [A CONFIRMAR], `tickers` = os 20 da SOFR |

`inicioHorario` e `limiteHorario` podem ser cadastrados já (são ignorados na v0).

## 13. Ordem

1. Tarefas 1.1 a 1.5 (seções 2 a 6).
2. **PAUSA** (1.6, seção 7).
3. Tarefas 2.1 e 2.2 (seções 8 e 9).
4. bff e front, 2.3 e 2.4 (seção 10).
5. Fechamento e homologação (3.x, seção 12).
