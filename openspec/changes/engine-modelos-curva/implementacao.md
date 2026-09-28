# Guia de implementação: engine-modelos-curva

Guia passo a passo para aplicar esta change com o mínimo de decisões. **A spec manda; este guia diz onde e como.** Siga as seções na ordem. Cada seção lista os arquivos, as assinaturas, o código das partes difíceis e os casos de teste. Os testes são escritos só na seção 16, depois de tudo compilar.

## 0. Regras para quem implementa

- Pacote base: `br.com.poc` (em `services/engine/src/main/java/br/com/poc/`). Java 21, Spring Boot 4 (Jackson 3, pacote `tools.jackson`).
- Não invente rota, classe pública, código de erro, código de aviso, coluna ou tabela fora deste guia e das specs. Não crie tabela, sequência, índice nem tópico.
- **Todo número de curva é `BigDecimal`.** Nunca `double`, nem em passo intermediário (exceção única: o chute inicial de `DecimalMath.ln`, seção 2).
- Mensagens para o usuário em pt-BR, com acentuação, UTF-8. Códigos (enums, `codigoErro`, avisos) não se traduzem.
- "Hoje" e instantes só pelo `Relogio` (seção 1.4). Proibido `LocalDate.now()`, `Instant.now()`, `ZoneId.systemDefault()` fora dele.
- Leitura só em `READ COMMITTED`. Proibido `NOLOCK` e `READ UNCOMMITTED`.
- Arquivos de configuração que já existem no repositório real (`pom.xml`, `application*.yml`, `logback.xml`): **não reescrever**. Verificar se têm o que este guia pede e acrescentar só o que faltar.
- O serviço roda em no mínimo 2 instâncias: nenhum estado de negócio em memória local, além dos caches descritos aqui.

### 0.1 Papel das tabelas (não confundir)

| Tabela | Conteúdo | Quem grava |
|---|---|---|
| `tDadoVertcCurva` | **curva construída**: os pontos (vértices), com `cDiaUtil`, `cQtdDiaPer`, `cQtdDiaReft`, `vFatorAcum`, `vFatorDia`, `vPrecoTx` | engine; `services/curves` na edição manual |
| `tDadoCurva` | **curva interpolada**: um `vPrecoTx` por dia corrido | só o engine (o curves só apaga) |
| `tCurvaMercd` | cadastro da curva; o engine escreve só `dBaseReft` e `cUsuarCalc` | curves e engine |
| `tCurvaPrvdr`, `tConfgCurva` | origens e configuração vigente | curves |
| `tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr` | brutos das fontes | processor e feeders |

Pontos, dias úteis dos pontos e `hashPontos` vêm **sempre** de `tDadoVertcCurva`. `tDadoCurva` nunca é lida para calcular.

### 0.2 Vetores de teste reais (usar nos testes)

Fonte: `docs/TaxaSwap.txt` (data-base `B` = `2026-09-14`), valores conferidos por uma reimplementação independente das fórmulas da spec:

| Item | Valor esperado |
|---|---|
| vértices por curva (`PRE`, `DCL`, `DPL`, `INP`, `PTX`) | 278, de `2026-09-15` a `2060-08-16` (`cDiaCorri` 12.390, `cDiaUtil` 8.496) |
| primeiro valor | `PRE` 13.9; `DCL` -117.96; `DPL` 18.59; `INP` 185648; `PTX` 5.1696 |
| último valor | `PRE` 14.16; `DCL` 20.13; `DPL` 7.19; `INP` 233414; `PTX` 56.3772259 |
| vértice de 7.406 DU da `PRE` | `2056-04-10`, 14.167 |
| `DU` do calendário `Brazil` nos 278 vértices da `PRE` | igual ao `cDiaUtil` publicado em todos (0 divergências) |
| `hashPontos` da `PRE` | `7c4982b34ca35f784863118902e63f28e7d49362d940cc27eb77961d526fca20` |
| `hashPontos` da `DCL` | `0504d24e90556dab534e53ec6b81740c99baef65829ff00cb1f61ced388aaf40` |
| `hashPontos` da `DPL` | `82c7d7ffcc7f4bbad9ca64a8dd611ea528cb10764f0782f456164def0dd8977a` |
| `hashPontos` da `INP` | `f08705dd681da17324ee8f7264be41a949408fb14cecdcbb49700a30f3db741a` |
| `hashPontos` da `PTX` | `1b2bbfc82020d153483a47193561702b0cdc7f516193fe4c0c8137153810827b` |
| `hashPontos` do vetor comum com o curves (`2026-09-15;13.9\n2026-09-16;-117.96`) | `8dcff432fa5271ff16bdaef72940792811802e0a5ae59e8c2bf5cead818d5544` |
| interpolação em `2030-06-10` (DU 932, DC 1.365; vizinhos `2030-05-15` DU 914 e `2030-07-01` DU 946, `w` = 0.5625) | `PRE` 14.0624222; `DCL` 6.6547016; `PTX` 6.6901076 (`DOWN`) |
| fatores da `PRE` em DU 932 (valor 14.0624222) | acumulado 1.6268101753135545; diário 1.0005222620285954 |
| fatores da `DCL` em DC 1.365 (valor 6.6547016, `Actual360`/`Simple`) | acumulado 1.2523241023333333; diário (`FA^(1/932)`) 1.0002414466401755 |
| fator da `PRE` no 1º vértice (13.9, DU 1) | acumulado = diário = 1.0005166043641946 |
| curva interpolada da `PRE` | 12.390 linhas em `tDadoCurva`, de `2026-09-15` a `2060-08-16`; `2026-09-19` e `2026-09-20` iguais a `2026-09-18` |
| Páscoa (Meeus/Jones/Butcher) | 2026: `04-05` (Carnaval `02-16` e `02-17`, Sexta-feira Santa `04-03`, Corpus Christi `06-04`); 2027: `03-28` (Carnaval `02-08`/`02-09`, Sexta `03-26`, Corpus `05-27`) |
| `UnitedStates`/`FederalReserve` 2026 | `01-01`, `01-19`, `02-16`, `05-25`, `06-19`, `09-07`, `10-12`, `11-11`, `11-26`, `12-25` (4 de julho é sábado: não observado) |
| `UnitedStates` 2027 | `01-01`, `01-18`, `02-15`, `05-31`, `07-05` (4 de julho no domingo), `09-06`, `10-11`, `11-11`, `11-25` (19/06 e 25/12 no sábado: não observados) |
| tenor `15M` a partir de `2026-09-14` | `2027-12-14` |

---

## 1. Base: dependências, configuração, erros, relógio

### 1.1 `pom.xml`: o mínimo de coisas novas

Só três acréscimos, porque a spec os exige e não há como fazer sem eles:

| Acrescentar | Por quê |
|---|---|
| `org.springframework.boot:spring-boot-starter-oauth2-resource-server` (sem versão: vem do Spring Boot) | validar o JWT do Entra ID e ler os papéis |
| `com.azure:azure-storage-blob` e `com.azure:azure-identity`, sem versão própria, pelo `com.azure:azure-sdk-bom` **1.3.8** importado em `dependencyManagement` (`<type>pom</type>`, `<scope>import</scope>`) | scripts Groovy no Blob, por Managed Identity |
| `org.apache.poi:poi-ooxml` **5.5.1** | planilhas `.xlsx` (memória de cálculo, auditoria, feriados) |

No `spring-boot-maven-plugin` (já existe), acrescentar a meta `build-info` (versão do artefato na proveniência).

Não remover nada do que já está no pom: o objetivo é o mínimo de coisas **novas**. As dependências que a engine nova deixa de usar (`commons-math3`, `co.dv01.jquantlib:core`, Feign, cache e Redis) ficam como estão; o código novo só não as usa.

Não acrescentar nenhuma dependência de teste: os testes usam só o que vem no `spring-boot-starter-test` (JUnit 5, inclusive testes parametrizados, Mockito, AssertJ, MockMvc) e o `archunit-junit5` que já existe. O `groovy` 4.0.25 fica como está.

### 1.2 `application.yml` (verificar e acrescentar)

```yaml
engine:
  seguranca:
    emissor: ${ENGINE_JWT_EMISSOR}          # https://login.microsoftonline.com/{tenant}/v2.0
    audiencia: ${ENGINE_JWT_AUDIENCIA}      # api://{app do engine}
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
  datasource:
    hikari:
      connection-timeout: ${engine.timeout.banco-conexao-ms}
      transaction-isolation: TRANSACTION_READ_COMMITTED
  jdbc:
    template:
      query-timeout: 30s
  jpa:
    open-in-view: false
    properties:
      hibernate:
        jdbc:
          batch_size: 1000
        order_inserts: true
        order_updates: true
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${engine.seguranca.emissor}
          audiences: ${engine.seguranca.audiencia}
```

Perfil `local` (e só ele): `engine.blob.connection-string` para o Azurite. O perfil de produção MUST NOT aceitar connection string (seção 11.2).

### 1.3 Erros e avisos: `domain/curva/CodigoErro.java`, `CodigoAviso.java`, `ErroEngine.java`, `Aviso.java`, `Detalhe.java`

```java
public enum CodigoErro {
  PARAMETRO_INVALIDO(400), NAO_AUTENTICADO(401), SEM_PERMISSAO(403),
  CURVA_NAO_ENCONTRADA(404), CURVA_NAO_CONSTRUIDA(404),
  CODIGO_DUPLICADO(409), NOME_AMBIGUO(409), CONSTRUCAO_EM_ANDAMENTO(409), ESTADO_SCRIPT_CONCORRENTE(409),
  CADASTRO_INVALIDO(422), CURVA_MAE_NAO_CONSTRUIDA(422), INSUMO_INCOMPLETO(422), INSUMO_AUSENTE(422),
  INSUMO_INVALIDO(422), PONTOS_NAO_INTERPOLAVEIS(422), PRAZO_FORA_DO_DOMINIO(422), MODELO_FALHOU(422),
  SCRIPT_INVALIDO(422), ERRO_INTERNO(500), BLOB_INDISPONIVEL(503);
  public final int http; CodigoErro(int http) { this.http = http; }
}

public enum CodigoAviso {
  CURVA_INATIVA, FORA_DA_VIGENCIA_CURVA, PONTOS_DIFERENTES_DA_FONTE, PONTO_DESCARTADO_MESMO_PRAZO,
  PONTO_DESCARTADO_PRAZO_NAO_POSITIVO, CALENDARIO_DIVERGENTE, CALCULO_GRAVADO_DIVERGENTE, SEM_CALCULO_GRAVADO,
  INTERPOLADA_DESATUALIZADA, ORIGEM_SECUNDARIA, ESTADO_SCRIPT_DESATUALIZADO, ESTADO_SCRIPT_DESCONHECIDO
}

public record Detalhe(String campo, Integer linha, String valor, String motivo) {}
public record Aviso(CodigoAviso codigo, String mensagem, List<Detalhe> detalhes) {}

public class ErroEngine extends RuntimeException {
  public final CodigoErro codigo; public final List<Detalhe> detalhes;
  public ErroEngine(CodigoErro codigo, String mensagem, List<Detalhe> detalhes) { super(mensagem); this.codigo = codigo; this.detalhes = List.copyOf(detalhes); }
  public ErroEngine(CodigoErro codigo, String mensagem) { this(codigo, mensagem, List.of()); }
}
```

