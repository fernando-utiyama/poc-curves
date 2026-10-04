# Guia de implementação: engine (changes `engine-construcao-curvas` e `engine-modelos-curva`)

Guia passo a passo para aplicar as duas changes do engine com o mínimo de decisões: a `engine-construcao-curvas` (primeira parte, a construção das 7 curvas) e a `engine-modelos-curva` (segunda parte). Cada `tasks.md` diz quais tarefas são dele; as tarefas mantêm a numeração deste guia. **A spec manda; este guia diz onde e como.** Siga a ordem da seção 15. Os testes são escritos só na seção 16, depois de tudo compilar.

> **O código real manda nos detalhes.** Este guia foi escrito sobre a cópia do serviço no poc (`services/engine`, pacote `br.com.poc`). No repositório real, o pacote raiz, as classes de exceção, o tratador de erro que de fato responde, o formato do corpo de erro, a biblioteca de log, a configuração do Jackson, os caches e o registro de beans **são os que o serviço já tem**: onde este guia cita uma classe ou configuração do código, leia "a equivalente do serviço" e confira antes de usar. O que não muda é o comportamento das specs (rotas, códigos de erro e de aviso, regras, formatos, vetores de teste). Divergência entre o guia e o código real não é motivo para parar: siga o código real e cumpra a spec.
>
> Na aplicação do guia do curves no serviço real apareceram estas divergências, que valem como lista do que conferir aqui também:
> - pacote raiz diferente do `br.com.poc` do guia, e o projeto na raiz do repositório (não em `services/...`);
> - exceções existentes que só produzem um código genérico (`BAD_REQUEST`, `NOT_FOUND`) e não aceitam o código da spec;
> - dois tratadores de erro no mesmo serviço (um em Problem Details e outro com record próprio), e `spring.mvc.problemdetails.enabled` não desliga um tratador próprio;
> - o record de erro com cópias no projeto, só uma usada, e com tipo de `timestamp` diferente do que o guia supunha;
> - entidade com coluna que não existe no script, nome de coluna errado e `Double` em coluna `DECIMAL`;
> - biblioteca de log da empresa em vez de `logstash-logback-encoder`;
> - configuração de Jackson 2 (`com.fasterxml.jackson.databind.Module`) que o Spring Boot 4 (Jackson 3) ignora;
> - cache distribuído (Redis e starter de caching) já disponível;
> - serviços registrados como `@Bean` numa classe de configuração, não por `@Service`.

## 0. Regras para quem implementa

**Java 21, arquitetura hexagonal, código nativo.**
- **Use o que o Java já tem:** `java.time` (`LocalDate`, `Period`, `DayOfWeek`, `TemporalAdjusters`, `ChronoUnit`, `LocalDate.datesUntil`), `BigDecimal`, `MathContext` e `RoundingMode`, records, sealed interfaces, `switch` com pattern matching, `HexFormat`, `MessageDigest`, `Stream`, `java.util.zip`, virtual threads.
- **Código próprio só nestes pontos**, porque o Java não tem:
  - os **enums com nomes do QuantLib** (`Compounding`, `Frequency`, `BusinessDayConvention`, `DayCounter`) e o record `InterestRate`, para os scripts Groovy usarem os nomes do mercado;
  - os **calendários de feriados** (`Brazil`, `UnitedStates`, `CalendarioPorLista`);
  - a ponte `DecimalMath` entre `BigDecimal` e o `StrictMath` (`pow`, `ln`, `exp`);
  - os **interpoladores** e os **modelos de construção** (estendíveis por Groovy);
  - as regras do negócio (validação do cadastro, dias úteis ancorados, grade, `hashPontos`).
- **Proibido criar tipo próprio** de relógio, período, unidade de tempo, arredondamento ou "utils" de data: use `java.time` e `RoundingMode`.
- **Records para tudo que é dado** (domínio, DTOs, eventos, linhas de memória, linhas de insumo). Classe só onde o record não serve: entidade JPA, `Calendario` e interpoladores (estendidos por Groovy), `MemoriaCalculo` (acumula linhas durante o cálculo).
- **Sem `synchronized`** (no Java 21 ele prende a virtual thread à thread do sistema): use `ConcurrentHashMap`, `ReentrantLock` e `Atomic*`.
- **Sem MapStruct nem Lombok no código novo:** conversão por métodos estáticos `de(...)` nos records.
- Todo número de curva é `BigDecimal`; `double` só dentro do `DecimalMath` (`StrictMath`) e na célula numérica da planilha.
- Fuso: a JVM inteira roda em `America/Sao_Paulo` (seção 1.4). `LocalDate.now()` e `OffsetDateTime.now()` são usados direto.
- Não invente rota, código de erro, aviso, coluna ou tabela fora deste guia e das specs. Não crie tabela, sequência, índice nem tópico.
- Arquivos de configuração que já existem (`pom.xml`, `application*.yml`, a configuração de log): **não reescrever**; conferir e acrescentar só o que faltar.
- O serviço roda em no mínimo 2 instâncias: nenhum estado de negócio em memória local, além dos caches descritos aqui (feriados por ano e estado dos scripts, que são iguais em todas as instâncias e podem ficar em memória; se o serviço já tiver cache distribuído, usá-lo é aceitável, sem mudar o comportamento).

### 0.1 Arquitetura hexagonal (pacote raiz do serviço; no poc, `br.com.poc`)

```
domain/                       Java puro: nada de Spring, JPA, Jackson, Azure, POI, Groovy
  curva/                      records do negócio, enums de erro e aviso, ResultadoConstrucao (sealed), HashPontos
  quantlib/                   Compounding, Frequency, BusinessDayConvention, DayCounter, InterestRate, Periodos
  matematica/                 DecimalMath
  calendario/                 Calendario, Brazil, UnitedStates, CalendarioPorLista
  interpolacao/               BaseInterpolacao, Extrapolacao, Classificacao, Interpolador, InterpoladorPorSegmento, Linear, LogLinear, FlatForward,
                              BackwardFlat, ForwardFlat, Cubic, EixoDiasUteis, PreparacaoPontos,
                              CurvaInterpolada, InterpolacaoDadoCurva
  construcao/                 ModeloConstrucao, ContextoConstrucao, CurvaPrimariaPort (porta do domínio),
                              B3CurvaPrimaria/AnbimaCurvaPrimaria/BloombergCurvaPrimaria, PontosProntos, ProntaTsB3, SofrZeroBloomberg,
                              NtnbBootstrapAnbima
  memoria/                    MemoriaCalculo e as linhas (records)
  cadastro/                   TabelaParametros, ValidadorCadastro
application/
  port/in/                    casos de uso (interfaces): ConstruirCurva, SimularCurva, ConsultarCurva,
                              InterpolarCurva, RegravarInterpolada, ProcessarCarga, ConstruirData,
                              ConsultarSituacao, ConsultarValoresCadastro, GerirScripts, GerirCalendario,
                              MontarAuditoria
  port/out/                   uma porta por tabela: CurvaMercadoPort (com a trava e o resumo de tCurvaMercd),
                              CurvaProvedorPort, ConfiguracaoCurvaPort, DadoVerticeCurvaPort, DadoCurvaPort; e ScriptsPort, CompiladorScriptsPort, PlanilhaPort, EventosPort
  service/                    uma classe por caso de uso, registrada do mesmo jeito que os serviços existentes (`@Service` ou `@Bean` numa configuração); @Transactional quando grava;
                              Paralelo (virtual threads), ResolverModelos
adapter/
  in/rest/                    controllers, DTOs (records), FiltroCorrelacao, configuração do Jackson (erros: o tratador do serviço, seção 1.5)
  out/persistence/            entidades JPA, repositórios Spring Data, adaptadores das portas
  out/blob/                   ScriptsBlobAdapter
  out/groovy/                 CarregadorGroovy
  out/planilha/               PlanilhaPoiAdapter
  out/log/                    EventosLogAdapter
config/                       ExecutorConfig, TimeZoneConfig
```

Regra de dependência: `adapter` → `application` → `domain`; o domínio não importa nada de fora dele. Os scripts Groovy só enxergam `domain/{curva, quantlib, matematica, calendario, interpolacao, construcao, memoria}` (spec `curve-extension-models`).

### 0.2 Papel das tabelas (não confundir)

| Tabela | Conteúdo | Quem grava |
|---|---|---|
| `tDadoVertcCurva` | **curva construída**: os pontos, com `cDiaUtil`, `cQtdDiaPer`, `cQtdDiaReft`, `vFatorAcum`, `vFatorDia`, `vPrecoTx` | engine; `services/curves` na edição manual |
| `tDadoCurva` | **curva interpolada**: um `vPrecoTx` por dia corrido | só o engine (o curves só apaga) |
| `tCurvaMercd` | cadastro da curva; o engine escreve só `dBaseReft` e `cUsuarCalc` | curves e engine |
| `tCurvaPrvdr`, `tConfgCurva` | origens e configuração vigente | curves |
| `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr` | brutos das fontes | processor e feeders |

Pontos, dias úteis dos pontos e `hashPontos` vêm **sempre** de `tDadoVertcCurva`. `tDadoCurva` nunca é lida para calcular.

### 0.3 Vetores de teste reais

Fonte: `docs/TaxaSwap.txt` (data-base `B` = `2026-09-14`), conferidos por uma reimplementação independente das fórmulas da spec:

| Item | Valor esperado |
|---|---|
| vértices por curva (`PRE`, `DCL`, `DPL`, `INP`, `PTX`) | 278, de `2026-09-15` a `2060-08-16` (`cDiaCorri` 12.390, `cDiaUtil` 8.496) |
| primeiro valor | `PRE` 13.9; `DCL` -117.96; `DPL` 18.59; `INP` 185648; `PTX` 5.1696 |
| último valor | `PRE` 14.16; `DCL` 20.13; `DPL` 7.19; `INP` 233414; `PTX` 56.3772259 |
| vértice de 7.406 DU da `PRE` | `2056-04-10`, 14.167 |
| `DU` do `Brazil` nos 278 vértices da `PRE` | igual ao `cDiaUtil` publicado em todos (0 divergências) |
| `hashPontos` `PRE` | `7c4982b34ca35f784863118902e63f28e7d49362d940cc27eb77961d526fca20` |
| `hashPontos` `DCL` | `0504d24e90556dab534e53ec6b81740c99baef65829ff00cb1f61ced388aaf40` |
| `hashPontos` `DPL` | `82c7d7ffcc7f4bbad9ca64a8dd611ea528cb10764f0782f456164def0dd8977a` |
| `hashPontos` `INP` | `f08705dd681da17324ee8f7264be41a949408fb14cecdcbb49700a30f3db741a` |
| `hashPontos` `PTX` | `1b2bbfc82020d153483a47193561702b0cdc7f516193fe4c0c8137153810827b` |
| `hashPontos` do vetor comum com o curves (`2026-09-15;13.9\n2026-09-16;-117.96`) | `8dcff432fa5271ff16bdaef72940792811802e0a5ae59e8c2bf5cead818d5544` |
| interpolação em `2030-06-10` (DU 932, DC 1.365; vizinhos `2030-05-15` DU 914 e `2030-07-01` DU 946, `w` = 0.5625) | `PRE` 14.0624222; `DCL` 6.6547016; `PTX` 6.6901076 (`DOWN`) |
| fatores da `PRE` em DU 932 (valor 14.0624222) | acumulado 1.6268101753135545; diário 1.0005222620285954 (conferir com tolerância de `1e-14`: as últimas casas vêm do `double`) |
| fatores da `DCL` em DC 1.365 (valor 6.6547016, `Actual360`/`Simple`) | acumulado 1.2523241023333333 (exato: sem `pow`); diário (`FA^(1/932)`) 1.0002414466401755 (tolerância `1e-14`) |
| fator da `PRE` no 1º vértice (13.9, DU 1) | acumulado = diário = 1.0005166043641946 (tolerância `1e-14`) |
| curva interpolada da `PRE` | 12.390 linhas em `tDadoCurva`, de `2026-09-15` a `2060-08-16`; `2026-09-19` e `2026-09-20` iguais a `2026-09-18` |
| Páscoa | 2026: `04-05` (Carnaval `02-16`/`02-17`, Sexta-feira Santa `04-03`, Corpus Christi `06-04`); 2027: `03-28` (Carnaval `02-08`/`02-09`, Sexta `03-26`, Corpus `05-27`) |
| `UnitedStates` 2026 | `01-01`, `01-19`, `02-16`, `05-25`, `06-19`, `09-07`, `10-12`, `11-11`, `11-26`, `12-25` (4 de julho no sábado: não observado) |
| `UnitedStates` 2027 | `01-01`, `01-18`, `02-15`, `05-31`, `07-05`, `09-06`, `10-11`, `11-11`, `11-25` (19/06 e 25/12 no sábado: não observados) |
| tenor `15M` a partir de `2026-09-14` | `2027-12-14` |
| 30/360 de `2026-09-14` a `2027-01-04` | 110 |