Toda falha de negócio é `ErroEngine`. As classes antigas de `application/exception/` continuam só se outro código ainda as usar; o handler novo (seção 13.2) trata `ErroEngine` primeiro.

### 1.4 `domain/tempo/Relogio.java`

```java
@Component
public class Relogio {
  public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
  private final Clock clock;
  public Relogio() { this(Clock.system(BRASILIA)); }
  public Relogio(Clock clock) { this.clock = clock.withZone(BRASILIA); }   // testes injetam Clock fixo
  public LocalDate hoje() { return LocalDate.now(clock); }
  public OffsetDateTime agora() { return OffsetDateTime.now(clock); }       // sempre com -03:00
  public String carimboArquivo() { return agora().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")); }
}
```

Serialização de instante: `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` (ex.: `2026-09-14T21:30:00.000-03:00`).

---

## 2. Matemática decimal: `domain/matematica/`

### 2.1 `DecimalMath.java`

Precisão interna de 60 dígitos; resultado em `MathContext.DECIMAL128`. Potência inteira é exata; fracionária é `exp(b·ln a)`.

```java
public final class DecimalMath {
  public static final MathContext MC = MathContext.DECIMAL128;
  private static final MathContext I = new MathContext(60, RoundingMode.HALF_EVEN);
  private static final BigDecimal EPS = new BigDecimal("1e-58");

  public static BigDecimal exp(BigDecimal z) {
    // redução: z/2^k com |z| < 0.5, série de Taylor, depois k quadraturas
    int k = 0; BigDecimal r = z;
    while (r.abs().compareTo(new BigDecimal("0.5")) > 0) { r = r.divide(BigDecimal.TWO, I); k++; }
    BigDecimal soma = BigDecimal.ONE, termo = BigDecimal.ONE;
    for (int n = 1; n < 400; n++) {
      termo = termo.multiply(r, I).divide(BigDecimal.valueOf(n), I);
      soma = soma.add(termo, I);
      if (termo.abs().compareTo(EPS) < 0) break;
    }
    for (int i = 0; i < k; i++) soma = soma.multiply(soma, I);
    return soma.round(MC);
  }

  public static BigDecimal ln(BigDecimal x) {
    if (x.signum() <= 0) throw new ArithmeticException("ln de valor não positivo: " + x);
    // Newton-Halley: y ← y + 2(x − e^y)/(x + e^y), chute pelo double (só o chute)
    BigDecimal y = new BigDecimal(Math.log(x.doubleValue()));
    for (int i = 0; i < 100; i++) {
      BigDecimal ey = expInterno(y);
      BigDecimal delta = BigDecimal.TWO.multiply(x.subtract(ey, I), I).divide(x.add(ey, I), I);
      y = y.add(delta, I);
      if (delta.abs().compareTo(EPS) < 0) break;
    }
    return y.round(MC);
  }

  public static BigDecimal pow(BigDecimal a, BigDecimal b) {
    if (b.signum() == 0) return BigDecimal.ONE;
    if (b.stripTrailingZeros().scale() <= 0 && b.abs().compareTo(BigDecimal.valueOf(999_999)) <= 0) {
      return a.pow(b.intValueExact(), MC);                 // expoente inteiro: exato
    }
    if (a.signum() <= 0) throw new ArithmeticException("potência fracionária de valor não positivo: " + a);
    return exp(b.multiply(ln(a), I));
  }
  // expInterno = exp sem o round(MC) final (mesmo código, retorna 'soma')
}
```

`ln` para `x` fora da faixa do `double` (acima de 1e308) não ocorre em curvas; se ocorrer, `ArithmeticException` → `ERRO_INTERNO`.

### 2.2 `Arredondamento.java`

```java
public enum ModoArredondamento { HALF_UP, HALF_EVEN, DOWN;
  public RoundingMode java() { return switch (this) { case HALF_UP -> RoundingMode.HALF_UP; case HALF_EVEN -> RoundingMode.HALF_EVEN; case DOWN -> RoundingMode.DOWN; }; } }
public record Arredondamento(int casas, ModoArredondamento modo) {
  public BigDecimal aplicar(BigDecimal v) { return v.setScale(casas, modo.java()); }
  public static BigDecimal fator16(BigDecimal v) { return v.setScale(16, RoundingMode.HALF_UP); }
}
```

---

## 3. Tipos QuantLib: `domain/quantlib/`

Nomes exatos (PascalCase nas constantes, como o QuantLib):

```java
public enum Compounding { Simple, Compounded, Continuous, SimpleThenCompounded, CompoundedThenSimple }
public enum Frequency { NoFrequency(-1), Once(0), Annual(1), Semiannual(2), EveryFourthMonth(3), Quarterly(4),
  Bimonthly(6), Monthly(12), EveryFourthWeek(13), Biweekly(26), Weekly(52), Daily(365), OtherFrequency(999);
  public final int valor; Frequency(int v) { valor = v; } }
public enum BusinessDayConvention { Following, ModifiedFollowing, Preceding, ModifiedPreceding, Unadjusted, HalfMonthModifiedFollowing, Nearest }
public enum TimeUnit { Days, Weeks, Months, Years }
public record Period(int n, TimeUnit unidade) {
  private static final Pattern P = Pattern.compile("^([1-9][0-9]*)([DWMY])$");
  public static Period parse(String s) { /* casa P; senão IllegalArgumentException; D→Days W→Weeks M→Months Y→Years */ }
  public LocalDate somarCorrido(LocalDate d) { /* Days: plusDays; Weeks: plusWeeks; Months: plusMonths; Years: plusYears (Java já leva ao último dia do mês) */ }
}
```

### 3.1 `DayCounter` (interface) e implementações

```java
public interface DayCounter { String nome(); BigDecimal fracaoAno(int du, int dc, LocalDate b, LocalDate d); }
// Business252: du/252 | Actual360: dc/360 | Actual365Fixed: dc/365 | Thirty360: dias30360(b,d)/360
```

Divisões com `DecimalMath.MC`. `Business252` recebe o `du` já resolvido (ancorado, seção 6.3); não conta sozinho.

`dias30360(b, d)` (Bond Basis, USA): `d1 = min(b.dia, 30)`; `d2 = (d.dia == 31 && d1 == 30) ? 30 : d.dia`; resultado `360·(a2−a1) + 30·(m2−m1) + (d2−d1)`. É também `cQtdDiaReft`.

### 3.2 `InterestRate.java` (cotação)

```java
public record InterestRate(DayCounter dc, Compounding comp, Frequency freq) {
  // r = valor/100; τ = fração de ano
  public BigDecimal fator(BigDecimal valorPercentual, BigDecimal tau) {
    BigDecimal r = valorPercentual.divide(BigDecimal.valueOf(100), DecimalMath.MC);
    BigDecimal fa = switch (comp) {
      case Simple -> BigDecimal.ONE.add(r.multiply(tau, DecimalMath.MC));
      case Compounded -> { BigDecimal f = BigDecimal.valueOf(freq.valor);
        yield DecimalMath.pow(BigDecimal.ONE.add(r.divide(f, DecimalMath.MC)), f.multiply(tau)); }
      case Continuous -> DecimalMath.exp(r.multiply(tau, DecimalMath.MC));
      default -> throw new ErroEngine(CodigoErro.CADASTRO_INVALIDO, "Cotação não suportada: " + comp);
    };
    if (fa.signum() <= 0) throw new ErroEngine(CodigoErro.MODELO_FALHOU, "Fator acumulado não positivo: " + fa);
    return fa;
  }
  public BigDecimal taxa(BigDecimal fa, BigDecimal tau) { // inversa exata, devolve em percentual
    BigDecimal r = switch (comp) {
      case Simple -> fa.subtract(BigDecimal.ONE).divide(tau, DecimalMath.MC);
      case Compounded -> { BigDecimal f = BigDecimal.valueOf(freq.valor);
        yield DecimalMath.pow(fa, BigDecimal.ONE.divide(f.multiply(tau), DecimalMath.MC)).subtract(BigDecimal.ONE).multiply(f); }
      case Continuous -> DecimalMath.ln(fa).divide(tau, DecimalMath.MC);
      default -> throw new IllegalStateException();
    };
    return r.multiply(BigDecimal.valueOf(100));
  }
}
```

Casos: 13,9 com DU 252 em `Business252`/`Compounded`/`Annual` → 1.139; 5,000 com DC 90 em `Actual360`/`Simple` → 1.0125.

---

## 4. Calendários: `domain/calendario/`

### 4.1 Base `Calendar.java`

```java
public abstract class Calendar {
  private final ConcurrentHashMap<Integer, Set<LocalDate>> cache = new ConcurrentHashMap<>();
  public abstract String nome(); public abstract String mercado();
  protected abstract Set<LocalDate> feriados(int ano);                  // ponto de extensão
  public boolean isBusinessDay(LocalDate d) {
    DayOfWeek w = d.getDayOfWeek();
    return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !cache.computeIfAbsent(d.getYear(), this::feriados).contains(d);
  }
  public int diasUteis(LocalDate de, LocalDate ate) {                  // quantidade em (de, ate]; 0 se ate <= de
    int n = 0; for (LocalDate d = de.plusDays(1); !d.isAfter(ate); d = d.plusDays(1)) if (isBusinessDay(d)) n++; return n;
  }
  public LocalDate advance(LocalDate d, int n) {                        // n dias úteis para frente (n >= 0)
    LocalDate r = d; int k = 0; while (k < n) { r = r.plusDays(1); if (isBusinessDay(r)) k++; } return r;
  }
  public LocalDate adjust(LocalDate d, BusinessDayConvention c) { /* regras QuantLib abaixo */ }
}
```

`adjust`: `Unadjusted` → `d`; `Following` → próximo útil (inclusive `d`); `Preceding` → anterior útil; `ModifiedFollowing` → `Following`, mas se mudar de mês, `Preceding`; `ModifiedPreceding` → `Preceding`, mas se mudar de mês, `Following`; `HalfMonthModifiedFollowing` → `ModifiedFollowing`, e se `d` ≤ dia 15 e o resultado passar do dia 15, `Preceding`; `Nearest` → o útil mais próximo, com empate indo para `Following`.

Um script Groovy (seção 11) sobrescreve só `feriados(int ano)`.

### 4.2 `brazil/Brazil.java` (nome `Brazil`, mercado `Settlement`)

```java
static LocalDate pascoa(int y) { // Meeus/Jones/Butcher
  int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
  int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
  int m = (a + 11 * h + 22 * l) / 451, mes = (h + l - 7 * m + 114) / 31, dia = ((h + l - 7 * m + 114) % 31) + 1;
  return LocalDate.of(y, mes, dia);
}
protected Set<LocalDate> feriados(int y) {
  LocalDate p = pascoa(y);
  Set<LocalDate> s = new HashSet<>(List.of(LocalDate.of(y,1,1), p.minusDays(48), p.minusDays(47), p.minusDays(2),
    LocalDate.of(y,4,21), LocalDate.of(y,5,1), p.plusDays(60), LocalDate.of(y,9,7), LocalDate.of(y,10,12),
    LocalDate.of(y,11,2), LocalDate.of(y,11,15), LocalDate.of(y,12,25)));
  if (y >= 2024) s.add(LocalDate.of(y,11,20));
  return s;
}
```

Constante estilo QuantLib: `Brazil.Market.Settlement` (enum interno com um valor) e construtor `new Brazil(Brazil.Market.Settlement)`.

### 4.3 `unitedstates/UnitedStates.java` (nome `UnitedStates`, mercado `FederalReserve`)

Datas fixas (1/1, 19/6 a partir de 2022, 4/7, 11/11, 25/12): no domingo → segunda seguinte; no sábado → não observado. Móveis: 3ª segunda de janeiro (a partir de 1998), 3ª segunda de fevereiro, última segunda de maio, 1ª segunda de setembro, 2ª segunda de outubro, 4ª quinta de novembro. Vetores 2026 e 2027 na seção 0.2. Constante: `UnitedStates.Market.FederalReserve`.

### 4.4 `CalendarioPorLista.java` (base dos calendários importados)

Campos: `nome`, `mercado`, `anoInicial`, `anoFinal`, `Set<LocalDate> datas`. `feriados(ano)` devolve as datas do ano; se `ano` fora da cobertura → `ErroEngine(MODELO_FALHOU, "Calendário {nome} v{versao}: data {d} fora da cobertura {anoInicial}–{anoFinal}")`. Também `isBusinessDay` checa a cobertura antes de tudo.

---

## 5. Cadastro: `domain/curva/CadastroCurva.java` e `adapter/out/persistence/jpa/CadastroJpaAdapter.java`

### 5.1 Modelo

```java
public enum Unidade { TAXA, PRECO, PONTOS }
public record Origem(String fonte, String produto, String codigoNaFonte, int prioridade) {}
public record Mae(String nome, String papel) {}
public record Parametros(String grandeza, String dayCounterTempo, Frequency frequency, String calendario,
  String mercadoCalendario, BusinessDayConvention convencao, String extrapolacaoInicio, String extrapolacaoFim,
  Period horizonte, Arredondamento arredondamento, Integer versaoScriptConstrucao, Integer versaoScriptInterpolacao,
  Integer versaoScriptCalendario, Map<String,String> modelosPorOrigem) {}
public record CadastroCurva(String codigo, String nome, Unidade unidade, String cNormaDia, Compounding cTpoJuro,
  boolean ativa, LocalDate inicioVigencia, LocalDate fimVigencia, List<Origem> origens /* ordem de prioridade */,
  List<Mae> maes /* vazia se não derivada */, int idConfiguracao, String modeloConstrucao, String interpolador,
  Parametros parametros, String jsonParametros) {
  public Origem origemPrincipal() { return origens.get(0); }
  public boolean derivada() { return "TCEN".equals(origemPrincipal().fonte()); }
}
```

### 5.2 Persistência (JPA; todas as colunas `CHAR`/`VARCHAR` com `stripTrailing()` na leitura)

Acesso por JPA, em `adapter/out/persistence/jpa/`: entidades `CurvaMercdEntity` (`tCurvaMercd`), `CurvaPrvdrEntity` (`tCurvaPrvdr`) e `ConfgCurvaEntity` (`tConfgCurva`, já existe: conferir as colunas contra o `001_SCRIPT_INICIAL.sql`), com repositórios Spring Data. As consultas abaixo são as que os repositórios SHALL executar; onde o JPQL não expressa exatamente, usar `@Query(nativeQuery = true)` com o SQL como está.

```sql
-- curva por código (0 → CURVA_NAO_ENCONTRADA; >1 → CODIGO_DUPLICADO)
SELECT cTickerIndcd, cTickerIdtfdUnic, cTpoVlr, cNormaDia, cTpoJuro, cSitReg, dInicVgcia, dValidAte, dBaseReft, cUsuarCalc
  FROM tCurvaMercd WHERE cTickerIdtfdUnic = ?;
-- curva por nome: ler todas com código não nulo e comparar em Java (normalizar = sem acento via Normalizer NFD
-- removendo \p{M}, minúsculo, strip()); 0 → CURVA_NAO_ENCONTRADA, >1 → NOME_AMBIGUO (detalhes: códigos e nomes)
SELECT cTickerIndcd, cTickerIdtfdUnic FROM tCurvaMercd WHERE cTickerIdtfdUnic IS NOT NULL;
-- origens
SELECT iPrvdrDados, cPrvdrMercd, cTickerPrvdr, cPriorCsumo FROM tCurvaPrvdr WHERE cTickerIndcd = ? ORDER BY cPriorCsumo, cldtfdUnic;
-- configuração vigente (precisa ser exatamente 1 linha; 0 ou >1 → CADASTRO_INVALIDO)
SELECT cldtfdConfg, cMotorCalc, cRotnaCalc, cModDado FROM tConfgCurva
 WHERE cTickerIndcd = ? AND dInicVgcia <= ? AND (dValidAte IS NULL OR dValidAte >= ?);
```

### 5.3 Validação (classe `ValidadorCadastro`, uma exceção `CADASTRO_INVALIDO` com um `Detalhe` por problema)

Aplicar **todas** as regras da spec `curve-build-pipeline` (requisito "Cadastro da curva e itens obrigatórios") nesta ordem, juntando os problemas antes de lançar:
1. `cModDado` é objeto JSON (Jackson `ObjectMapper.readTree`); cada chave pertence à tabela; tipo certo (string, número inteiro, ou objeto só em `MODELOS_POR_ORIGEM`).
2. Obrigatórios presentes; valores nas listas (comparação exata, com caixa). Padrão só para `EXTRAPOLACAO_INICIO` e `EXTRAPOLACAO_FIM` (`Disabled`).
3. `cTpoVlr` ∈ `TAXA|PRECO|PONTOS`; para `TAXA`, `cNormaDia` ∈ `Business252|Actual360|Actual365Fixed|Thirty360` e `cTpoJuro` ∈ `Simple|Compounded|Continuous` (os outros dois → inválido).
4. Combinações: `Price` só com `PRECO`/`PONTOS`, e o contrário; `FREQUENCY` só com `Compounded` (obrigatória nele), nunca `NoFrequency|Once|OtherFrequency`; `MERCADO_CALENDARIO` = mercado do calendário resolvido; `FlatForward` só com `Linear|LogLinear`; `CASAS_DECIMAIS` 0..12; `HORIZONTE` pelo `Period.parse`.
5. `MODELOS_POR_ORIGEM`: chave casa `^[^/]+/[^/]+$`, valor texto não vazio. Chave sem ligação ou da principal é ignorada (não é erro).
6. Modelo de construção resolvido (seção 11) aceita a fonte e o produto da origem usada.
7. Derivada (`TCEN`): mães existem, sem ciclo (DFS pelas ligações `TCEN`), papéis exatamente os declarados pelo modelo.

`cSitReg` ≠ `ATIVO` (inclusive nulo) → `ativa = false`. Situação e vigência **nunca** geram `CADASTRO_INVALIDO`.

### 5.4 Origem secundária (`fonte` e `produto` na construção e na simulação)

Os dois ou nenhum (só um → `PARAMETRO_INVALIDO`). Com eles: ligação com `iPrvdrDados = fonte` e `cPrvdrMercd = produto`; nenhuma ou mais de uma → `CADASTRO_INVALIDO` listando as origens; `fonte = TCEN` → `PARAMETRO_INVALIDO`. Modelo = `modelosPorOrigem.get(fonte + "/" + produto)`, senão `cMotorCalc`. Aviso `ORIGEM_SECUNDARIA` com fonte, produto, código na fonte e prioridade.

---

## 6. Interpolação: `domain/interpolacao/`

### 6.1 Contratos

```java
public interface Interpolador {                          // chamado só com xs[0] <= x <= xs[n-1]
  String nome();
  BigDecimal valor(BigDecimal x, List<BigDecimal> xs, List<BigDecimal> ys, MemoriaCalculo memoria);
}
public abstract class InterpoladorLocal implements Interpolador {
  public final BigDecimal valor(BigDecimal x, List<BigDecimal> xs, List<BigDecimal> ys, MemoriaCalculo m) {
    int i = segmento(x, xs);                              // maior i com xs[i] <= x, limitado a n-2
    if (x.compareTo(xs.get(i)) == 0) return ys.get(i);
    BigDecimal w = x.subtract(xs.get(i)).divide(xs.get(i + 1).subtract(xs.get(i)), DecimalMath.MC);
    return valorNoSegmento(w, ys.get(i), ys.get(i + 1));
  }
  public BigDecimal valorNoSegmentoPublico(BigDecimal w, BigDecimal yE, BigDecimal yD) { return valorNoSegmento(w, yE, yD); } // usado pelo FlatForward
  protected abstract BigDecimal valorNoSegmento(BigDecimal w, BigDecimal yEsquerda, BigDecimal yDireita);
}
```

| Classe (pacote) | `valorNoSegmento` |
|---|---|
| `linear/Linear` | `yE + w·(yD − yE)` |
| `loglinear/LogLinear` | `yE · pow(yD/yE, w)` (todos os `y` > 0, checado antes: senão `PONTOS_NAO_INTERPOLAVEIS` com os pontos) |
| `backwardflat/BackwardFlat` | `yD` |
| `forwardflat/ForwardFlat` | `yE` |