---

## 1. Base

### 1.1 `pom.xml`: o mínimo de coisas novas

> Na primeira parte (`engine-construcao-curvas`), nenhuma destas dependências entra: sem Blob, Groovy nem planilha. Só a meta `build-info`, se for usada na proveniência.

| Acrescentar | Por quê |
|---|---|
| `com.azure:azure-storage-blob` e `com.azure:azure-identity`, pelo `com.azure:azure-sdk-bom` **1.3.8** importado em `dependencyManagement` (`<type>pom</type>`, `<scope>import</scope>`) | scripts Groovy no Blob, Managed Identity |
| `org.apache.poi:poi-ooxml` **5.5.1** | planilhas `.xlsx` |

No `spring-boot-maven-plugin` (já existe), acrescentar a meta `build-info`. Não remover nada do que já está no pom (MapStruct, Lombok, Feign, Redis ficam; o código novo só não os usa). Nenhuma dependência de teste nova: `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, MockMvc) e o `archunit-junit5`, se o serviço já tiver (senão, a regra de dependência do `ArquiteturaTest` vira busca de imports no pacote `domain`).

### 1.2 `application.yml` (conferir e acrescentar)

```yaml
engine:
  blob:
    endpoint: ${ENGINE_BLOB_ENDPOINT}
    container: ${ENGINE_BLOB_CONTAINER}
    circuito-aberto-segundos: 60
  groovy:
    timeout-segundos: 5
    cache-estado-segundos: 30
    espera-subida-segundos: 300
  timeout:
    banco-conexao-ms: 5000
    banco-comando-segundos: 30
    trava-curva-segundos: 30
    blob-ms: 5000
    requisicao-segundos: 60
    carga-segundos: 120
    situacao-segundos: 60
    construcao-data-segundos: 300
  situacao:
    paralelismo: 8
  construcao-data:
    paralelismo: 8
spring:
  threads:
    virtual:
      enabled: true                       # requisições HTTP em virtual threads
  datasource:
    hikari:
      connection-timeout: ${engine.timeout.banco-conexao-ms}
      maximum-pool-size: 20               # >= paralelismo + margem para as requisições
      transaction-isolation: TRANSACTION_READ_COMMITTED
  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          batch_size: 1000
        order_inserts: true
```

Perfil `local` (e só ele): `engine.blob.connection-string` para o Azurite.

### 1.3 Virtual threads: `config/ExecutorConfig.java` e `application/service/Paralelo.java`

```java
@Configuration
public class ExecutorConfig {
  @Bean(destroyMethod = "close")
  ExecutorService executorVirtual() { return Executors.newVirtualThreadPerTaskExecutor(); }
}
```

Trabalho em paralelo (construção da data, situação): uma virtual thread por curva, limitada por `Semaphore`. O limite protege o pool de conexões do banco, não as threads.

```java
public sealed interface Resultado<R> {
  record Ok<R>(R valor) implements Resultado<R> {}
  record TempoEsgotado<R>() implements Resultado<R> {}
  record Falha<R>(Throwable erro) implements Resultado<R> {}
}

public final class Paralelo {
  public static <T, R> List<Resultado<R>> executar(ExecutorService exec, List<T> itens, int limite,
      Duration prazo, Function<T, R> tarefa) {
    var vagas = new Semaphore(limite);
    var futuros = itens.stream().map(i -> exec.submit(() -> {
      vagas.acquire();
      try { return tarefa.apply(i); } finally { vagas.release(); }
    })).toList();
    long fim = System.nanoTime() + prazo.toNanos();
    return futuros.stream().<Resultado<R>>map(f -> {
      try { return new Resultado.Ok<>(f.get(Math.max(0, fim - System.nanoTime()), TimeUnit.NANOSECONDS)); }
      catch (TimeoutException e) { f.cancel(true); return new Resultado.TempoEsgotado<>(); }
      catch (ExecutionException e) { return new Resultado.Falha<>(e.getCause()); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); return new Resultado.TempoEsgotado<>(); }
    }).toList();
  }
}
```

(`TimeUnit` aqui é o `java.util.concurrent.TimeUnit` do Java.) Cada tarefa abre a sua própria transação: a curva é a unidade de gravação.

### 1.4 Fuso da JVM: `Application.java` e `config/TimeZoneConfig.java`

```java
public static void main(String[] args) {
  TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));   // antes de qualquer coisa
  SpringApplication.run(Application.class, args);
}

@Configuration
public class TimeZoneConfig {
  static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  @Bean ApplicationRunner conferirFuso() {
    return args -> {
      if (!ZoneId.systemDefault().equals(BRASILIA))
        throw new IllegalStateException("Fuso da JVM é " + ZoneId.systemDefault() + "; o engine exige America/Sao_Paulo");
    };
  }
}
```

"Hoje" = `LocalDate.now()`; instante = `OffsetDateTime.now()` (sai com `-03:00`); carimbo de arquivo = `LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))`. Instante no JSON: `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`.

### 1.5 Erros, avisos e rótulos: padrão do projeto

Erros usam **o que o serviço já tem** (no poc, `application/exception`: `ErrorCode`, `BaseException` e as subclasses). O que a spec exige, e o que o teste confere, é só o conteúdo da resposta: o código da spec (`CURVA_NAO_ENCONTRADA`, não um código genérico), o título e o detalhe em português, a rota, o `correlationId` e, quando houver, os `detalhes`. Como chegar lá depende do código real:
- **qual tratador responde:** o serviço pode ter mais de um (no poc, `ApplicationExceptionHandler` em Problem Details; em outros serviços do projeto há um segundo com record próprio). Deixar um formato só, decidindo qual prevalece (`@Order` ou retirar o outro), e conferir com um teste de rota por status;
- **código da spec na exceção:** se uma exceção existente só produz um código genérico (ex.: `NOT_FOUND`), acrescentar a ela um construtor que recebe `ErrorCode`, ou lançar a que já aceita; não criar hierarquia paralela.

```java
// domain/curva/CodigoErro.java — implementa a interface do projeto
public enum CodigoErro implements ErrorCode {
  PARAMETRO_INVALIDO, CURVA_NAO_ENCONTRADA, CURVA_NAO_CONSTRUIDA,
  CODIGO_DUPLICADO, NOME_AMBIGUO, CONSTRUCAO_EM_ANDAMENTO, ESTADO_SCRIPT_CONCORRENTE,
  CADASTRO_INVALIDO, CURVA_COMPONENTE_NAO_CONSTRUIDA, INSUMO_INCOMPLETO, INSUMO_AUSENTE, INSUMO_INVALIDO,
  PONTOS_NAO_INTERPOLAVEIS, PRAZO_FORA_DO_DOMINIO, MODELO_FALHOU, SCRIPT_INVALIDO, ERRO_INTERNO, BLOB_INDISPONIVEL;
  public String getCode() { return name(); }
  public String getMessage() { return name(); }   // o texto pt-BR vem do messages.properties (poc.errors.<code>)
}
```

Status esperado para cada código, com a exceção equivalente no poc (no real, conferir a classe e o tratador):

| Exceção (no poc) | HTTP | Códigos |
|---|---|---|
| `InvalidInputException` | 400 | `PARAMETRO_INVALIDO` |
| `NotFoundException` | 404 | `CURVA_NAO_ENCONTRADA`, `CURVA_NAO_CONSTRUIDA` |
| `ConflictException` (**acrescentar**, no molde das demais, e o método dela no handler) | 409 | `CODIGO_DUPLICADO`, `NOME_AMBIGUO`, `CONSTRUCAO_EM_ANDAMENTO`, `ESTADO_SCRIPT_CONCORRENTE` |
| `BusinessException` | 422 | `CADASTRO_INVALIDO`, `CURVA_COMPONENTE_NAO_CONSTRUIDA`, `INSUMO_*`, `PONTOS_NAO_INTERPOLAVEIS`, `PRAZO_FORA_DO_DOMINIO`, `MODELO_FALHOU`, `SCRIPT_INVALIDO` |
| `InfrastructureException` | 500 | `ERRO_INTERNO` |
| `ServiceUnavailableException` | 503 | `BLOB_INDISPONIVEL` |

**Acrescentar** (sem reescrever nada que existe): na exceção base do serviço, a lista `detalhes` (`List<DetalheErro>`, com `comDetalhes(List<DetalheErro>)`); no tratador que responde, `correlationId` (do MDC) e `detalhes` (quando houver) no corpo de erro, e o tratamento de 409. A ArchUnit do projeto passa a permitir que `domain` dependa de `application.exception` (classes em Java puro), e de nada mais fora do domínio.

```java
public record DetalheErro(String campo, Integer linha, String valor, String motivo) {}       // item de detalhes de um erro

public enum CodigoAvisoCurva {
  CURVA_INATIVA, FORA_DA_VIGENCIA_CURVA, PONTOS_DIFERENTES_DA_FONTE, COMPARACAO_INDISPONIVEL, PONTO_DESCARTADO_MESMO_PRAZO,
  PONTO_DESCARTADO_PRAZO_NAO_POSITIVO, CALENDARIO_DIVERGENTE, CALCULO_GRAVADO_DIVERGENTE, SEM_CALCULO_GRAVADO,
  INTERPOLADA_DESATUALIZADA, ORIGEM_SECUNDARIA, ESTADO_SCRIPT_DESATUALIZADO, ESTADO_SCRIPT_DESCONHECIDO
}
public record DetalheAviso(String campo, Integer linha, String valor, String motivo) {}
public record AvisoCurva(CodigoAvisoCurva codigo, String mensagem, List<DetalheAviso> detalhes) {}
```

**Textos (rótulos e descrições pt-BR) no `messages.properties`**, pelo `MessageSource` que o tratador de erro já usa (conferir as chaves que ele espera), e não em record nem em interface:
- erros: no poc, `poc.errors.title.<CODIGO>` (título) e `poc.errors.<CODIGO>` (detalhe padrão); no real, o prefixo que o tratador do serviço espera;
- demais enums do cadastro, das respostas e dos avisos: `poc.valores.<Enum>.<CONSTANTE>.rotulo` e `.descricao`; o `GET /valores-cadastro` monta o catálogo lendo essas chaves;
- teste obrigatório: toda constante de todo enum exposto tem as suas chaves (substitui a garantia de "sem texto não compila");
- os três `RoundingMode` aceitos no cadastro: `poc.valores.RoundingMode.<CONSTANTE>.*`.

---

## 2. Matemática: `domain/matematica/DecimalMath.java`

Híbrido (decisão D7): valores e arredondamento em `BigDecimal`; `pow` fracionário, `ln` e `exp` pelo **`StrictMath`** do Java (nunca `Math`: o `StrictMath` dá o mesmo resultado bit a bit em qualquer máquina, o que mantém o `hashPontos` igual entre as instâncias). Expoente inteiro pelo `BigDecimal.pow` nativo, exato.

```java
public final class DecimalMath {
  public static final MathContext MC = MathContext.DECIMAL128;
  private DecimalMath() {}