`cubic/Cubic` (implementa `Interpolador` direto): spline natural. Com `h_i = x_{i+1}−x_i`, resolver o sistema tridiagonal das segundas derivadas `M` (`M_0 = M_{n-1} = 0`; para `i = 1..n-2`: `h_{i-1}·M_{i-1} + 2(h_{i-1}+h_i)·M_i + h_i·M_{i+1} = 6·((y_{i+1}−y_i)/h_i − (y_i−y_{i-1})/h_{i-1})`) pelo algoritmo de Thomas em `BigDecimal`, e avaliar `S(x) = M_i(x_{i+1}−x)³/(6h_i) + M_{i+1}(x−x_i)³/(6h_i) + (y_i/h_i − M_i h_i/6)(x_{i+1}−x) + (y_{i+1}/h_i − M_{i+1} h_i/6)(x−x_i)`. Com 2 pontos, igual ao linear.

### 6.2 Grandeza (`Grandeza.java`, enum `Discount, CompoundFactor, ZeroYield, Price`)

Para um valor `v` num prazo com fração de ano `τ` da cotação: `Discount` → `y = 1/FA`; `CompoundFactor` → `y = FA`; `ZeroYield` → `y = v/100`; `Price` → `y = v`. Inversa no prazo pedido: `Discount` → `FA = 1/y`, `v = taxa(FA, τ)`; `CompoundFactor` → `v = taxa(y, τ)`; `ZeroYield` → `v = 100·y`; `Price` → `v = y`.

### 6.3 Dias úteis ancorados: `EixoDiasUteis.java` (requisito "Dias úteis publicados pela fonte ou informados pelo usuário")

```java
public record PontoGravado(LocalDate data, BigDecimal valor, Integer diasUteis /* cDiaUtil; null = calendário */,
  Integer diasCorridos, Integer dias30360, BigDecimal fatorAcum, BigDecimal fatorDia) {}

public final class EixoDiasUteis {
  private final LocalDate base; private final Calendar cal; private final List<LocalDate> datas; private final int[] du;
  // pontos já mantidos (seção 6.4), em ordem de data; du[i] = p.diasUteis() != null ? p.diasUteis() : cal.diasUteis(base, p.data())
  public int du(LocalDate d) {
    int i = indiceDoPontoAnteriorOuIgual(d);             // -1 se antes do primeiro
    if (i >= 0 && datas.get(i).equals(d)) return du[i];
    if (i < 0) return Math.min(cal.diasUteis(base, d), du[0]);
    int v = du[i] + cal.diasUteis(datas.get(i), d);
    return (i + 1 < du.length) ? Math.min(v, du[i + 1]) : v;
  }
  public LocalDate dataDoDu(int n) {                      // prazo pedido em du
    for (int i = 0; i < du.length; i++) if (du[i] == n) return datas.get(i);
    int i = maiorIndiceComDuMenor(n);                    // -1 se n < du[0]
    LocalDate de = i < 0 ? base : datas.get(i); int ja = i < 0 ? 0 : du[i];
    LocalDate d = cal.advance(de, n - ja);
    if (i + 1 < du.length && !d.isBefore(datas.get(i + 1))) d = datas.get(i + 1).minusDays(1);
    return d;
  }
}
```

Para a grade diária (seção 7.4), não chamar `du(d)` dia a dia do zero: caminhar os dias em ordem, somando 1 a cada dia útil a partir do ponto anterior, e reiniciar em cada ponto.

### 6.4 Preparação dos pontos: `PreparacaoPontos.java` (requisito "Pontos no mesmo prazo do eixo")

Entrada: pontos gravados em ordem de data. Para cada ponto, `x` = fração do eixo (`Business252`: `DUp/252`; demais: pelo `DayCounter` com `DC`). Regras, nesta ordem:
1. `x <= 0` → descarta, aviso `PONTO_DESCARTADO_PRAZO_NAO_POSITIVO`.
2. Percorrendo em ordem de data: se `x` ≤ `x` do último mantido → descarta com `PONTO_DESCARTADO_MESMO_PRAZO` (data descartada, data mantida, `x`). Isso cobre "mesmo `x`, fica o de menor data" e "`x` que diminui".
3. Sobrou nenhum → `CURVA_NAO_CONSTRUIDA`.

Os pontos gravados nunca são alterados; a consulta devolve todos, com os avisos.

### 6.5 `CurvaInterpolada.java`

Monta, a partir do cadastro e dos pontos mantidos: `xs`, `ys` (grandeza), eixo de dias úteis, interpolador resolvido, políticas de início e fim, domínio.
- Início do domínio: `cal.advance(B, 1)`. Fim: `max(data do último ponto, horizonte.somarCorrido(B))`.
- `avaliar(LocalDate d, Integer duPedido)`:
  1. `d` fora de `[início, fim]` → `PRAZO_FORA_DO_DOMINIO` (prazo e limites).
  2. `du = duPedido != null ? duPedido : eixo.du(d)`; `dc = d − B`; `x` pelo eixo; `τ` pela cotação.
  3. `d` = data de ponto mantido → valor gravado, `PONTO`.
  4. Antes do primeiro: política de início; depois do último: política de fim; senão interpolador → `y`; valor pela inversa da grandeza.
  5. `Disabled` → `PRAZO_FORA_DO_DOMINIO`. `FlatValue` → valor do ponto adjacente. `FlatForward` (só `Linear`/`LogLinear`; ≥ 2 pontos) → `valorNoSegmentoPublico(w, yE, yD)` do segmento adjacente com `w` fora de `[0,1]`.
  6. Arredondar o valor pelo cadastro. Para `TAXA`: `FA` = `fator(valorArredondado, τ)` e `FD` = `FA^(1/du)` (`du` ≥ 1), ambos `fator16`.
- Registra na memória: prazo, vizinhos, `w`, `Y`, classificação, motivo.

---

## 7. Pipeline e gravação: `application/service/ConstruirCurvaService.java`

### 7.1 Interface e resultado

```java
public enum Acionamento { CARGA, ORQUESTRADOR, API }
public enum Situacao { CONSTRUIDA, RECONSTRUIDA, EXISTENTE, IGNORADA, SEM_INSUMO }
public record PedidoConstrucao(String codigo, LocalDate dataBase, boolean forcarRecalculo, String fonte, String produto,
  Acionamento acionadoPor, String usuario, String idCarga, Map<String,Integer> linhasAvisadas /* só na carga */,
  boolean gravar /* false = simulação */) {}
public record ResultadoConstrucao(String codigo, String nome, LocalDate dataBase, Situacao situacao, String motivoIgnorada,
  Proveniencia proveniencia, int quantidadePontos, String hashPontos, List<Aviso> avisos, long duracaoMs,
  CodigoErro codigoErro, String mensagemErro, MemoriaCalculo memoria) {}
```

### 7.2 Passos da construção (`gravar = true`), numa única transação (`@Transactional`, `TransactionTemplate` com timeout 30 s)

Entidades JPA (`adapter/out/persistence/jpa/entity/`), só com as colunas do `001_SCRIPT_INICIAL.sql`, chave composta por `@IdClass` (`dBaseReft`, `cTickerIndcd`, `dVertcReft`):
- `DadoVertcCurvaEntity` (`tDadoVertcCurva`): `dBaseReft`, `cTickerIndcd`, `dVertcReft`, `cDiaUtil` (`Integer`), `vFatorDia` e `vFatorAcum` (`BigDecimal`, `precision = 28, scale = 16`), `cQtdDiaPer`, `cQtdDiaReft` (`Integer`), `vPrecoTx` (`BigDecimal`, `precision = 28, scale = 12`). Reescrever a entidade atual.
- `DadoCurvaEntity` (`tDadoCurva`): `dBaseReft`, `cTickerIndcd`, `dVertcReft`, `vPrecoTx`. Tirar os campos que não existem no schema (`dtVerticeReferencia`, `cDiaUtil`, `cQtdDiaReft`, `vDiaFator`, `vFatorCalc`).

A trava é consulta nativa no repositório de `CurvaMercdEntity` (o `SET LOCK_TIMEOUT` vai na mesma conexão, dentro da transação, pelo `EntityManager`). As exclusões são `@Modifying @Query` em JPQL; as inclusões, `saveAll` (com o `batch_size` da seção 1.2). O SQL abaixo é o que tem de chegar ao banco:

```sql
SET LOCK_TIMEOUT 30000;
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;   -- erro SQL 1222 → CONSTRUCAO_EM_ANDAMENTO
SELECT dVertcReft, vPrecoTx, cDiaUtil, cQtdDiaPer, cQtdDiaReft, vFatorAcum, vFatorDia
  FROM tDadoVertcCurva WHERE cTickerIndcd = ? AND dBaseReft = ? ORDER BY dVertcReft;   -- pontos atuais (depois da trava)
```
1. Cadastro vigente (seção 5), com a origem pedida. Avisos `CURVA_INATIVA` / `FORA_DA_VIGENCIA_CURVA` (vigência de `tCurvaMercd`) quando a construção é pela API.
2. Havendo pontos e sem `forcarRecalculo` → executar o modelo sem gravar (passos 3–5 com `gravar = false`), comparar `hashPontos`, aviso `PONTOS_DIFERENTES_DA_FONTE` com a quantidade de pontos diferentes (data presente dos dois lados com valor diferente, mais os que só existem de um lado), situação `EXISTENTE`. Nada é escrito. Fim.
3. Ler insumos pelo `LeitorInsumos` (seção 8). Na carga: `linhas lidas != linhasAvisadas[código na fonte]` → `INSUMO_INCOMPLETO` (lidas e avisadas).
4. Executar o modelo (seção 8) → `List<PontoConstruido>`. Arredondar cada valor. Datas repetidas → `MODELO_FALHOU`.
5. Para cada ponto: `DUp` = `diasUteisPublicados` ou `cal.diasUteis(B, d)`; se publicado e diferente do calendário, ou data não útil → acumular `CALENDARIO_DIVERGENTE`. `DC = d − B`; `30/360` pela seção 3.1; para `TAXA`: `FA`, `FD` (16 casas).
6. Montar a `CurvaInterpolada` com esses pontos e gerar a grade (seção 7.4). Falha → desfaz tudo, com o erro.
7. Gravar:
```sql
DELETE FROM tDadoCurva      WHERE cTickerIndcd = ? AND dBaseReft = ?;
DELETE FROM tDadoVertcCurva WHERE cTickerIndcd = ? AND dBaseReft = ?;
INSERT INTO tDadoVertcCurva (dBaseReft, cTickerIndcd, dVertcReft, cDiaUtil, vFatorDia, vFatorAcum, cQtdDiaPer, cQtdDiaReft, vPrecoTx)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);                        -- batchUpdate
INSERT INTO tDadoCurva (dBaseReft, cTickerIndcd, dVertcReft, vPrecoTx) VALUES (?, ?, ?, ?);   -- batchUpdate em lotes de 1.000
UPDATE tCurvaMercd SET dBaseReft = CASE WHEN dBaseReft IS NULL OR dBaseReft < ? THEN ? ELSE dBaseReft END,
                       cUsuarCalc = ? WHERE cTickerIndcd = ?;
```
8. Situação `CONSTRUIDA` (não havia pontos) ou `RECONSTRUIDA`. Depois do commit (`TransactionSynchronization.afterCommit`): log `CURVA_GRAVADA` (seção 14.1) e `CONSTRUCAO_CONCLUIDA`. Falha → `CONSTRUCAO_FALHOU`, sem `CURVA_GRAVADA`.

`fatores` nulos para `PRECO` e `PONTOS`. `cUsuarCalc` = `preferred_username` do token, ou `appid` para identidade de serviço.

### 7.3 Simulação (`gravar = false`)

Mesmo serviço, sem trava e sem escrita. Não lança erro de negócio: devolve `status` `ERRO` com `codigoErro`, mensagem e a memória até a falha (só `CURVA_NAO_ENCONTRADA` e `PARAMETRO_INVALIDO` viram HTTP). Compara com os pontos gravados: `IGUAL`, `DIFERENTE`, `SO_SIMULADO`, `SO_GRAVADO`. Prazos pedidos fora do domínio: `FORA_DO_DOMINIO` com motivo, sem falhar os demais.

### 7.4 Grade da curva interpolada (`GradeInterpolada.java`)

- Início: `cal.advance(B, 1)`, ou a data do primeiro ponto se a extrapolação de início é `Disabled`.
- Fim: fim do domínio, ou a data do último ponto se a de fim é `Disabled`.
- Um valor por dia corrido, pelo mesmo `avaliar` da seção 6.5 (sem `duPedido`), arredondado. Caminhar os dias em ordem para os dias úteis (seção 6.3). O valor de cada dia MUST ser igual ao da rota de interpolação para a mesma data.

### 7.5 Regravação da interpolada: `RegravarInterpoladaService.java`

Mesma trava da 7.2. Ler os pontos de `tDadoVertcCurva`; sem pontos → `DELETE FROM tDadoCurva ...` e `CURVA_NAO_CONSTRUIDA`. Com pontos → cadastro vigente, `CurvaInterpolada`, grade, `DELETE` + `INSERT` só em `tDadoCurva`. Não executa modelo, não altera pontos, não gera `CURVA_GRAVADA`; loga `INTERPOLADA_REGRAVADA` (código, nome, data-base, usuário, `hashPontos`, quantidade de linhas).

### 7.6 Conferência da interpolada (consulta, auditoria, situação)

Gerar a grade em memória a partir dos pontos atuais e comparar com `SELECT dVertcReft, vPrecoTx FROM tDadoCurva WHERE cTickerIndcd = ? AND dBaseReft = ?` (comparar `BigDecimal` com `compareTo`, porque o banco devolve 12 casas). Diferença em data ou valor → `INTERPOLADA_DESATUALIZADA` com a quantidade de dias diferentes.

### 7.7 `hashPontos` (`HashPontos.java`)

```java
public static String calcular(List<PontoGravado> pontos) {             // em ordem de data
  String texto = pontos.stream().sorted(Comparator.comparing(PontoGravado::data))
    .map(p -> p.data() + ";" + canonico(p.valor())).collect(Collectors.joining("\n"));
  return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
}
static String canonico(BigDecimal v) { return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString(); }
```

---

## 8. Modelos de construção: `domain/construcao/`

### 8.1 Contratos

```java
public record PontoConstruido(LocalDate data, BigDecimal valor, Integer diasUteisPublicados) {}
public interface ModeloConstrucao {
  String nome(); String fonte(); String produto(); List<String> papeis();   // papeis vazio se não derivado
  List<PontoConstruido> construir(ContextoConstrucao ctx, MemoriaCalculo memoria);
}
public interface ContextoConstrucao {
  CadastroCurva cadastro(); Origem origem(); LocalDate dataBase(); Calendar calendario(); LeitorInsumos insumos();
  CurvaMae curvaMae(String papel);    // só derivadas; em outro modelo → ErroEngine(MODELO_FALHOU)
}
```

### 8.2 `LeitorInsumos` (único acesso às tabelas brutas; `adapter/out/persistence/jpa/LeitorInsumosJpa.java`, com `@Query(nativeQuery = true)` ou entidades das três tabelas brutas)

```sql
SELECT cldtfdUnic, cTickerIndcd, dBaseReft, cDiaCorri, cDiaUtil, vPrecoTx FROM tBtrsCurvaPrimr  WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT cldtfdUnic, cTickerIndcd, dBaseReft, vVertcCurva, vPrecoTx      FROM tAnbmaCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT cldtfdUnic, cTickerIndcd, cTickerBberg, dBaseReft, vPrecoUlt   FROM tBbergCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;
```

`cTickerIndcd` é o **nome** da curva. Nenhuma linha → `INSUMO_AUSENTE` (código, fonte, código na fonte, data). O leitor devolve a quantidade de linhas lidas para a conferência da carga. Cada linha vai para a aba `Insumos` da memória.

### 8.3 `pontosprontos/PontosProntos.java` (comum)

Ordena por data, rejeita data repetida (`INSUMO_INVALIDO` com as linhas) e registra os pontos na memória com as colunas extras do modelo.

### 8.4 `prontatsb3/ProntaTsB3` (`PRONTA_TS_B3`, fonte `B3`, produto `TS`)

Aplicar a tabela "Regras do arquivo" da spec `b3-ready-curve-model` linha a linha. Ponto: `data = B + cDiaCorri`, `valor = vPrecoTx` (sem mudar sinal ou escala), `diasUteisPublicados = cDiaUtil`. Colunas extras na aba `Pontos`: `DC publicado`, `DU publicado`. Não falha por calendário (o pipeline avisa, passo 7.2.5).

### 8.5 `sofrzerobloomberg/SofrZeroBloomberg` (`SOFR_ZERO_BLOOMBERG`, fonte `BLOOMBERG`, produto `BLC2`)

Aplicar a tabela "Regras do arquivo" da spec `sofr-bloomberg-curve-model`. `cTickerBberg.stripTrailing().split(" +")`: termo 0 = membro (tem de ser o código na fonte), termo 1 = tenor (`^([1-9][0-9]*)([DWMY])$`). Data: `D` → `cal.advance(B, n)`; `W` → `adjust(B + 7n)`; `M`/`Y` → `adjust(B.plusMonths/plusYears(n))`, com a convenção do cadastro. Mesmo tenor e mesmo valor → mantém o de menor `cldtfdUnic`, descarta os demais com `DUPLICADO`. `diasUteisPublicados = null`. Extras: `Tenor`, `Data nao ajustada`.

### 8.6 `ntnbbootstrapanbima/NtnbBootstrapAnbima` (`NTNB_BOOTSTRAP_ANBIMA`, fonte `ANBIMA`, produto `MS`)

Interpolador cadastrado tem de ser `Linear` ou `LogLinear` (senão `CADASTRO_INVALIDO`). Aplicar a tabela "Regras do arquivo" da spec `ntnb-anbima-curve-model` (sem tolerância). Para cada título mantido:

```java
LocalDate a = cal.advance(B, vVertcCurva);                 // data aproximada
LocalDate v = a.withDayOfMonth(15);                        // vencimento nominal
LocalDate p = cal.adjust(v, BusinessDayConvention.Following); // data do ponto; DU do ponto = vVertcCurva
BigDecimal y = vPrecoTx / 100;
BigDecimal c = 100 × (pow(1.06, 0.5) − 1);                 // cupom, sem arredondar
// eventos: datas nominais v, v−6m, v−12m... enquanto adjust(nominal, Following) > B
// DU_i = cal.diasUteis(B, pagamento_i), exceto o do vencimento, que é vVertcCurva
// fluxo F_i = c; no vencimento F = c + 100
// cotação C = Σ F_i · (1+y)^(−DU_i/252)
```

Bootstrap em ordem crescente de `v`. Para o título `n`, `f(z) = Σ F_i·DF_i(z) − C`:
- vencimento: `DF = (1+z)^(−DU_n/252)`;
- evento antes de qualquer título resolvido (primeiro título): mesma incógnita (`INCOGNITA`);
- evento antes do primeiro título resolvido: `(1+z_1)^(−DU_i/252)` (`FLAT_INICIO`);
- evento na data de um título resolvido `k`: `(1+z_k)^(−DU_i/252)` (`RESOLVIDO`);
- demais: `DF` interpolado pela grandeza, interpolador e eixo cadastrados entre os resolvidos e o ponto `(P_n, z)` (`INTERPOLADO`).

Bisseção em `[−0.99, 1.00]`: `f(a)·f(b) > 0` → `MODELO_FALHOU` (vencimento e `f` nos extremos); senão até largura < `1e-14` ou 200 iterações; `z_n` = ponto médio; ponto `(P_n, 100·z_n, vVertcCurva)`. Memória: extras `Vencimento nominal`, `Taxa indicativa`, `Cotacao`, `Iteracoes`, `Residuo`; aba `Fluxos` com `Titulo`, `Data nominal`, `Data pagamento`, `DU`, `Fluxo`, `Origem DF`, `DF`, `Valor presente`.

---

## 9. Carga, construção da data e situação: `application/service/`

### 9.1 `ProcessarCargaService` (`POST /api/v1/cargas`, papel `Curvas.Processor`)

Corpo: `{ idCarga (1..100), fonte, produto, dataBase, linhasPorCodigo: { código: inteiro >= 0 } }`; faltando campo, mapa vazio, negativo ou data inválida → 400. Não grava a carga em lugar nenhum. Passos:
1. Log `CARGA_RECEBIDA`.
2. Curvas cuja **origem principal** tem `fonte`/`produto` da carga e código na fonte em `linhasPorCodigo`. Código sem curva → log `AVISO`.
3. Para cada curva, independente (falha de uma não para as outras): inativa ou fora da vigência → `IGNORADA` com o motivo; senão `ConstruirCurvaService` com `acionadoPor = CARGA`, `linhasAvisadas`, `forcarRecalculo = false`.
4. Cadeia de derivadas (9.3).
5. Resposta 200: `idCarga` e, por curva, `codigo`, `situacao` ou `codigoErro` + `mensagem`, `motivoIgnorada`, `avisos`, `hashPontos`. Log `CARGA_PROCESSADA`.