  public static BigDecimal pow(BigDecimal a, BigDecimal b) {
    if (b.signum() == 0) return BigDecimal.ONE;
    if (b.stripTrailingZeros().scale() <= 0 && b.abs().compareTo(BigDecimal.valueOf(999_999)) <= 0)
      return a.pow(b.intValueExact(), MC);                              // inteiro: exato
    if (a.signum() <= 0) throw new ArithmeticException("potência fracionária de valor não positivo: " + a);
    return BigDecimal.valueOf(StrictMath.pow(a.doubleValue(), b.doubleValue()));
  }
  public static BigDecimal ln(BigDecimal x) {
    if (x.signum() <= 0) throw new ArithmeticException("ln de valor não positivo: " + x);
    return BigDecimal.valueOf(StrictMath.log(x.doubleValue()));
  }
  public static BigDecimal exp(BigDecimal z) { return BigDecimal.valueOf(StrictMath.exp(z.doubleValue())); }
}
```

`BigDecimal.valueOf(double)` usa a representação decimal mais curta do `double` (a do `Double.toString`), também determinística.

Arredondamento: **nada próprio**. Valor da curva = `valor.setScale(configuracao.casasDecimais(), configuracao.modoArredondamento())` (o `modoArredondamento` já é `RoundingMode`). Fatores = `fator.setScale(16, RoundingMode.HALF_UP)`, direto no cálculo dos fatores (seção 6.5).

---

## 3. Tipos com nomes do QuantLib: `domain/quantlib/`

Nomes exatos, com o PascalCase das constantes do QuantLib:

```java
public enum Compounding { Simple, Compounded, Continuous, SimpleThenCompounded, CompoundedThenSimple }

public enum Frequency {
  NoFrequency(-1), Once(0), Annual(1), Semiannual(2), EveryFourthMonth(3), Quarterly(4), Bimonthly(6),
  Monthly(12), EveryFourthWeek(13), Biweekly(26), Weekly(52), Daily(365), OtherFrequency(999);
  private final int vezesPorAno; Frequency(int v) { vezesPorAno = v; }
  public int vezesPorAno() { return vezesPorAno; }
}

public enum BusinessDayConvention { Following, ModifiedFollowing, Preceding, ModifiedPreceding, Unadjusted,
  HalfMonthModifiedFollowing, Nearest }

/** base = data-base; data = data do prazo; du = dias úteis já resolvidos (publicados ou ancorados, seção 6.3). */
public enum DayCounter {
  Business252    { public BigDecimal fracaoAno(LocalDate base, LocalDate data, int du) { return div(du, 252); } },
  Actual360      { public BigDecimal fracaoAno(LocalDate base, LocalDate data, int du) { return div(ChronoUnit.DAYS.between(base, data), 360); } },
  Actual365Fixed { public BigDecimal fracaoAno(LocalDate base, LocalDate data, int du) { return div(ChronoUnit.DAYS.between(base, data), 365); } },
  Thirty360      { public BigDecimal fracaoAno(LocalDate base, LocalDate data, int du) { return div(dias30360(base, data), 360); } };

  public abstract BigDecimal fracaoAno(LocalDate base, LocalDate data, int du);

  /** 30/360 USA (Bond Basis). Também é o cQtdDiaReft. */
  public static int dias30360(LocalDate b, LocalDate d) {
    int d1 = Math.min(b.getDayOfMonth(), 30);
    int d2 = (d.getDayOfMonth() == 31 && d1 == 30) ? 30 : d.getDayOfMonth();
    return 360 * (d.getYear() - b.getYear()) + 30 * (d.getMonthValue() - b.getMonthValue()) + (d2 - d1);
  }
  private static BigDecimal div(long a, int b) { return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), DecimalMath.MC); }
}
```

`Business252` recebe o `du` já resolvido; ele não conta dias úteis sozinho.

### 3.1 `InterestRate.java` (cotação)

```java
public record InterestRate(DayCounter dayCounter, Compounding compounding, Frequency frequency) {
  private static final BigDecimal CEM = BigDecimal.valueOf(100);

  public BigDecimal fator(BigDecimal valorPercentual, LocalDate base, LocalDate data, int du) {
    var r = valorPercentual.divide(CEM, DecimalMath.MC);
    var tau = dayCounter.fracaoAno(base, data, du);
    var fa = switch (compounding) {
      case Simple -> BigDecimal.ONE.add(r.multiply(tau, DecimalMath.MC));
      case Compounded -> {
        var f = BigDecimal.valueOf(frequency.vezesPorAno());
        yield DecimalMath.pow(BigDecimal.ONE.add(r.divide(f, DecimalMath.MC)), f.multiply(tau));
      }
      case Continuous -> DecimalMath.exp(r.multiply(tau, DecimalMath.MC));
      case SimpleThenCompounded, CompoundedThenSimple ->
          throw new BusinessException(CodigoErro.CADASTRO_INVALIDO.getCode(), "Cotação não suportada: " + compounding);
    };
    if (fa.signum() <= 0) throw new BusinessException(CodigoErro.MODELO_FALHOU.getCode(), "Fator acumulado não positivo: " + fa);
    return fa;
  }

  public BigDecimal taxa(BigDecimal fa, LocalDate base, LocalDate data, int du) {          // inversa exata, em percentual
    var tau = dayCounter.fracaoAno(base, data, du);
    var r = switch (compounding) {
      case Simple -> fa.subtract(BigDecimal.ONE).divide(tau, DecimalMath.MC);
      case Compounded -> {
        var f = BigDecimal.valueOf(frequency.vezesPorAno());
        yield DecimalMath.pow(fa, BigDecimal.ONE.divide(f.multiply(tau), DecimalMath.MC)).subtract(BigDecimal.ONE).multiply(f);
      }
      case Continuous -> DecimalMath.ln(fa).divide(tau, DecimalMath.MC);
      case SimpleThenCompounded, CompoundedThenSimple -> throw new IllegalStateException();
    };
    return r.multiply(CEM);
  }
}
```

### 3.2 `Periodos.java`: `nD|nW|nM|nY` → `java.time.Period`

```java
public final class Periodos {
  private static final Pattern FORMATO = Pattern.compile("^([1-9][0-9]*)([DWMY])$");
  private Periodos() {}
  public static Period parse(String s) {
    var m = FORMATO.matcher(s);
    if (!m.matches()) throw new IllegalArgumentException("Período fora do formato nD, nW, nM ou nY: " + s);
    int n = Integer.parseInt(m.group(1));
    return switch (m.group(2)) {
      case "D" -> Period.ofDays(n);
      case "W" -> Period.ofWeeks(n);
      case "M" -> Period.ofMonths(n);
      default -> Period.ofYears(n);
    };
  }
}
```

`LocalDate.plus(Period)` já leva ao último dia do mês quando o dia não existe. Usado no `HORIZONTE` (dias corridos). O tenor da SOFR trata `D` à parte (dias úteis, seção 8.5).

---

## 4. Calendários: `domain/calendario/`

### 4.1 Base `Calendario.java`

Classe abstrata (não interface) porque guarda o cache de feriados e porque os scripts Groovy a estendem sobrescrevendo só `feriados(int ano)`.

```java
public abstract class Calendario {
  private final ConcurrentHashMap<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();
  public abstract String nome();
  public abstract String mercado();
  protected abstract Set<LocalDate> feriados(int ano);

  public boolean isBusinessDay(LocalDate d) {
    return switch (d.getDayOfWeek()) {
      case SATURDAY, SUNDAY -> false;
      default -> !cache.computeIfAbsent(d.getYear(), a -> Set.copyOf(feriados(a))).contains(d);
    };
  }

  /** Dias úteis em (de, ate]; 0 se ate <= de. */
  public int diasUteis(LocalDate de, LocalDate ate) {
    if (!ate.isAfter(de)) return 0;
    return (int) de.plusDays(1).datesUntil(ate.plusDays(1)).filter(this::isBusinessDay).count();
  }

  /** n dias úteis depois de d (n >= 0). */
  public LocalDate advance(LocalDate d, int n) {
    if (n == 0) return d;
    return Stream.iterate(d.plusDays(1), x -> x.plusDays(1)).filter(this::isBusinessDay)
        .skip(n - 1L).findFirst().orElseThrow();
  }

  public LocalDate adjust(LocalDate d, BusinessDayConvention c) {
    return switch (c) {
      case Unadjusted -> d;
      case Following -> proximo(d, 1);
      case Preceding -> proximo(d, -1);
      case ModifiedFollowing -> { var f = proximo(d, 1); yield f.getMonth() == d.getMonth() ? f : proximo(d, -1); }
      case ModifiedPreceding -> { var p = proximo(d, -1); yield p.getMonth() == d.getMonth() ? p : proximo(d, 1); }
      case HalfMonthModifiedFollowing -> {
        var mf = adjust(d, BusinessDayConvention.ModifiedFollowing);
        yield (d.getDayOfMonth() <= 15 && mf.getDayOfMonth() > 15) ? proximo(d, -1) : mf;
      }
      case Nearest -> {
        var f = proximo(d, 1); var p = proximo(d, -1);
        yield ChronoUnit.DAYS.between(d, f) <= ChronoUnit.DAYS.between(p, d) ? f : p;
      }
    };
  }