Tempo total limitado a `engine.timeout.carga-segundos`.

### 9.2 `ConstruirDataService` (`POST /api/v1/construcoes/{dataBase}`, papel `Curvas.Orquestrador`)

Todas as curvas com código. Mesmas regras da carga, sem conferência de quantidade, `acionadoPor = ORQUESTRADOR`. Sem pontos e sem insumo (nenhuma linha bruta da origem na data, ou alguma mãe sem pontos) → `SEM_INSUMO`, sem erro. Curvas de provedor em paralelo (`ExecutorService` com `engine.construcao-data.paralelismo`), depois as derivadas em cadeia. Esgotado `engine.timeout.construcao-data-segundos`, não iniciar as que faltam e responder `ERRO_INTERNO` (as gravadas ficam). Logs `CONSTRUCAO_DATA_RECEBIDA` e `CONSTRUCAO_DATA_PROCESSADA`.

A existência de pontos é conferida **depois** da trava (passo 7.2), então webhook e orquestrador simultâneos não duplicam.

### 9.3 Cadeia de derivadas

Repetir até não haver mudança: derivadas ativas, vigentes, sem pontos e com todas as mães com pontos na data → construir (em ordem topológica). Derivada com pontos → comparar como `EXISTENTE`. Nunca recalcular. `curvaMae(papel)` monta uma `CurvaInterpolada` da mãe com os pontos de `tDadoVertcCurva` e o cadastro vigente dela, e guarda o `hashPontos` para a proveniência.

### 9.4 `SituacaoService` (`GET /api/v1/curvas/situacao?dataBase=`)

Por curva com código, em paralelo (`engine.situacao.paralelismo`), sem gravar: `insumo` (linhas brutas: `SELECT COUNT(*)` na tabela da origem; ou mães com/sem pontos), `pontosGravados` (quantidade e `hashPontos`), `interpolada` (linhas em `tDadoCurva` e `atualizada`, seção 7.6), `conferencia` (simulação: `status`, `codigoErro`, mensagem, `hashPontosFonte`, `pontosDiferentes`; nula sem insumo). Falha ou tempo de uma curva → `conferencia.status = ERRO` só dela.

---

## 10. Memória de cálculo e planilhas

### 10.1 `domain/memoria/MemoriaCalculo.java`

Objeto único preenchido pelos próprios métodos que calculam (nenhum recálculo para a memória). Coleções: `resumo` (lista de `Campo`/`Valor`), `insumos`, `pontos` (com colunas extras declaradas pelo modelo), `fluxos`, `interpolacao`, `eventos` (`Ordem`, `Nivel` `INFO|AVISO|ERRO`, `Evento`, `Mensagem`).

### 10.2 `adapter/out/planilha/PlanilhaMemoriaCalculo.java` (Apache POI, `XSSFWorkbook`)

Seis abas, nesta ordem e com os cabeçalhos exatos da spec `curve-calculation-memory`: `Resumo`, `Insumos`, `Pontos`, `Fluxos`, `Interpolacao`, `Eventos`. Primeira linha congelada (`createFreezePane(0, 1)`). Datas: células de data com formato `dd/mm/yyyy`. Números: células numéricas (`setCellValue(v.doubleValue())` **só** na célula da planilha; o JSON tem a precisão completa). Célula não aplicável: vazia. Nota de 15 dígitos no `Resumo`.

Nome: `{codigo}_{dataBase}_{GRAVADA|SIMULACAO}_{AAAAMMDDHHmmss}.xlsx`, `Content-Type` `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

### 10.3 `formato=zip`

`memoria.xlsx`, `memoria.json`, `modelos/{tipo}/{nome}/v{versao}.groovy` (texto compilado guardado em memória na carga da versão), `manifesto.json` (`correlationId`, versão do engine, `estadoScript`, modelos, lista de arquivos com SHA-256). `application/zip`.

---

## 11. Modelos Groovy e Blob: `domain/modelo/`, `adapter/out/blob/`

### 11.1 Resolução (`RegistroModelos<T>`, um por tipo `construcao|interpolacao|calendario`)

Ordem: versão fixada no cadastro (tem de estar `VALIDADA`, `ATIVA` ou `INATIVA` depois de validada) → versão `ATIVA` no `estado.json` → nativo Java (`@Component` registrado na subida) → `CADASTRO_INVALIDO` (tipo e nome). Nativos: construção `PRONTA_TS_B3`, `NTNB_BOOTSTRAP_ANBIMA`, `SOFR_ZERO_BLOOMBERG`; interpolação `Linear`, `LogLinear`, `BackwardFlat`, `ForwardFlat`, `Cubic`; calendário `Brazil`, `UnitedStates`.

### 11.2 Blob (`RepositorioScriptsBlob`)

- Cliente: `new BlobServiceClientBuilder().endpoint(endpoint).credential(new DefaultAzureCredentialBuilder().build())`; `connectionString` só no perfil `local` (em outro perfil, a presença dela falha a subida).
- `groovy-models/{tipo}/{nome}/v{n}.groovy`: `upload` com `BlobRequestConditions().setIfNoneMatch("*")`; 409 com o mesmo conteúdo = sucesso.
- `groovy-models/{tipo}/{nome}/estado.json`: `{ "ativa": n|null, "versoes": { "n": { "status", "hash", "autor", "criadoEm", "aprovador", "atualizadoEm", "motivo" } } }`; gravar com `setIfMatch(etagLido)`; 412 → `ESTADO_SCRIPT_CONCORRENTE`.
- Cache de estado por instância: 30 s (inclusive a ausência); releitura condicional por ETag. Classe compilada em memória por (tipo, nome, versão, hash), sem expirar. Hash do conteúdo ≠ `estado.json` → `MODELO_FALHOU`.
- Repetição: só leituras e a gravação imutável, 3 tentativas (200, 400, 800 ms + até 100 ms aleatórios). Circuito: 5 falhas seguidas → 60 s sem chamar o Blob (log `CIRCUITO_BLOB_ABERTO` uma vez).
- Blob fora: último estado lido, sem prazo, `estadoScript = DESATUALIZADO`, log `ESTADO_SCRIPT_DESATUALIZADO` no máximo 1 por minuto; sem estado ou sem a versão compilada → nativo, `DESCONHECIDO`, log `ERRO`. Subida: tenta ler todos os estados por até 300 s fora de prontidão; depois fica pronta mesmo assim.

### 11.3 `CarregadorGroovy`

```java
CompilerConfiguration cc = new CompilerConfiguration();
SecureASTCustomizer s = new SecureASTCustomizer();
s.setIndirectImportCheckEnabled(true);
s.setAllowedStarImports(List.of("br.com.poc.domain.curva", "br.com.poc.domain.quantlib", "br.com.poc.domain.matematica",
  "br.com.poc.domain.calendario", "br.com.poc.domain.interpolacao", "br.com.poc.domain.construcao", "br.com.poc.domain.memoria",
  "java.math", "java.time", "java.util"));
s.setDisallowedReceivers(List.of("java.lang.System", "java.lang.Runtime", "java.lang.Thread", "java.lang.Class",
  "java.lang.ClassLoader", "java.lang.ProcessBuilder", "java.io.File", "java.net.URL", "java.net.Socket"));
s.setAllowedImports(List.of());                                // só os star imports acima
cc.addCompilationCustomizers(s, new ASTTransformationCustomizer(Map.of("value", timeoutSegundos), TimedInterrupt.class));
```

Além do customizer, na validação: script que referencie pacote fora da lista, `java.io`, `java.net`, `java.lang.reflect`, `Thread`, `System`, `Runtime`, `ProcessBuilder` → `REPROVADA`. Classe compilada tem de implementar o contrato do tipo (`ModeloConstrucao`, `Interpolador`, `Calendar`), senão `REPROVADA`. Tempo esgotado na execução → `MODELO_FALHOU` (modelo e tempo).

### 11.4 Estados e validação

`RASCUNHO` → validação → `VALIDADA`/`REPROVADA`; `VALIDADA`|`INATIVA` → ativação → `ATIVA` (a anterior vira `INATIVA`, `aprovador` = quem ativou); `ATIVA` → desativação → `INATIVA`. Outra transição → `SCRIPT_INVALIDO`. Validação por tipo: interpolação em `xs=[1,2,3]`, `ys=[0.9,0.8,0.7]`, `x=1.5` e `2.5`; calendário: `isBusinessDay` de todos os dias do ano corrente (e, se gerado por planilha, toda a cobertura: útil ⇔ não é fim de semana nem data da lista); construção: `codigo` e `dataBase` no corpo, simulação com `status = OK`.

---

## 12. Calendário por planilha: `CalendarioPlanilhaService`

Importação (`POST /api/v1/calendarios/{nome}/importacao?mercado=&anoInicial=&anoFinal=`, multipart `arquivo`): aba `Feriados`, cabeçalho `Data`, `Descricao`; datas célula ou texto `dd/mm/aaaa`/`aaaa-mm-dd`; rejeições da spec `calendar-management` → 400 `PARAMETRO_INVALIDO` com um `Detalhe` por problema. Gerar o script por modelo fixo (datas em ordem crescente, uma por linha), gravar como próxima versão `RASCUNHO` do tipo `calendario`. Não guardar a planilha.

```groovy
import br.com.poc.domain.calendario.*
import java.time.LocalDate
class {NomeClasse} extends CalendarioPorLista {
  {NomeClasse}() { super("{nome}", "{mercado}", {anoInicial}, {anoFinal}, [
    LocalDate.parse("2026-01-01"),
    ...
  ] as Set) }
}
```

`{NomeClasse}` = `Calendario_` + nome com não alfanuméricos trocados por `_`. Mesma planilha → mesmo texto → mesmo hash.

Exportação (`GET /api/v1/calendarios/{nome}?mercado=&anoInicial=&anoFinal=&versao=&formato=`): dias de segunda a sexta não úteis no intervalo (≤ 150 anos); `xlsx` com abas `Feriados` e `Resumo`.

---

## 13. API: `adapter/in/api/rest/`

### 13.1 Rotas (controllers novos; prefixo `/api/v1`)

| Controller | Rotas |
|---|---|
| `CargaController` | `POST /cargas` |
| `ConstrucaoDataController` | `POST /construcoes/{dataBase}` |
| `CurvaController` | `GET /curvas`, `GET /curvas/situacao`, `POST /curvas/{codigo}/{dataBase}/construcao`, `GET /curvas/{codigo}/{dataBase}`, `GET .../interpolacao`, `GET .../simulacao`, `POST .../interpolada`, `GET .../auditoria` |
| `CurvaPorNomeController` | `GET /curvas/por-nome/{dataBase}`, `.../interpolacao`, `.../simulacao` (só leitura) |
| `ValoresCadastroController` | `GET /valores-cadastro` |
| `ModeloController` | `POST /modelos/{tipo}/{nome}`, `POST .../versoes/{versao}/validacao`, `POST .../versoes/{versao}/ativacao`, `POST .../desativacao`, `GET /modelos/{tipo}/{nome}` |
| `CalendarioController` | `POST /calendarios/{nome}/importacao`, `GET /calendarios/{nome}` |

Parâmetros: validar à mão (`@RequestParam Map<String,List<String>>`) contra a lista permitida de cada rota; desconhecido → 400. `du` inteiro ≥ 1; `data` `AAAA-MM-DD` posterior à data-base (qualquer dia corrido); até 5.000 prazos, na ordem recebida; `formato` `json|xlsx|zip` (zip só consulta, interpolação e simulação); `forcarRecalculo` `true|false`; `fonte`+`produto` juntos.

### 13.2 Erros, correlação e serialização

- `FiltroCorrelacao` (`OncePerRequestFilter`, primeira posição): `X-Correlation-Id` recebido ou `UUID.randomUUID()`; coloca no MDC (`correlationId`) e no cabeçalho de toda resposta, inclusive `xlsx`/`zip` e erro.
- `ErroEngineHandler` (`@RestControllerAdvice`): `ErroEngine` → `status = codigo.http`, corpo `{ codigoErro, mensagem, correlationId, detalhes: [{campo, linha, valor, motivo}] }`; `AccessDeniedException` → 403 `SEM_PERMISSAO`; falha de autenticação → 401 `NAO_AUTENTICADO`; tempo de requisição → 500 `ERRO_INTERNO`; qualquer outro → 500 `ERRO_INTERNO` sem stack trace.
- Jackson 3: `BigDecimal` como string plana. Com o `JsonMapper.Builder`: `withConfigOverride(BigDecimal.class, o -> o.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)))` e `enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)`. Datas `AAAA-MM-DD`; instantes pelo formato da seção 1.4. `avisos` sempre presente (lista vazia).

### 13.3 Segurança (`SegurancaConfig`)

```java
http.csrf(c -> c.disable())
  .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
  .authorizeHttpRequests(a -> a
    .requestMatchers("/actuator/health/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/v1/cargas").hasAuthority("Curvas.Processor")
    .requestMatchers(HttpMethod.POST, "/api/v1/construcoes/*").hasAuthority("Curvas.Orquestrador")
    .requestMatchers(HttpMethod.POST, "/api/v1/curvas/*/*/construcao", "/api/v1/curvas/*/*/interpolada").hasAuthority("Curvas.Operador")
    .requestMatchers(HttpMethod.POST, "/api/v1/modelos/*/*", "/api/v1/modelos/*/*/versoes/*/validacao", "/api/v1/calendarios/*/importacao").hasAuthority("Curvas.ModelosAutor")
    .requestMatchers(HttpMethod.POST, "/api/v1/modelos/*/*/versoes/*/ativacao", "/api/v1/modelos/*/*/desativacao").hasAuthority("Curvas.ModelosAprovador")
    .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyAuthority("Curvas.Leitura", "Curvas.Operador")
    .anyRequest().denyAll())
  .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(conversorRoles())));