  private LocalDate proximo(LocalDate d, int passo) {    // d se útil, senão o próximo útil no sentido do passo
    var r = d; while (!isBusinessDay(r)) r = r.plusDays(passo); return r;
  }
}
```

### 4.2 `Brazil.java` (nome `Brazil`, mercado `Settlement`, construtor `new Brazil()`)

```java
public class Brazil extends Calendario {
  public String nome() { return "Brazil"; }
  public String mercado() { return "Settlement"; }
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
```

### 4.3 `UnitedStates.java` (nome `UnitedStates`, mercado `FederalReserve`)

Com os `TemporalAdjusters` do Java:

```java
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
```

### 4.4 `CalendarioPorLista.java`

`public class CalendarioPorLista extends Calendario`, construtor `(String nome, String mercado, int anoInicial, int anoFinal, Set<LocalDate> datas)`. `feriados(ano)` devolve as datas do ano; ano fora da cobertura → `BusinessException(MODELO_FALHOU, "Calendário {nome}: data fora da cobertura {anoInicial}–{anoFinal}")`. `isBusinessDay` confere a cobertura antes de chamar `super`.

---

## 5. Cadastro

### 5.1 Records (`domain/curva/`)

```java
public enum Unidade { TAXA, PRECO, PONTOS }   // rótulos em poc.valores.Unidade.* (messages.properties, seção 1.5)
public record CurvaProvedor(String fonte, String produto, String codigoNaFonte, int prioridade) {}   // uma linha de tCurvaPrvdr
public record Componente(String nome, String papel) {}
public record ConfiguracaoCurva(BaseInterpolacao baseInterpolacao, DayCounter dayCounterTempo, Frequency frequency, String calendario,
    String mercadoCalendario, BusinessDayConvention convencao, Extrapolacao extrapolacaoInicio,
    Extrapolacao extrapolacaoFim, Period horizonte, int casasDecimais, RoundingMode modoArredondamento,
    Integer versaoScriptConstrucao, Integer versaoScriptInterpolacao, Integer versaoScriptCalendario,
    Map<String, String> modelosPorOrigem) {}
public record CurvaMercado(String codigo, String nome, Unidade unidade, DayCounter dayCounterCotacao,
    Compounding compounding, boolean ativa, LocalDate inicioVigencia, LocalDate fimVigencia,
    List<CurvaProvedor> origens /* por prioridade */, List<Componente> componentes /* vazia se não derivada */,
    long idConfiguracao, String modeloConstrucao, String interpolador, ConfiguracaoCurva configuracao, String jsonParametros) {
  public CurvaProvedor origemPrincipal() { return origens.getFirst(); }       // SequencedCollection (Java 21)
  public boolean derivada() { return "TCEN".equals(origemPrincipal().fonte()); }
  public InterestRate cotacao() { return new InterestRate(dayCounterCotacao, compounding, configuracao.frequency()); }
}
```

### 5.2 Persistência (`adapter/out/persistence/`, implementa `CurvaMercadoPort`, `CurvaProvedorPort` e `ConfiguracaoCurvaPort`)

Entidades JPA (classes, só no adaptador) de `tCurvaMercd`, `tCurvaPrvdr` e `tConfgCurva`. As que já existirem no serviço são reaproveitadas e **conferidas coluna a coluna contra o `001_SCRIPT_INICIAL.sql`** antes do uso (no curves real havia coluna inexistente mapeada, nome de coluna errado e `Double` em coluna `DECIMAL`): valor de curva sempre `BigDecimal` com a precisão e a escala da coluna. Entidade com `@Data` serve; getters de `CHAR` escritos à mão continuam valendo. Texto sempre com `stripTrailing()` na leitura (colunas `CHAR`). O adaptador devolve os dados crus em records (`CurvaMercadoLida`); quem monta e valida o `CurvaMercado` é o domínio (5.3). Consultas (`@Query(nativeQuery = true)` quando o JPQL não expressa igual):

```sql
-- curva por código (0 → CURVA_NAO_ENCONTRADA; >1 → CODIGO_DUPLICADO)
SELECT cTickerIndcd, cTickerIdtfdUnic, cTpoVlr, cNormaDia, cTpoJuro, cSitReg, dInicVgcia, dValidAte, dBaseReft, cUsuarCalc
  FROM tCurvaMercd WHERE cTickerIdtfdUnic = ?;
-- por nome: ler (cTickerIndcd, cTickerIdtfdUnic) com código não nulo e comparar em Java o nome normalizado
-- (Normalizer.normalize(s, NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip()); 0 → CURVA_NAO_ENCONTRADA; >1 → NOME_AMBIGUO
SELECT cTickerIndcd, cTickerIdtfdUnic FROM tCurvaMercd WHERE cTickerIdtfdUnic IS NOT NULL;
SELECT iPrvdrDados, cPrvdrMercd, cTickerPrvdr, cPriorCsumo FROM tCurvaPrvdr WHERE cTickerIndcd = ? ORDER BY cPriorCsumo, cIdtfdUnic;
-- configuração vigente (exatamente 1 linha; 0 ou >1 → CADASTRO_INVALIDO)
SELECT cIdtfdConfg, cMotorCalc, cRotnaCalc, cModDado FROM tConfgCurva
 WHERE cTickerIndcd = ? AND dInicVgcia <= ? AND (dValidAte IS NULL OR dValidAte >= ?);
```

O `cModDado` é lido pelo adaptador com o Jackson e entregue ao domínio como `Map<String, Object>` (o domínio não depende do Jackson); JSON inválido vira um problema de `CADASTRO_INVALIDO` com o motivo.

### 5.3 `domain/cadastro/TabelaParametros` e `ValidadorCadastro`

`TabelaParametros`: lista de records `Chave(String nome, TipoJson tipo, boolean obrigatoria, String condicao, String padrao, List<String> valores, String formato, String rotulo, String descricao)`, na ordem da tabela da spec `curve-build-pipeline`. Fonte única do validador e do `GET /valores-cadastro`. `MODO_ARREDONDAMENTO` aceita `HALF_UP`, `HALF_EVEN`, `DOWN` e vira `RoundingMode.valueOf(...)` (os nomes são os mesmos do Java).

`ValidadorCadastro.montar(CurvaMercadoLida, Optional<CurvaProvedor> origemPedida, Function<String, ModeloConstrucao> resolver)`: junta todos os problemas e lança um único `CADASTRO_INVALIDO` com um `DetalheAviso` por problema:
1. `cModDado` é objeto JSON; cada chave existe na tabela; tipo certo.
2. Obrigatórios presentes; valores nas listas, com caixa exata. Padrão só em `EXTRAPOLACAO_INICIO`/`FIM` (`Disabled`).
3. `cTpoVlr` ∈ `TAXA|PRECO|PONTOS`; com `TAXA`, `cNormaDia` ∈ constantes de `DayCounter` (`DayCounter.valueOf`) e `cTpoJuro` ∈ `Simple|Compounded|Continuous`.
4. Combinações: `Price` só com `PRECO`/`PONTOS` e o contrário; `FREQUENCY` só com `Compounded` (e obrigatória nele), nunca `NoFrequency|Once|OtherFrequency`; `MERCADO_CALENDARIO` = `mercado()` do calendário resolvido; interpolador `FlatForward` só com a base `Discount`; extrapolação `FlatForward` só com `Linear|LogLinear|FlatForward`; `CASAS_DECIMAIS` 0..12; `HORIZONTE` por `Periodos.parse`.
5. `MODELOS_POR_ORIGEM`: chave `^[^/]+/[^/]+$`, valor texto não vazio; chave sem provedor ou da principal é ignorada.
6. O modelo de construção da origem usada aceita a fonte e o produto dela.
7. Derivada: componentes existem, sem ciclo (busca em profundidade pelos provedores da curva `TCEN`), papéis iguais aos declarados pelo modelo.

`cSitReg` ≠ `ATIVO` (inclusive nulo) → `ativa = false`. Situação e vigência nunca geram `CADASTRO_INVALIDO`.

### 5.4 Origem secundária

`fonte` e `produto` juntos ou nenhum (só um → `PARAMETRO_INVALIDO`); `fonte = TCEN` → `PARAMETRO_INVALIDO`. Provedor da curva com `iPrvdrDados = fonte` e `cPrvdrMercd = produto`; nenhuma ou mais de uma → `CADASTRO_INVALIDO` com as origens. Modelo = `modelosPorOrigem.get(fonte + "/" + produto)` ou `cMotorCalc`. Aviso `ORIGEM_SECUNDARIA` (fonte, produto, código na fonte, prioridade).

---

## 6. Interpolação: `domain/interpolacao/`

### 6.1 Contratos e interpoladores

```java
public interface Interpolador {                        // chamado só com xs.getFirst() <= x <= xs.getLast()
  String nome();
  BigDecimal valor(BigDecimal x, List<BigDecimal> xs, List<BigDecimal> ys, MemoriaCalculo memoria);
}

public abstract class InterpoladorPorSegmento implements Interpolador {
  public final BigDecimal valor(BigDecimal x, List<BigDecimal> xs, List<BigDecimal> ys, MemoriaCalculo m) {
    int pos = Collections.binarySearch(xs, x);
    if (pos >= 0) return ys.get(pos);
    int i = Math.min(-pos - 2, xs.size() - 2);                       // maior i com xs[i] < x
    var w = x.subtract(xs.get(i)).divide(xs.get(i + 1).subtract(xs.get(i)), DecimalMath.MC);
    return valorNoSegmento(w, ys.get(i), ys.get(i + 1));
  }
  public final BigDecimal extrapolar(BigDecimal w, BigDecimal yEsquerda, BigDecimal yDireita) {   // FlatForward
    return valorNoSegmento(w, yEsquerda, yDireita);
  }
  protected abstract BigDecimal valorNoSegmento(BigDecimal w, BigDecimal yEsquerda, BigDecimal yDireita);
}
```

| Classe | `valorNoSegmento` |
|---|---|
| `Linear` | `yE + w·(yD − yE)` |
| `LogLinear` | `yE · DecimalMath.pow(yD/yE, w)` (todos os `y` > 0, checado antes; senão `PONTOS_NAO_INTERPOLAVEIS` com os pontos) |
| `FlatForward` | igual ao `LogLinear` (a classe estende `LogLinear`); o cadastro com `BASE_INTERPOLACAO` diferente de `Discount` é recusado antes, com `CADASTRO_INVALIDO` |
| `BackwardFlat` | `yD` |
| `ForwardFlat` | `yE` |

Classes públicas e não finais: um script Groovy pode estender `LogLinear` e sobrescrever só `valorNoSegmento`.

`Cubic implements Interpolador`: spline natural em `BigDecimal`. `h_i = x_{i+1} − x_i`; segundas derivadas `M` com `M_0 = M_{n-1} = 0` e, para `i = 1..n-2`, `h_{i-1}·M_{i-1} + 2(h_{i-1}+h_i)·M_i + h_i·M_{i+1} = 6·((y_{i+1}−y_i)/h_i − (y_i−y_{i-1})/h_{i-1})`, resolvido pelo algoritmo de Thomas. Avaliação: `S(x) = M_i(x_{i+1}−x)³/(6h_i) + M_{i+1}(x−x_i)³/(6h_i) + (y_i/h_i − M_i h_i/6)(x_{i+1}−x) + (y_{i+1}/h_i − M_{i+1} h_i/6)(x−x_i)`. Com 2 pontos, igual ao linear.

### 6.2 `BaseInterpolacao`, `Extrapolacao` e `Classificacao` (enums)

```java
public enum BaseInterpolacao {
  Discount, CompoundFactor, ZeroYield, Price;   // rótulos em poc.valores.BaseInterpolacao.*
  public BigDecimal paraY(BigDecimal valor, LocalDate base, LocalDate data, int du, InterestRate cotacao) {
    return switch (this) {
      case Discount -> BigDecimal.ONE.divide(cotacao.fator(valor, base, data, du), DecimalMath.MC);
      case CompoundFactor -> cotacao.fator(valor, base, data, du);
      case ZeroYield -> valor.movePointLeft(2);
      case Price -> valor;
    };
  }
  public BigDecimal deY(BigDecimal y, LocalDate base, LocalDate data, int du, InterestRate cotacao) {
    return switch (this) {
      case Discount -> cotacao.taxa(BigDecimal.ONE.divide(y, DecimalMath.MC), base, data, du);
      case CompoundFactor -> cotacao.taxa(y, base, data, du);
      case ZeroYield -> y.movePointRight(2);
      case Price -> y;
    };
  }
}
public enum Extrapolacao { Disabled, FlatForward, FlatValue }   // rótulos em poc.valores.Extrapolacao.*
public enum Classificacao { PONTO, INTERPOLADO, EXTRAPOLADO_INICIO, EXTRAPOLADO_FIM, FORA_DO_DOMINIO; }   // rótulos em poc.valores.Classificacao.*
```

### 6.3 Dias úteis ancorados: `EixoDiasUteis.java` (requisito "Dias úteis publicados pela fonte ou informados pelo usuário")

```java
public record DadoVerticeCurva(LocalDate data, BigDecimal valor, Integer diasUteis /* cDiaUtil; null = calendário */,
    Integer diasCorridos, Integer dias30360, BigDecimal fatorAcum, BigDecimal fatorDia) {}

public final class EixoDiasUteis {
  private final LocalDate base; private final Calendario cal; private final List<LocalDate> datas; private final int[] du;

  public EixoDiasUteis(LocalDate base, Calendario cal, List<DadoVerticeCurva> mantidos) {   // em ordem de data
    this.base = base; this.cal = cal;
    this.datas = mantidos.stream().map(DadoVerticeCurva::data).toList();
    this.du = mantidos.stream().mapToInt(p -> p.diasUteis() != null ? p.diasUteis() : cal.diasUteis(base, p.data())).toArray();
  }

  public int du(LocalDate d) {
    int pos = Collections.binarySearch(datas, d);
    if (pos >= 0) return du[pos];
    int i = -pos - 2;                                              // -1 se antes do primeiro
    if (i < 0) return Math.min(cal.diasUteis(base, d), du[0]);
    int v = du[i] + cal.diasUteis(datas.get(i), d);
    return i + 1 < du.length ? Math.min(v, du[i + 1]) : v;
  }

  public LocalDate dataDoDu(int n) {                             // prazo pedido em du
    for (int i = 0; i < du.length; i++) if (du[i] == n) return datas.get(i);
    int i = -1; while (i + 1 < du.length && du[i + 1] < n) i++;    // maior índice com du < n
    var de = i < 0 ? base : datas.get(i);
    var d = cal.advance(de, n - (i < 0 ? 0 : du[i]));
    return (i + 1 < du.length && !d.isBefore(datas.get(i + 1))) ? datas.get(i + 1).minusDays(1) : d;
  }
}
```

Na grade diária (6.6) os dias úteis são contados caminhando os dias em ordem (soma 1 a cada dia útil e reinicia em cada ponto), sem chamar `du(d)` do zero para cada dia.

### 6.4 `PreparacaoPontos.java` (requisito "Pontos no mesmo prazo do eixo")

Entrada: pontos em ordem de data. `x` = `dayCounterTempo.fracaoAno(B, d, DUp)`. Em ordem:
1. `x <= 0` → descarta, aviso `PONTO_DESCARTADO_PRAZO_NAO_POSITIVO`.
2. `x` ≤ `x` do último mantido → descarta com `PONTO_DESCARTADO_MESMO_PRAZO` (data descartada, data mantida, `x`).
3. Nenhum mantido → `CURVA_NAO_CONSTRUIDA`.

Devolve `record PontosPreparados(List<DadoVerticeCurva> mantidos, List<AvisoCurva> avisos)`. Os gravados nunca são alterados.

### 6.5 `CurvaInterpolada.java`

Criada por um método de fábrica com o cadastro, os pontos mantidos, o calendário e o interpolador resolvidos:
- início do domínio `cal.advance(B, 1)`; fim `max(último ponto, B.plus(horizonte))`;
- `xs`, `ys` (`baseInterpolacao.paraY`), eixo de dias úteis, políticas de início e fim.

`ValorNoPrazo avaliar(LocalDate d, Integer duPedido)`, com `record ValorNoPrazo(LocalDate data, int du, long dc, BigDecimal x, BigDecimal valor, Classificacao classificacao, BigDecimal fatorAcum, BigDecimal fatorDia)`:
1. `d` fora de `[início, fim]` → `PRAZO_FORA_DO_DOMINIO` (prazo e limites).
2. `du = duPedido != null ? duPedido : eixo.du(d)`; `x` pelo eixo (`dayCounterTempo.fracaoAno(B, d, du)`).
3. `d` = data de ponto mantido → valor gravado, `PONTO`.
4. Antes do primeiro → política de início; depois do último → política de fim; senão interpolador e `baseInterpolacao.deY`.
5. `Disabled` → `PRAZO_FORA_DO_DOMINIO`; `FlatValue` → valor do ponto adjacente; `FlatForward` (só `Linear`/`LogLinear`/`FlatForward`, ≥ 2 pontos) → `extrapolar(w, yE, yD)` do segmento adjacente com `w` fora de `[0,1]`.
6. `valor.setScale(casasDecimais, modoArredondamento)`. Para `TAXA`: `fa = cotacao.fator(valorArredondado, B, d, du).setScale(16, RoundingMode.HALF_UP)`, `fd = DecimalMath.pow(fa, BigDecimal.ONE.divide(BigDecimal.valueOf(du), DecimalMath.MC)).setScale(16, RoundingMode.HALF_UP)` (`du` ≥ 1). `PRECO`/`PONTOS`: fatores nulos.

### 6.6 `InterpolacaoDadoCurva.java`

- Início: `cal.advance(B, 1)`, ou o primeiro ponto se a extrapolação de início é `Disabled`. Fim: fim do domínio, ou o último ponto se a de fim é `Disabled`.
- `List<DadoCurva>` (`record DadoCurva(LocalDate data, BigDecimal valor)`), um por dia corrido de `inicio.datesUntil(fim.plusDays(1))`, cada dia pelo mesmo `avaliar` da 6.5, com os dias úteis contados em ordem.
- O valor de cada dia MUST ser igual ao da rota de interpolação para a mesma data.

---

## 7. Casos de uso de construção: `application/service/`

### 7.1 Pedido e resultado (sealed), em `domain/curva/`

```java
public enum Acionamento { CARGA, CONSTRUCAO_DATA, API }
public record PedidoConstrucao(String codigo, LocalDate dataBase, boolean forcarRecalculo, String fonte, String produto,
    Acionamento acionadoPor, String usuario, String idCarga, Map<String, Integer> linhasAvisadas) {}

public sealed interface ResultadoConstrucao permits Construida, Reconstruida, Existente, Ignorada, SemInsumo, Falhou {
  String codigo();
}
public record Construida(String codigo, String nome, LocalDate dataBase, Proveniencia proveniencia, int pontos,
    String hashPontos, List<AvisoCurva> avisos, long duracaoMs) implements ResultadoConstrucao {}
public record Reconstruida(String codigo, String nome, LocalDate dataBase, Proveniencia proveniencia, int pontos,
    String hashPontos, List<AvisoCurva> avisos, long duracaoMs) implements ResultadoConstrucao {}
public record Existente(String codigo, String nome, LocalDate dataBase, String hashPontos, List<AvisoCurva> avisos) implements ResultadoConstrucao {}
public record Ignorada(String codigo, CodigoAvisoCurva motivo) implements ResultadoConstrucao {}          // CURVA_INATIVA | FORA_DA_VIGENCIA_CURVA
public record SemInsumo(String codigo) implements ResultadoConstrucao {}
public record Falhou(String codigo, CodigoErro codigoErro, String mensagem, List<DetalheAviso> detalhes) implements ResultadoConstrucao {}
```

O adaptador REST converte com `switch (resultado) { case Construida c -> ...; case Existente e -> ...; ... }`, exaustivo: o compilador avisa se faltar um caso.

### 7.2 `ConstruirCurvaService` (`@Transactional`, sem `timeout`)

Portas: `CurvaMercadoPort` (`tCurvaMercd`: trava, `dBaseReft` e `cUsuarCalc`), `CurvaProvedorPort` e `ConfiguracaoCurvaPort` (cadastro), `DadoVerticeCurvaPort` (`tDadoVertcCurva`), `DadoCurvaPort` (`tDadoCurva`), `CurvaPrimariaPort` (tabelas `*CurvaPrimr`), `EventosPort`. O SQL que tem de chegar ao banco (trava e resumo em consulta nativa; exclusões em `@Modifying @Query`; inclusões por `saveAll` com o `batch_size`):

A transação **não** tem tempo limite próprio: os 30 s são só para obter a trava (spec `curve-engine-resilience`). A trava é uma consulta nativa com tempo limite **no próprio comando**, `query.setHint("jakarta.persistence.query.timeout", engine.timeout.trava-curva-segundos × 1000)`, que o Hibernate passa ao JDBC (`Statement.setQueryTimeout`) e não fica na conexão do pool. Só o estouro desse comando (`jakarta.persistence.QueryTimeoutException`) vira `CONSTRUCAO_EM_ANDAMENTO`; qualquer outra exceção de banco sobe como erro interno. **Proibido** `SET LOCK_TIMEOUT` e `@Transactional(timeout = ...)`.

```sql
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;   -- tempo limite do comando: 30 s → CONSTRUCAO_EM_ANDAMENTO
SELECT dVertcReft, vPrecoTx, cDiaUtil, cQtdDiaPer, cQtdDiaReft, vFatorAcum, vFatorDia
  FROM tDadoVertcCurva WHERE cTickerIndcd = ? AND dBaseReft = ? ORDER BY dVertcReft;
DELETE FROM tDadoCurva      WHERE cTickerIndcd = ? AND dBaseReft = ?;
DELETE FROM tDadoVertcCurva WHERE cTickerIndcd = ? AND dBaseReft = ?;
INSERT INTO tDadoVertcCurva (dBaseReft, cTickerIndcd, dVertcReft, cDiaUtil, vFatorDia, vFatorAcum, cQtdDiaPer, cQtdDiaReft, vPrecoTx) VALUES (...);
INSERT INTO tDadoCurva (dBaseReft, cTickerIndcd, dVertcReft, vPrecoTx) VALUES (...);
UPDATE tCurvaMercd SET dBaseReft = CASE WHEN dBaseReft IS NULL OR dBaseReft < ? THEN ? ELSE dBaseReft END,
                       cUsuarCalc = ? WHERE cTickerIndcd = ?;
```

Entidades (só no adaptador): `DadoVertcCurvaEntity` (`@IdClass` com `dBaseReft`, `cTickerIndcd`, `dVertcReft`; `cDiaUtil`, `cQtdDiaPer`, `cQtdDiaReft` `Integer`; `vFatorDia`, `vFatorAcum` `precision 28, scale 16`; `vPrecoTx` `precision 28, scale 12`) e `DadoCurvaEntity` (as 4 colunas do schema; tirar `dtVerticeReferencia`, `cDiaUtil`, `cQtdDiaReft`, `vDiaFator`, `vFatorCalc`).

Passos:
1. Trava; pontos atuais (depois da trava).
2. Cadastro vigente (seção 5) com a origem pedida; na construção pela API, avisos `CURVA_INATIVA`/`FORA_DA_VIGENCIA_CURVA`.
3. Havendo pontos e sem `forcarRecalculo`: passos 4–6 sem gravar, comparar `hashPontos`, aviso `PONTOS_DIFERENTES_DA_FONTE` com a quantidade de pontos diferentes → `Existente`. Nada é escrito. Se os passos 4–6 falharem, a curva continua `Existente`, com o aviso `COMPARACAO_INDISPONIVEL` (código e mensagem da falha) e o log `COMPARACAO_INDISPONIVEL`; nunca `Existente` sem aviso, nunca erro da curva.
4. Insumos pelo `CurvaPrimariaPort`; na carga, linhas lidas ≠ `linhasAvisadas[código na fonte]` → `INSUMO_INCOMPLETO` (lidas e avisadas).
5. Modelo → `List<VerticeConstruido>`; arredondar; data repetida → `MODELO_FALHOU`. Para cada ponto: `DUp` = publicado ou `cal.diasUteis(B, d)`; publicado ≠ calendário, ou data não útil → acumular `CALENDARIO_DIVERGENTE`; `DC`; `DayCounter.dias30360`; para `TAXA`, fatores (16 casas).
6. `CurvaInterpolada` + `InterpolacaoDadoCurva`; falha → desfaz tudo.
7. Gravar (SQL acima); `Construida` ou `Reconstruida`. A proveniência vem do que o `RegistroModelos` de fato resolveu para modelo, interpolador e calendário: nativo → origem `JAVA` e versão do engine; script → origem `GROOVY`, versão e hash do script. Nunca valores fixos.
8. `TransactionSynchronization.afterCommit`: `CURVA_GRAVADA` e `CONSTRUCAO_CONCLUIDA`. Falha → `CONSTRUCAO_FALHOU`, sem `CURVA_GRAVADA`.

`cUsuarCalc` = o `usuario` da requisição (cabeçalho `X-Usuario`, seção 13.3), nulo quando ausente.

### 7.3 `SimularCurvaService`

Mesmo pipeline sem trava nem escrita (o pipeline dos passos 2–6 é um componente único, chamado pelos dois serviços, com `gravar` falso aqui). Falha de negócio não vira HTTP: devolve `status` `ERRO`, `codigoErro`, mensagem e a memória até a falha (só `CURVA_NAO_ENCONTRADA` e `PARAMETRO_INVALIDO` viram HTTP). Comparação com os gravados: `IGUAL`, `DIFERENTE`, `SO_SIMULADO`, `SO_GRAVADO`. Prazo pedido fora do domínio: `FORA_DO_DOMINIO` com motivo.

### 7.4 `RegravarInterpoladaService`

Mesma trava. Sem pontos → apaga `tDadoCurva` da data e `CURVA_NAO_CONSTRUIDA`. Com pontos → cadastro, `CurvaInterpolada`, grade, `DELETE` + `INSERT` só em `tDadoCurva`. Sem modelo, sem `CURVA_GRAVADA`. O evento `INTERPOLADA_REGRAVADA` sai em `TransactionSynchronization.afterCommit`, como o `CURVA_GRAVADA` da construção (seção 7.2, passo 8); falha ou desfazer da transação não emite o evento.

### 7.5 Conferência da interpolada (consulta, auditoria, situação)

Grade em memória a partir dos pontos atuais × `SELECT dVertcReft, vPrecoTx FROM tDadoCurva WHERE cTickerIndcd = ? AND dBaseReft = ?` (comparar com `compareTo`: o banco devolve 12 casas). Diferença → `INTERPOLADA_DESATUALIZADA` com a quantidade de dias.

### 7.6 `HashPontos` (`domain/curva/`)

```java
public final class HashPontos {
  private HashPontos() {}
  public static String calcular(List<DadoVerticeCurva> pontos) {
    var texto = pontos.stream().sorted(Comparator.comparing(DadoVerticeCurva::data))
        .map(p -> p.data() + ";" + (p.valor().signum() == 0 ? "0" : p.valor().stripTrailingZeros().toPlainString()))
        .collect(Collectors.joining("\n"));
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
```

---

## 8. Modelos de construção: `domain/construcao/`

### 8.1 Contratos

```java
public record VerticeConstruido(LocalDate data, BigDecimal valor, Integer diasUteisPublicados) {}
public interface ModeloConstrucao {
  String nome(); String fonte(); String produto();
  default List<String> papeis() { return List.of(); }
  List<VerticeConstruido> construir(ContextoConstrucao ctx, MemoriaCalculo memoria);
}
public record ContextoConstrucao(CurvaMercado cadastro, CurvaProvedor origem, LocalDate dataBase, Calendario calendario,
    CurvaPrimariaPort curvasPrimarias, Function<String, CurvaComponente> componentes /* só derivadas; nos outros modelos lança MODELO_FALHOU */) {
  public CurvaComponente curvaComponente(String papel) { return componentes.apply(papel); }
}
public interface CurvaPrimariaPort {                  // porta do domínio (tabelas *CurvaPrimr); implementada no adaptador
  List<B3CurvaPrimaria> b3(String nomeCurva, LocalDate dataBase);
  List<AnbimaCurvaPrimaria> anbima(String nomeCurva, LocalDate dataBase);
  List<BloombergCurvaPrimaria> bloomberg(String nomeCurva, LocalDate dataBase);
}
public record B3CurvaPrimaria(int id, String curva, LocalDate dataBase, Integer diasCorridos, Integer diasUteis, BigDecimal taxa) {}
public record AnbimaCurvaPrimaria(int id, String curva, LocalDate dataBase, BigDecimal prazo, BigDecimal taxa) {}
public record BloombergCurvaPrimaria(int id, String curva, String ticker, LocalDate dataBase, BigDecimal ultimo) {}
```

Adaptador (`CurvaPrimariaJpaAdapter`), consultas nativas:

```sql
SELECT cIdtfdUnic, cTickerIndcd, dBaseReft, cDiaCorri, cDiaUtil, vPrecoTx FROM tBtrsCurvaPrimr  WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT cIdtfdUnic, cTickerIndcd, dBaseReft, vVertcCurva, vPrecoTx      FROM tAnbmaCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT cIdtfdUnic, cTickerIndcd, cTickerBberg, dBaseReft, vPrecoUlt   FROM tBbergCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;
```

Nenhuma linha → `INSUMO_AUSENTE` (código, fonte, código na fonte, data). Cada linha vai para a aba `Insumos`.

### 8.2 `PontosProntos` (comum)

Ordena por data, data repetida → `INSUMO_INVALIDO` com as linhas, registra os pontos na memória com as colunas extras do modelo.

### 8.3 `ProntaTsB3` (`PRONTA_TS_B3`, `B3`/`TS`)

Tabela "Regras do arquivo" da spec `b3-ready-curve-model` linha a linha. Ponto: `B.plusDays(diasCorridos)`, `taxa` sem mudar sinal nem escala, `diasUteisPublicados = diasUteis`. Extras: `DC publicado`, `DU publicado`. Não falha por calendário.

### 8.4 `NtnbBootstrapAnbima` (`NTNB_BOOTSTRAP_ANBIMA`, `ANBIMA`/`MS`)

Interpolador `Linear`, `LogLinear` ou `FlatForward` (senão `CADASTRO_INVALIDO`); tabela "Regras do arquivo" da spec `ntnb-anbima-curve-model` (sem tolerância). Por título:

```java
var a = cal.advance(B, prazo);                                   // data aproximada
var v = a.withDayOfMonth(15);                                     // vencimento nominal
var p = cal.adjust(v, BusinessDayConvention.Following);           // data do ponto; DU = prazo publicado
var y = taxa.movePointLeft(2);
var c = BigDecimal.valueOf(100).multiply(DecimalMath.pow(new BigDecimal("1.06"), new BigDecimal("0.5")).subtract(BigDecimal.ONE));
// eventos: Stream.iterate(v, n -> n.minusMonths(6)).takeWhile(n -> cal.adjust(n, Following).isAfter(B));
// DU_i pelo calendário; o do vencimento = prazo. Fluxo c por evento, c + 100 no vencimento.
// Cotação C = Σ F_i·(1+y)^(−DU_i/252)
```

Bootstrap em ordem crescente de `v`, `f(z) = Σ F_i·DF_i(z) − C`, com as origens de DF `INCOGNITA`, `FLAT_INICIO`, `RESOLVIDO`, `INTERPOLADO` da spec; bisseção em `[−0.99, 1.00]` até largura < `1e-14` ou 200 iterações; sem troca de sinal → `MODELO_FALHOU`. Ponto `(p, 100·z, prazo)`. Memória: extras `Vencimento nominal`, `Taxa indicativa`, `Cotacao`, `Iteracoes`, `Residuo`; aba `Fluxos`.

### 8.5 `SofrZeroBloomberg` (`SOFR_ZERO_BLOOMBERG`, `BLOOMBERG`/`BLC2`)

Tabela "Regras do arquivo" da spec `sofr-bloomberg-curve-model`. `ticker.strip().split(" +")`: termo 0 = membro, termo 1 = tenor (`^([1-9][0-9]*)([DWMY])$`). Data: `D` → `cal.advance(B, n)`; `W`/`M`/`Y` → `cal.adjust(B.plus(Periodos.parse(tenor)), convencao)`. Mesmo tenor e mesmo valor → fica o de menor `id`, os demais `DUPLICADO`. `diasUteisPublicados = null`. Extras: `Tenor`, `Data nao ajustada`.

---

## 9. Carga, construção da data e situação

### 9.1 `ProcessarCargaService` (`POST /api/v1/cargas`)

Corpo: `record NotificacaoCarga(String idCarga, String fonte, String produto, LocalDate dataBase, Map<String, Integer> verticesPorCodigo)`; inválido → 400. Nada é gravado sobre a carga.
1. `CARGA_RECEBIDA`.
2. Curvas cuja **origem principal** tem a fonte e o produto da carga e código em `verticesPorCodigo`, agrupadas em `Map<String, List<...>>` (um código na fonte pode ter **várias** curvas; todas são processadas); código sem curva → log `AVISO`.
3. Cada curva independente: inativa/fora da vigência → `Ignorada`; senão `ConstruirCurvaService` (`CARGA`, `linhasAvisadas`, sem recálculo) com `fonte` e `produto` do `PedidoConstrucao` **nulos**: eles só existem para a construção por origem secundária pela API; na carga, a construção é pela origem principal, sem `ORIGEM_SECUNDARIA`.
4. Cadeia de derivadas (9.3).
5. 200 com o `idCarga` e os resultados; `CARGA_PROCESSADA`. Prazo total `engine.timeout.carga-segundos`.

### 9.2 `ConstruirDataService` (`POST /api/v1/construcoes/{dataBase}`)

Todas as curvas com código, regras da carga, sem conferência de quantidade, `CONSTRUCAO_DATA`; sem pontos e sem insumo → `SemInsumo`. Curvas de provedor com `Paralelo.executar` (limite `engine.construcao-data.paralelismo`, prazo `engine.timeout.construcao-data-segundos`); depois as derivadas em cadeia. Tempo esgotado: as já gravadas ficam, as outras não começam, resposta `ERRO_INTERNO`. Eventos `CONSTRUCAO_DATA_RECEBIDA` e `CONSTRUCAO_DATA_PROCESSADA`. A existência de pontos é conferida depois da trava.

### 9.3 Cadeia de derivadas

Até não mudar nada: derivadas ativas, vigentes, sem pontos e com todas as curvas componentes com pontos → construir em ordem topológica. Com pontos → comparar como `Existente`. Nunca recalcular. `curvaComponente(papel)` monta a `CurvaInterpolada` da curva componente com os pontos dela e guarda o `hashPontos` para a proveniência.

### 9.4 `ConsultarSituacaoService` (`GET /api/v1/curvas/situacao?dataBase=`)

Por curva, com `Paralelo.executar` (limite `engine.situacao.paralelismo`, prazo `engine.timeout.situacao-segundos`), sem gravar: `insumo`, `pontosGravados`, `interpolada` (7.5), `conferencia` (simulação). Falha ou tempo de uma → `conferencia.status = ERRO` só dela.

---

## 10. Memória de cálculo e planilhas

### 10.1 `domain/memoria/MemoriaCalculo.java`

Classe (acumula durante um cálculo, confinada a uma thread) com listas de records: `CampoResumo(String campo, String valor)`, `LinhaInsumo`, `LinhaPonto` (com `SequencedMap<String, Object> extras` na ordem declarada pelo modelo, `LinkedHashMap`), `LinhaFluxo`, `LinhaPrazo`, `Evento(int ordem, String nivel, String evento, String mensagem)`. Preenchida pelos próprios métodos que calculam.

### 10.2 `adapter/out/planilha/PlanilhaPoiAdapter.java` (implementa `PlanilhaPort`)

Seis abas, na ordem e com os cabeçalhos exatos da spec `curve-calculation-memory`: `Resumo`, `Insumos`, `Pontos`, `Fluxos`, `Interpolacao`, `Eventos`. Primeira linha congelada (`createFreezePane(0, 1)`); datas com formato `dd/mm/yyyy`; números como células numéricas (`doubleValue()` só na célula); célula não aplicável vazia; nota dos 15 dígitos no `Resumo`. Nome `{codigo}_{dataBase}_{GRAVADA|SIMULACAO}_{AAAAMMDDHHmmss}.xlsx`.

### 10.3 `formato=zip`

`java.util.zip.ZipOutputStream`: `memoria.xlsx`, `memoria.json`, `modelos/{tipo}/{nome}/v{versao}.groovy` (texto compilado guardado em memória), `manifesto.json` (`correlationId`, versão do engine, `estadoScript`, modelos, arquivos com SHA-256).

---

## 11. Modelos Groovy e Blob

### 11.1 `application/service/ResolverModelos`

Por tipo (`construcao`, `interpolacao`, `calendario`): versão fixada no cadastro (validada) → versão `ATIVA` do `estado.json` → nativo (beans `ModeloConstrucao`, `Interpolador` e `Calendario` do domínio, declarados num `@Configuration` e registrados por nome na subida) → `CADASTRO_INVALIDO`. Nativos: `PRONTA_TS_B3`, `NTNB_BOOTSTRAP_ANBIMA`, `SOFR_ZERO_BLOOMBERG`; `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`; `Brazil`, `UnitedStates`.

### 11.2 `adapter/out/blob/ScriptsBlobAdapter` (implementa `ScriptsPort`)

- Cliente `new BlobServiceClientBuilder().endpoint(endpoint).credential(new DefaultAzureCredentialBuilder().build())`; connection string só no perfil `local`.
- `groovy-models/{tipo}/{nome}/v{n}.groovy` com `BlobRequestConditions().setIfNoneMatch("*")` (409 com o mesmo conteúdo = sucesso).
- `estado.json`: `record EstadoScript(Integer ativa, Map<String, VersaoScript> versoes)` e `record VersaoScript(String status, String hash, String autor, OffsetDateTime criadoEm, String aprovador, OffsetDateTime atualizadoEm, String motivo)`, gravado com `setIfMatch(etag)`; 412 → `ESTADO_SCRIPT_CONCORRENTE`.
- Cache por instância de 30 s (inclusive da ausência) em `ConcurrentHashMap` com o instante de leitura; releitura condicional por ETag. Classe compilada em memória por (tipo, nome, versão, hash), sem expirar. Hash ≠ `estado.json` → `MODELO_FALHOU`.
- Repetição só em leitura e gravação imutável: 3 tentativas (200, 400, 800 ms + até 100 ms, `ThreadLocalRandom`). Circuito: 5 falhas seguidas → 60 s sem chamar o Blob (`AtomicInteger` e `AtomicLong`); log `CIRCUITO_BLOB_ABERTO` uma vez.
- Blob fora: último estado lido, sem prazo, `DESATUALIZADO` (log no máximo 1 por minuto); sem estado ou sem a versão compilada → nativo, `DESCONHECIDO`, log `ERRO`. Subida: tenta ler os estados por até 300 s fora de prontidão.

### 11.3 `adapter/out/groovy/CarregadorGroovy` (implementa `CompiladorScriptsPort`)

Os pacotes permitidos são os do `domain` do serviço, pelo pacote raiz real (no poc, `br.com.poc.domain.*`): montar a lista a partir do pacote de uma classe do domínio (`Calendario.class.getPackageName()` e irmãos), não por texto fixo, para os scripts funcionarem igual no poc e no real.

```java
var s = new SecureASTCustomizer();
s.setIndirectImportCheckEnabled(true);
s.setAllowedStarImports(List.of("br.com.poc.domain.curva", "br.com.poc.domain.quantlib", "br.com.poc.domain.matematica",
    "br.com.poc.domain.calendario", "br.com.poc.domain.interpolacao", "br.com.poc.domain.construcao",
    "br.com.poc.domain.memoria", "java.math", "java.time", "java.util"));
s.setAllowedImports(List.of());
s.setDisallowedReceivers(List.of("java.lang.System", "java.lang.Runtime", "java.lang.Thread", "java.lang.Class",
    "java.lang.ClassLoader", "java.lang.ProcessBuilder", "java.io.File", "java.net.URL", "java.net.Socket"));
var cc = new CompilerConfiguration();
cc.addCompilationCustomizers(s, new ASTTransformationCustomizer(Map.of("value", timeoutSegundos), TimedInterrupt.class));
```

A classe compilada tem de implementar o contrato do tipo (`ModeloConstrucao`, `Interpolador`, estender `Calendario`), senão `REPROVADA`. Tempo esgotado → `MODELO_FALHOU` (modelo e tempo). O `TimedInterrupt` interrompe a thread, e isso funciona igual em virtual threads.

### 11.4 Estados e validação

`RASCUNHO` → validação → `VALIDADA`/`REPROVADA`; `VALIDADA`|`INATIVA` → ativação → `ATIVA` (a anterior vira `INATIVA`; `aprovador` = quem ativou); `ATIVA` → desativação → `INATIVA`; outra → `SCRIPT_INVALIDO`. Validação: interpolação em `xs=[1,2,3]`, `ys=[0.9,0.8,0.7]`, `x=1.5` e `2.5`; calendário: todos os dias do ano corrente (e, se gerado por planilha, toda a cobertura); construção: `codigo` e `dataBase` no corpo e simulação `OK`.

---

## 12. Calendário por planilha (`GerirCalendarioService` + `PlanilhaPoiAdapter`)

Importação (`POST /api/v1/calendarios/{nome}/importacao?mercado=&anoInicial=&anoFinal=`, multipart `arquivo`): aba `Feriados` (`Data`, `Descricao`); rejeições da spec `calendar-management` → 400 com um `DetalheAviso` por problema. Script gerado por um text block fixo (datas em ordem crescente):

```java
static String script(String nome, String mercado, int ini, int fim, SortedSet<LocalDate> datas) {
  var classe = "Calendario_" + nome.replaceAll("[^A-Za-z0-9]", "_");
  var lista = datas.stream().map(d -> "    LocalDate.parse(\"" + d + "\")").collect(Collectors.joining(",\n"));
  return """
      import br.com.poc.domain.calendario.*
      import java.time.LocalDate
      class %s extends CalendarioPorLista {
        %s() { super("%s", "%s", %d, %d, [
      %s
        ] as Set) }
      }
      """.formatted(classe, classe, nome, mercado, ini, fim, lista);
}
```

Mesma planilha → mesmo texto → mesmo hash. Grava a próxima versão `RASCUNHO` do tipo `calendario`; a planilha não é guardada. Exportação (`GET /api/v1/calendarios/{nome}?mercado=&anoInicial=&anoFinal=&versao=&formato=`): dias de segunda a sexta não úteis no intervalo (≤ 150 anos); `xlsx` com `Feriados` e `Resumo`.

---

## 13. API: `adapter/in/rest/`

### 13.1 Rotas

| Controller | Rotas |
|---|---|
| `CargaController` | `POST /cargas` |
| `ConstrucaoDataController` | `POST /construcoes/{dataBase}` |
| `CurvaController` | `GET /curvas`, `GET /curvas/situacao`, `POST /curvas/{codigo}/{dataBase}/construcao`, `GET /curvas/{codigo}/{dataBase}`, `GET .../interpolacao`, `GET .../simulacao`, `POST .../interpolada`, `GET .../auditoria` |
| `CurvaPorNomeController` | `GET /curvas/por-nome/{dataBase}`, `.../interpolacao`, `.../simulacao` |
| `ValoresCadastroController` | `GET /valores-cadastro` |
| `ModeloController` | `POST /modelos/{tipo}/{nome}`, `POST .../versoes/{versao}/validacao`, `POST .../versoes/{versao}/ativacao`, `POST .../desativacao`, `GET /modelos/{tipo}/{nome}` |
| `CalendarioController` | `POST /calendarios/{nome}/importacao`, `GET /calendarios/{nome}` |

Controllers só convertem (records de entrada e saída) e chamam as portas de entrada; nenhuma regra de negócio. Parâmetros validados à mão (`@RequestParam MultiValueMap<String, String>`) contra a lista permitida de cada rota; desconhecido → 400. `du` inteiro ≥ 1; `data` posterior à data-base (qualquer dia corrido); até 5.000 prazos na ordem recebida; `formato` `json|xlsx|zip`; `forcarRecalculo` `true|false`; `fonte`+`produto` juntos. Tempo total da requisição: `engine.timeout.requisicao-segundos` (`executorVirtual.submit(...).get(timeout)`).

### 13.2 Erros, correlação e JSON

- `FiltroCorrelacao` (`OncePerRequestFilter`, primeiro na cadeia): `X-Correlation-Id` recebido ou `UUID.randomUUID()`, no MDC e em toda resposta.
- Erros: o tratador do serviço (seção 1.5), com os acréscimos de `correlationId`, `detalhes` e 409. Nada de tratador novo além do que o serviço já tem. Tempo esgotado da requisição → `InfrastructureException(ERRO_INTERNO)`; qualquer exceção não prevista cai no tratamento padrão do Spring, sem stack trace na resposta.
- JSON: `BigDecimal` como string plana, datas `AAAA-MM-DD`, instantes `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, `avisos` sempre presente. A configuração tem de estar **no mapper que o Spring MVC usa**: o Spring Boot 4 usa Jackson 3 (`tools.jackson`: `withConfigOverride(BigDecimal.class, o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))` + `StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN`), e um `Module` ou customizador de Jackson 2 (`com.fasterxml.jackson.databind`) que o serviço já tenha é ignorado. Conferir onde o serviço configura o Jackson e pôr ali. Teste de rota: um fator com 16 casas sai como string.

### 13.3 Sem autenticação; `acionadoPor` e `usuario`

O engine não tem Spring Security, `SegurancaConfig` nem validação de token: **Proibido** acrescentar o Resource Server, `JwtDecoder`, filtro de autorização ou qualquer verificação de papel. Quem autentica o usuário é o `services/curves` (e o BFF), que chama o engine.

- `acionadoPor`: vem da rota (o controller de `POST /cargas` passa `CARGA`, o de `POST /construcoes/{dataBase}` passa `CONSTRUCAO_DATA`, e os demais `API`); não há como o chamador escolher.
- `usuario`: cada controller lê o cabeçalho opcional (`@RequestHeader(name = "X-Usuario", required = false)`) e o passa ao caso de uso, que o grava no `usuario` do `CURVA_GRAVADA` e em `cUsuarCalc`; ausente, é nulo (a coluna `cUsuarCalc` aceita nulo). O engine não valida o valor. Só o envio e a ativação de script (segunda parte) exigem o cabeçalho (400 `PARAMETRO_INVALIDO` sem ele), porque o `estado.json` grava `autor` e `aprovador`.

### 13.4 `GET /valores-cadastro`

Gerado da `TabelaParametros` (5.3) e dos enums, listados um a um com referência de método, sem interface:

```java
record ItemCatalogo(String valor, String rotulo, String descricao) {}
<E extends Enum<E>> List<ItemCatalogo> itens(Class<E> tipo) {          // textos do MessageSource (seção 1.5)
  return Arrays.stream(tipo.getEnumConstants()).map(v -> new ItemCatalogo(v.name(),
      mensagens.getMessage("poc.valores." + tipo.getSimpleName() + "." + v.name() + ".rotulo", null, Locale.of("pt", "BR")),
      mensagens.getMessage("poc.valores." + tipo.getSimpleName() + "." + v.name() + ".descricao", null, Locale.of("pt", "BR")))).toList();
}
// catalogo.put("unidade", itens(Unidade.class));
// catalogo.put("codigoErro", itens(CodigoErro.class)); ... um por enum (erros: poc.errors.title.<CODIGO>)
```

Além disso: modelos por tipo (nativos + Groovy `ATIVA`), com fonte/produto e papéis; catálogos de enums de resposta, avisos e erros; `versaoValores` = SHA-256 do JSON com chaves ordenadas.

### 13.5 Remover do engine antigo

`CurvaConstrucaoController`, `CurvaCalculoController`, `ModeloUploadController`, `CalcularCurvaService` (a consulta de pontos e a interpolação ficam em serviços novos com nome de caso de uso, `ConsultarPontosService` e `InterpolarCurvaService`), DTOs antigos, `domain/pipeline/**`, `domain/strategy/**`, `domain/service/CurvaInterpolacaoDomainService`, `domain/service/GroovyDynamicModelCompiler`, `domain/model/**` sem uso, `domain/calendar/**`, `MtrizCurvaEntity`/repositório, `ParmConfgCurvaEntity`/repositório, `CurvaJpaPersistenceAdapter`, `CalcularCurvaUseCase`, `ConstruirCurvaUseCase`, `CurvaPersistencePort` e os serviços antigos. Ao final, busca sem referências a `MetodoInterpolacao`, `PoliticaExtrapolacao`, `ComposableCurveBuilder`, `CurveBuilderRegistry`, `BusinessCalendar`, `B3BusinessCalendar`, `CalcularCurvaService`, `tMtrizCurva`, `dtVerticeReferencia`, e nenhum arquivo `.java` vazio sobrando das pastas apagadas.

---

## 14. Auditoria, logs, métricas e resiliência

### 14.1 Eventos (`adapter/out/log/EventosLogAdapter`, JSON com `correlationId`)

| Evento | Nível | Campos |
|---|---|---|
| `CURVA_GRAVADA` | AVISO | `idAuditoria`, `codigo`, `nome`, `dataBase`, `operacao` (`CONSTRUCAO`/`RECONSTRUCAO`), `acionadoPor`, `usuario`, `instante`, `idCarga`, `hashPontos`, `hashPontosAnterior`, `quantidadePontos`, `pontosAnteriores`, `origem`, `proveniencia` |
| `CONSTRUCAO_CONCLUIDA` / `CONSTRUCAO_FALHOU` / `INSUMO_DESCARTADO` / `SIMULACAO_EXECUTADA` | INFO / ERRO / AVISO / INFO | spec `curve-build-pipeline` |
| `CARGA_RECEBIDA`, `CARGA_PROCESSADA`, `PONTOS_DIFERENTES_DA_FONTE` (AVISO), `CONSTRUCAO_DATA_RECEBIDA`, `CONSTRUCAO_DATA_PROCESSADA`, `INTERPOLADA_REGRAVADA` | | specs `curve-load-trigger` e `curve-engine-api` |
| `REQUISICAO_CONCLUIDA`, `DEPENDENCIA_CHAMADA` (DEBUG), `DEPENDENCIA_LENTA` (AVISO), `DEPENDENCIA_FALHOU` (ERRO), `TEMPO_ESGOTADO` | | spec `curve-engine-resilience` |

Cada evento é um record serializado pela biblioteca de log JSON que o serviço já usa (no poc, `logstash-logback-encoder`; no real, conferir, pode ser a biblioteca da empresa); conferir que os campos saem estruturados no JSON. Nunca token, connection string, script ou corpo inteiro; lista de pontos só em `CURVA_GRAVADA`.

### 14.2 Métricas (Micrometer)

`engine_construcao_total{codigo,situacao}`, `engine_construcao_falha_total{codigo,codigoErro}`, `engine_construcao_duracao_segundos`, `engine_interpolacao_duracao_segundos`, `engine_dependencia_duracao_segundos{dependencia,operacao}`, `engine_tempo_esgotado_total{dependencia}`, `engine_estado_script_idade_segundos` (gauge), `engine_pontos_diferentes_fonte_total{codigo}`.

### 14.3 Tempos limite e prontidão

Tabela da spec `curve-engine-resilience`, com as propriedades da seção 1.2. Gravação no banco e `estado.json` nunca repetem. `readiness`: banco e, só na espera de subida, o estado dos scripts; `health` mostra o Blob como informativo.

### 14.4 Versão do engine

`BuildProperties.getVersion()` (meta `build-info`), na proveniência, no `CURVA_GRAVADA`, no `Resumo` e no manifesto.

---

## 15. Ordem de implementação (uma tarefa de `tasks.md` por vez; `mvn -q compile` ao fim de cada uma)

### 15.1 Primeira parte (change `engine-construcao-curvas`)

1. Seções 1, 2, 3, 4 (tarefas 1.x, 2.x e 15.1). Na seção 1, **pular** o que é da segunda parte: no `pom.xml`, nada de Blob, `azure-identity` nem POI; no `application.yml`, nada de `engine.blob` nem `engine.groovy`.
2. Seção 6 (3.x). Seção 5 (4.1, 4.1b, 4.2, 4.3; a 5.4, origem secundária, é da segunda parte).
3. Seção 7.6 (6.2). Seção 8 (7.x): o contrato recebe a `MemoriaCalculo` da seção 10.1, só como acumulador, sem planilha.
4. Seção 7 (8.1, 8.3, 8.2b, 8.4, 8.5, 8.6; a 8.2, simulação como rota, é da segunda parte). Seção 9 (9.1, 9.2, 9.3, 9.3b, 9.4; a 9.5, derivadas, é da segunda parte). Leitura em `READ COMMITTED` (10.3).
5. Seção 13 (12.0, 12.1, 12.2, 12.4; sem a gestão de scripts). Seção 12, só a exportação de feriados em JSON (14.4).
6. Seção 13.5 (remoções) e as correções 17.1, 17.2, 17.4, 17.6 a 17.10; `mvn compile` limpo.
7. Seção 16, só os testes do que foi feito (os de Groovy, Blob, planilha, auditoria e resiliência ficam para a segunda parte). Tarefas 16.1 e 16.2.

### 15.2 Segunda parte (change `engine-modelos-curva`)

1. Seção 1, o que ficou: dependências de Blob, `azure-identity` e POI; `engine.blob`, `engine.groovy`.
2. Seção 5.4 (4.2b). Seção 11 (5.x). Seção 10.1 completa (6.1).
3. Seção 7 (8.2, 8.3b). Seção 9.3 (9.5). Seção 14.1 (10.1). Seção 13 (10.4, 12.3). Seções 10.2/10.3 (13.x, 14.6). Seção 12 (14.1–14.3, e a planilha e a `versao` da 14.4). Seções 14.2–14.4 (11.x, 14.5).
4. Correções 17.3 e 17.5; testes da segunda parte (seção 16); homologação (seção 17).

---

## 16. Testes (ao final)

Ordem: **verificar, adaptar, criar, rodar**. Só o que já existe no pom (`spring-boot-starter-test`, ArchUnit). **Sem banco nem Blob reais**: portas de saída com Mockito ou implementações em memória (`ScriptsPort` em memória com ETag simulado). O domínio, por ser Java puro, é testado sem Spring (`new` direto).

### 16.1 Verificar

1. `mvn -q compile` limpo.
2. Buscas sem resultado em `src/main`: `NOLOCK`, `READ_UNCOMMITTED`, `synchronized`, `double ` em `domain/` (fora do `DecimalMath`), e a expressão regular `Math\.(pow|log|exp)\(` (tem de ser `StrictMath`; o `` evita casar com `StrictMath.`), `MetodoInterpolacao`, `PoliticaExtrapolacao`, `tMtrizCurva`, `dtVerticeReferencia`, `class Relogio`, `class Period`, `class Arredondamento`, `TimeUnit {` (enum próprio).

### 16.2 Recursos de teste

- `src/test/resources/TaxaSwap_20260914.txt`: cópia de `docs/TaxaSwap.txt`.
- `FixturesCadastro`: as 7 curvas como nas specs de modelo e no `exemplo-cadastro-7-curvas.txt`.
- `FixturesB3`: lê o `TaxaSwap` e devolve os `B3CurvaPrimaria` que o `CurvaPrimariaPort` simulado entrega para `DIxPRE`, `Cupom limpo de dólar`, `Cupom Limpo DI X IPCA`, `IBOVESPA` e `PTAX - USD`.
- Massa de 12 meses de `TaxaSwap` (tarefa 15.2) em `src/test/resources/massa-b3/`.
- Fuso nos testes, como no `main`: `src/test/resources/junit-platform.properties` com `junit.jupiter.extensions.autodetection.enabled=true` e uma `TimeZoneExtension` (registrada em `src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension`) que faz `TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))` no `beforeAll`.

### 16.3 Criar

| Teste | Casos |
|---|---|
| `DecimalMathTest` | `pow`, `ln`, `exp` contra valores de referência (erro relativo < `1e-14`); expoente inteiro exato pelo `BigDecimal.pow`; mesma entrada dá o mesmo resultado bit a bit |
| `ArredondamentoTest` | `setScale` com o `RoundingMode` do cadastro: 5.43219876 → 5.4321987 (`DOWN`, 7); 5.123456789 → 5.12345679 (`HALF_UP`, 8) |
| `QuantlibTest` | constantes e valores de `Frequency`; `Periodos.parse` válido e inválido; `DayCounter` com `(B,d]` e 30/360 (110, seção 0.3) |
| `InterestRateTest` | 1.139; 1.0125; ida e volta taxa→fator→taxa igual depois do arredondamento cadastrado |
| `BrazilTest`, `UnitedStatesTest` | Páscoa e feriados de 2026 e 2027 (seção 0.3); DU dos 278 vértices da `PRE` = publicado; `adjust` nas 7 convenções |
| `InterpoladoresTest` | valor exato nos nós; `LogLinear` com `y <= 0`; `Cubic` com 2 pontos = linear |
| `ManualB3Test` | 1.4.2, 1.4.3, 1.4.4, 1.4.5, 1.4.11 e 1.4.6–1.4.10 implementados direto no teste |
| `EixoDiasUteisTest` | quatro cenários do requisito de dias úteis publicados; calendário certo = calendário puro |
| `PreparacaoPontosTest` | `2026-12-24` e `2026-12-25`; ponto sozinho no feriado; fora de ordem; na data-base |
| `CurvaInterpoladaTest` | vetores de `2030-06-10` (valores exatos nas 7 casas) e fatores com tolerância `1e-14` (seção 0.3); domínio; `FlatValue` da `INP`; `PTX` depois do último ponto |
| `InterpolacaoDadoCurvaTest` | `PRE`: 12.390 dias, fim de semana = sexta, cada dia = `avaliar` da mesma data |
| `HashPontosTest` | vetor comum e os 5 `hashPontos`; valor com 12 casas dá o mesmo hash |
| `ValidadorCadastroTest` | um caso por regra de `CADASTRO_INVALIDO`; `'ATIVO' + espaços`; `business252` recusado; `MODELOS_POR_ORIGEM` sem provedor ignorado |
| `ProntaTsB3Test`, `SofrZeroBloombergTest`, `NtnbBootstrapAnbimaTest` | tabelas "Regras do arquivo"; `15M` → `2027-12-14`; bootstrap sintético que recupera uma curva zero conhecida |
| `ConstruirCurvaServiceTest` (portas simuladas) | `PRE`: 278 pontos e 12.390 linhas enviados às portas; recálculo; `Existente` com e sem `PONTOS_DIFERENTES_DA_FONTE`; `dBaseReft` não retrocede; falha no commit sem `CURVA_GRAVADA`; trava → `CONSTRUCAO_EM_ANDAMENTO`; `CALENDARIO_DIVERGENTE` |
| `RegravarInterpoladaServiceTest`, `ProcessarCargaServiceTest`, `ConstruirDataServiceTest`, `OrigemSecundariaTest` | cenários das specs `curve-engine-api`, `curve-load-trigger` e `curve-build-pipeline`; no paralelo, uma curva lenta estourando o prazo sem afetar as outras |
| `ParaleloTest` | limite do `Semaphore` respeitado (nunca mais que `limite` tarefas ao mesmo tempo); tempo esgotado cancela só as atrasadas; exceção de uma tarefa vira `Falha` só dela |
| `ResolverModelosTest` (`ScriptsPort` em memória) | resolução; duas instâncias; ativação concorrente; hash adulterado; Blob parado; circuito |
| `CarregadorGroovyTest` | válido; tipo errado; rede → reprovado; laço → `MODELO_FALHOU`; `LogLinear` sobrescrevendo só `valorNoSegmento`; script com `DayCounter.Business252` e `new Brazil()` |
| `PlanilhaPoiAdapterTest`, `CalendarioPlanilhaTest` | abas, cabeçalhos, tipos de célula; ida e volta do `Brazil` 2001–2100; mesma planilha → mesmo hash |
| `ApiContratoTest` (MockMvc) | um por `codigoErro`; `X-Usuario` presente e ausente; `X-Correlation-Id`; fator com 16 casas como string; parâmetro desconhecido 400; `data` em sábado |
| `FusoTest` | `OffsetDateTime.ofInstant(Instant.parse("2026-09-15T01:30:00Z"), ZoneId.systemDefault())` → `2026-09-14T22:30-03:00`; `conferirFuso` falha com outro fuso |
| `ValoresCadastroTest` | ida e volta com o validador; Groovy ativo aparece; todo valor com `rotulo` e `descricao` |
| `ArquiteturaTest` (ArchUnit) | `domain` não depende de `org.springframework`, `jakarta.persistence`, `tools.jackson`, `com.azure`, `org.apache.poi`, `groovy`; `application` não depende de `adapter` |
| `PropriedadesTest` (`@ParameterizedTest` com 1.000 casos de `new Random(20260914)`) | ponto preservado; determinismo (bit a bit); ida e volta taxa↔fator igual depois do arredondamento cadastrado; monotonicidade de DU e DC |
| `OraculoB3Test` | massa de 12 meses: cada vértice devolve o `vPrecoTx`, sem `CALENDARIO_DIVERGENTE` |

### 16.4 Rodar

`mvn verify` passa sem banco nem Blob. Teste que falha e reflete a spec → corrigir o código.

### 16.5 Conferido na homologação (não é teste automatizado)

- trava: duas construções da mesma curva ao mesmo tempo não misturam pontos; a segunda espera até 30 s ou recebe `CONSTRUCAO_EM_ANDAMENTO`;
- consulta durante uma reconstrução devolve os pontos antigos ou os novos inteiros;
- `tDadoVertcCurva` e `tDadoCurva` gravadas juntas, com as quantidades da seção 0.3;
- ativação de script chegando às duas instâncias em até 30 s; ativação concorrente com `ESTADO_SCRIPT_CONCORRENTE`;
- construção da data com as 7 curvas em paralelo (virtual threads) sem esgotar o pool de conexões;
- a seção 17.

## 17. Verificação ponta a ponta (na homologação)

1. Banco com o `001_SCRIPT_INICIAL.sql`, o cadastro das 7 curvas e `tBtrsCurvaPrimr` do `TaxaSwap` de `2026-09-14`.
2. `POST /api/v1/cargas` com `B3`/`TS` e 278 por código → as 5 curvas construídas, `hashPontos` iguais aos da seção 0.3.
3. `GET /api/v1/curvas/PRE/2026-09-14/interpolacao?data=2030-06-10` → 14.0624222, igual a `tDadoCurva` em `2030-06-10`.
4. Alterar um ponto direto em `tDadoVertcCurva` → consulta com `INTERPOLADA_DESATUALIZADA` → `POST .../interpolada` → atualizada.
5. `POST /api/v1/construcoes/2026-09-14` → as 5 `EXISTENTE`, NTN-B e SOFR `SEM_INSUMO`.
6. Baixar `formato=xlsx` e `formato=zip` da simulação da `PRE`.
7. `openspec validate engine-construcao-curvas --strict` e `openspec validate engine-modelos-curva --strict`.