// conversorRoles: JwtGrantedAuthoritiesConverter com setAuthoritiesClaimName("roles") e setAuthorityPrefix("")
```

Usuário: claim `preferred_username`, senão `appid`. Não há propriedade que desligue a segurança fora do perfil `local` de teste.

### 13.4 `GET /valores-cadastro`

Gerado das mesmas constantes do `ValidadorCadastro` (uma única tabela de parâmetros em código: chave, tipo, obrigatoriedade, condição, padrão, valores, formato) e dos enums; modelos por tipo do `RegistroModelos` (nativos + Groovy `ATIVA`), com fonte/produto e papéis; catálogos de enums de resposta, avisos e erros com `rotulo` e `descricao` pt-BR. Os textos ficam **no próprio código**, em cada enum, pela interface:

```java
public interface ComRotulo { String rotulo(); String descricao(); }
// exemplo
public enum ModoArredondamento implements ComRotulo {
  HALF_UP("Arredondar para cima no meio", "Arredonda para o mais próximo; no meio, para longe do zero"),
  HALF_EVEN("Arredondar para o par", "Arredonda para o mais próximo; no meio, para o dígito par"),
  DOWN("Truncar", "Corta as casas excedentes, sem arredondar");
  private final String rotulo, descricao;
  ModoArredondamento(String r, String d) { rotulo = r; descricao = d; }
  public String rotulo() { return rotulo; } public String descricao() { return descricao; }
}
```

Todo enum que aparece no cadastro, nas respostas, nos avisos e nos erros SHALL implementar `ComRotulo` (inclusive `CodigoErro`, `CodigoAviso`, `Situacao`, `Unidade`, `Grandeza`, `Compounding`, `Frequency`, `BusinessDayConvention`, as políticas de extrapolação e os estados de script); o texto é argumento do construtor, então valor sem texto não compila. As chaves de `cModDado` (tabela de parâmetros do `ValidadorCadastro`) levam `rotulo` e `descricao` na própria tabela em código. Modelos Groovy ativos usam o nome como rótulo e a descrição "Modelo Groovy {nome}, versão {n}". Mudar um texto exige deploy, como qualquer código; `versaoValores` = SHA-256 do JSON gerado com chaves ordenadas.

### 13.5 Remover

`CurvaConstrucaoController`, `CurvaCalculoController`, `ModeloUploadController`, DTOs antigos (`CalcularCurva*`, `ConstruirCurva*`, `UploadModeloResponse`), `domain/pipeline/**`, `domain/strategy/**`, `domain/service/CurvaInterpolacaoDomainService`, `domain/service/GroovyDynamicModelCompiler`, `domain/model/{ConvencaoDias, MetodoInterpolacao, PoliticaExtrapolacao, ...}` sem uso, `domain/calendar/**`, `MtrizCurvaEntity`/repositório, `ParmConfgCurvaEntity`/repositório, os campos antigos das entidades de `tDadoCurva` e `tDadoVertcCurva` (seção 7.2), `CurvaJpaPersistenceAdapter`, `CalcularCurvaUseCase`, `ConstruirCurvaUseCase`, `CurvaPersistencePort` e os serviços antigos (depois de reescritos). Ao final, busca sem referências a `MetodoInterpolacao`, `PoliticaExtrapolacao`, `ComposableCurveBuilder`, `CurveBuilderRegistry`, `tMtrizCurva`, `dtVerticeReferencia`.

---

## 14. Auditoria, logs, métricas e resiliência

### 14.1 Eventos de log (JSON, `logstash-logback-encoder`, sempre com `correlationId`)

| Evento | Nível | Campos |
|---|---|---|
| `CURVA_GRAVADA` | AVISO | `idAuditoria` (UUID), `codigo`, `nome`, `dataBase`, `operacao` (`CONSTRUCAO`/`RECONSTRUCAO`), `acionadoPor`, `usuario`, `instante`, `idCarga`, `hashPontos`, `hashPontosAnterior`, `quantidadePontos`, `pontosAnteriores` (data e valor), `origem`, `proveniencia` (versão do engine, `estadoScript`, avisos, modelos, cadastro, mães) |
| `CONSTRUCAO_CONCLUIDA` / `CONSTRUCAO_FALHOU` / `INSUMO_DESCARTADO` / `SIMULACAO_EXECUTADA` | INFO / ERRO / AVISO / INFO | spec `curve-build-pipeline` |
| `CARGA_RECEBIDA`, `CARGA_PROCESSADA`, `PONTOS_DIFERENTES_DA_FONTE` (AVISO) | | spec `curve-load-trigger` |
| `CONSTRUCAO_DATA_RECEBIDA`, `CONSTRUCAO_DATA_PROCESSADA`, `INTERPOLADA_REGRAVADA` | INFO | specs `curve-load-trigger` e `curve-engine-api` |
| `REQUISICAO_CONCLUIDA`, `DEPENDENCIA_CHAMADA` (DEBUG), `DEPENDENCIA_LENTA` (AVISO), `DEPENDENCIA_FALHOU` (ERRO), `TEMPO_ESGOTADO` | | spec `curve-engine-resilience` |

Nunca no log: token, connection string, conteúdo de script, corpo inteiro. Lista de pontos só em `CURVA_GRAVADA`. Stack trace só em campo próprio.

### 14.2 Métricas (Micrometer)

`engine_construcao_total{codigo,situacao}`, `engine_construcao_falha_total{codigo,codigoErro}`, `engine_construcao_duracao_segundos`, `engine_interpolacao_duracao_segundos`, `engine_dependencia_duracao_segundos{dependencia,operacao}`, `engine_tempo_esgotado_total{dependencia}`, `engine_estado_script_idade_segundos` (gauge), `engine_pontos_diferentes_fonte_total{codigo}`.

### 14.3 Tempos limite e prontidão

Tabela da spec `curve-engine-resilience` com as propriedades da seção 1.2. Requisição inteira: `ExecutorService` + `Future.get(timeout)` no controller, ou `AsyncRequestTimeout` equivalente. Gravação no banco e `estado.json` nunca repetem. `readiness`: banco (`DataSourceHealthIndicator`) e, só durante a espera de subida, o estado dos scripts; `health` mostra o Blob como informativo.

### 14.4 Versão do engine

`spring-boot-maven-plugin` com a meta `build-info` (já existe o plugin; só acrescentar a meta); a proveniência lê `BuildProperties.getVersion()`. Sem commit e sem plugin novo.

---

## 15. Ordem de implementação (uma tarefa de `tasks.md` por vez; `mvn -q compile` depois de cada uma)

1. Seções 1, 2, 3, 4 (tarefas 1.x e 2.x).
2. Seção 6 (3.x). Seção 5 (4.x).
3. Seção 11 (5.x). Seção 10.1 e 7.7 (6.x). Seção 8 (7.x).
4. Seção 7 (8.x). Seção 9 (9.x). Seção 14.1 (10.1). Seção 13 (10.4, 12.x). Seção 10.2/10.3 (13.x, 14.6). Seção 12 (14.1–14.4). Seções 14.2–14.4 (11.x, 14.5). Relógio já feito (15.1).
5. Seção 13.5 (remoções), `mvn compile` limpo.
6. Seção 16 (testes). Seção 17 (verificação).

---

## 16. Testes (ao final da implementação)

Só depois de tudo compilar. Ordem: **verificar, adaptar, criar, rodar**. Não há testes no engine hoje (`src/test` não existe); crie `src/test/java/br/com/poc/` e `src/test/resources/`.

### 16.1 Verificar

1. `mvn -q compile` limpo.
2. Buscas que não podem achar nada em `src/main`: `LocalDate.now(`, `Instant.now(`, `ZoneId.systemDefault(`, `NOLOCK`, `READ_UNCOMMITTED`, `double ` em `domain/` (fora do chute de `DecimalMath.ln` e das células de planilha), `MetodoInterpolacao`, `PoliticaExtrapolacao`, `tMtrizCurva`, `dtVerticeReferencia`.

### 16.2 Recursos de teste

- `src/test/resources/TaxaSwap_20260914.txt`: cópia de `docs/TaxaSwap.txt`.
- **Sem banco nem Blob reais nos testes.** Os testes rodam só com `mvn verify`, sem SQL Server, Azurite ou rede: repositórios JPA e o cliente do Blob são substituídos por Mockito ou por implementações em memória das portas (`RepositorioScripts` em memória com ETag simulado). O que só um banco ou um Blob reais provam (trava de 30 s, leitura consistente durante a reconstrução, gravação das duas tabelas no SQL Server, ETag do Blob) fica na lista da seção 16.5, conferida na homologação.
- `FixturesCadastro`: as 7 curvas exatamente como nas specs `b3-ready-curve-model`, `ntnb-anbima-curve-model`, `sofr-bloomberg-curve-model` e o `exemplo-cadastro-7-curvas.txt` do change `curves-cadastro-curvas`.
- `FixturesB3`: lê o `TaxaSwap` e devolve as linhas que o `LeitorInsumos` simulado entrega para `DIxPRE`, `Cupom limpo de dólar`, `Cupom Limpo DI X IPCA`, `IBOVESPA` e `PTAX - USD` (`cldtfdUnic` sequencial no teste), no formato das colunas de `tBtrsCurvaPrimr`.
- Massa de 12 meses de `TaxaSwap` (tarefa 15.2): compactada em `src/test/resources/massa-b3/`.

### 16.3 Criar (um arquivo por linha; casos das seções e das specs)

| Teste | Casos principais |
|---|---|
| `RelogioTest` (tempo simulado, sem esperar relógio real) | `new Relogio(Clock.fixed(Instant.parse("2026-09-15T01:30:00Z"), ZoneOffset.UTC))`: `hoje()` = `2026-09-14` e `agora()` = `2026-09-14T22:30:00.000-03:00`; `carimboArquivo()` = `20260914223000`. Nos demais testes, o `Relogio` é um mock do Mockito (`when(relogio.agora()).thenReturn(...)`), nunca o relógio real |
| `DecimalMathTest` | `pow`, `ln`, `exp` contra referência de 50 dígitos (erro relativo < `1e-30`); potência inteira exata |
| `ArredondamentoTest` | 5.43219876 → 5.4321987 (`DOWN`, 7); 5.123456789 → 5.12345679 (`HALF_UP`, 8) |
| `PeriodTest`, `FrequencyTest` | parse válido/inválido; valores numéricos |
| `DayCounterTest` | `(B,d]` com `2026-09-14`→`2026-09-15` = 1 e 1; 30/360 com fim de mês |
| `InterestRateTest` | 1.139; 1.0125; ida e volta exata |
| `BrazilTest` | Páscoa e feriados de 2026 e 2027 (seção 0.2); DU dos 278 vértices da `PRE` = publicado |
| `UnitedStatesTest` | feriados 2026 e 2027 (seção 0.2) |
| `InterpoladoresTest` | valor exato nos nós; `LogLinear` com `y <= 0` → `PONTOS_NAO_INTERPOLAVEIS`; `Cubic` com 2 pontos = linear |
| `ManualB3Test` | 1.4.2, 1.4.3, 1.4.4, 1.4.5, 1.4.11, 1.4.6–1.4.10 implementados direto em `BigDecimal` no teste e comparados |
| `EixoDiasUteisTest` | os quatro cenários do requisito de dias úteis publicados; calendário certo = calendário puro |
| `PreparacaoPontosTest` | pontos de `2026-12-24` e `2026-12-25` (mesmo prazo); ponto sozinho no feriado; dias úteis fora de ordem; ponto na data-base |
| `CurvaInterpoladaTest` | vetores de `2030-06-10` (`PRE`, `DCL`, `PTX`) e fatores (seção 0.2); domínio; `FlatValue` da `INP`; `PTX` depois do último ponto |
| `HashPontosTest` | vetor comum e os 5 `hashPontos` da seção 0.2; valor lido com 12 casas dá o mesmo hash |
| `ValidadorCadastroTest` | um caso por regra de `CADASTRO_INVALIDO`; `'ATIVO' + espaços`; `business252` recusado; `MODELOS_POR_ORIGEM` sem ligação ignorado |
| `ProntaTsB3Test`, `SofrZeroBloombergTest`, `NtnbBootstrapAnbimaTest` | tabelas "Regras do arquivo"; `15M` → `2027-12-14`; bootstrap sintético que recupera uma curva zero conhecida; `z_1 = y_1`; soma dos valores presentes = cotação − resíduo |
| `ConstruirCurvaServiceTest` (repositórios simulados) | `PRE`: 278 pontos e 12.390 linhas enviados ao `saveAll`; fim de semana = sexta; recálculo apaga e regrava as duas; `EXISTENTE` com e sem `PONTOS_DIFERENTES_DA_FONTE`; `dBaseReft` não retrocede; exceção no commit simulado sem `CURVA_GRAVADA`; exceção de trava → `CONSTRUCAO_EM_ANDAMENTO`; calendário com feriado a menos → `CALENDARIO_DIVERGENTE` |
| `RegravarInterpoladaTest` (repositórios simulados) | ponto alterado direto em `tDadoVertcCurva` → `INTERPOLADA_DESATUALIZADA` → regravação → atualizada |
| `ProcessarCargaServiceTest`, `ConstruirDataServiceTest` (serviço de construção e repositórios simulados) | todos os cenários da spec `curve-load-trigger` (retry, republicação, inativa, falha isolada, 150 de 278, aviso atrasado, linha a menos do usuário, derivada, webhook e orquestrador juntos) |
| `OrigemSecundariaTest` (repositórios simulados) | cenários da spec `curve-build-pipeline` e `curve-engine-api` |
| `RegistroModelosTest` (`RepositorioScripts` em memória) | resolução; duas instâncias do registro; ativação concorrente; hash adulterado; Blob parado; circuito |
| `CarregadorGroovyTest` | script válido; tipo errado; rede → reprovado; laço → `MODELO_FALHOU`; `LogLinear` sobrescrevendo só `valorNoSegmento` |
| `PlanilhaMemoriaCalculoTest`, `CalendarioPlanilhaTest` | abas, cabeçalhos, tipos de célula; ida e volta do `Brazil` 2001–2100; mesma planilha → mesmo hash |
| `ApiContratoTest` (MockMvc) | um teste por rota com 401/403/papel certo; um por `codigoErro`; `X-Correlation-Id` em sucesso, erro e `xlsx`; fator com 16 casas como string; parâmetro desconhecido 400; `data` em sábado |
| `ValoresCadastroTest` | ida e volta com o validador; interpolador Groovy ativado aparece; todo valor com `rotulo` e `descricao` não vazios e com acentuação correta em UTF-8 |
| `ArquiteturaTest` (ArchUnit) | proibições da seção 16.1; `domain` não depende de `adapter` |
| `PropriedadesTest` (`@ParameterizedTest` com 1.000 casos gerados por `new Random(20260914)`, sem biblioteca extra) | ponto preservado; determinismo; ida e volta taxa↔fator; monotonicidade de DU e DC |
| `OraculoB3Test` | massa de 12 meses: cada vértice das 5 curvas devolve o `vPrecoTx`, e nenhuma construção traz `CALENDARIO_DIVERGENTE` |

### 16.4 Rodar e fechar

1. `mvn verify` no engine: tudo passa, sem banco nem Blob.
2. As buscas da 16.1 não acham nada.
3. Teste que falha e reflete a spec → corrigir o código, não o teste.

### 16.5 Conferido na homologação (não é teste automatizado)

O que depende de SQL Server ou de Blob reais fica para o roteiro de homologação, no ambiente com o banco e o Blob do projeto:
- trava da curva: duas construções da mesma curva ao mesmo tempo não misturam pontos, e a segunda espera até 30 s ou recebe `CONSTRUCAO_EM_ANDAMENTO`;
- consulta durante uma reconstrução devolve os pontos antigos inteiros ou os novos inteiros;
- `tDadoVertcCurva` e `tDadoCurva` gravadas juntas, com as quantidades da seção 0.2;
- ativação de script propagada às duas instâncias em até 30 s, e ativação concorrente com `ESTADO_SCRIPT_CONCORRENTE`;
- a seção 17.

## 17. Verificação ponta a ponta (na homologação)

1. Banco de homologação com o `001_SCRIPT_INICIAL.sql`, o cadastro das 7 curvas e `tBtrsCurvaPrimr` do `TaxaSwap` de `2026-09-14`.
2. `POST /api/v1/cargas` com `B3`/`TS` e 278 por código → as 5 curvas `CONSTRUIDA`, `hashPontos` iguais aos da seção 0.2.
3. `GET /api/v1/curvas/PRE/2026-09-14/interpolacao?data=2030-06-10` → 14.0624222, igual a `tDadoCurva` em `2030-06-10`.
4. Alterar um ponto direto em `tDadoVertcCurva` → consulta com `INTERPOLADA_DESATUALIZADA` → `POST .../interpolada` → atualizada.
5. `POST /api/v1/construcoes/2026-09-14` → as 5 `EXISTENTE`, NTN-B e SOFR `SEM_INSUMO`.
6. Baixar `formato=xlsx` e `formato=zip` da simulação da `PRE`.
7. `openspec validate engine-modelos-curva --strict`.
