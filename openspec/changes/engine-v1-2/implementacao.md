# Guia de implementação: engine-v1-2 (para fazer na mão)

> **Jackson 3 (Spring Boot 4):** para injetar o mapper do Spring, use `tools.jackson.databind.ObjectMapper` (ou `tools.jackson.databind.json.JsonMapper`), com `tools.jackson.core.type.TypeReference` e `JsonNode` de `tools.jackson.databind`. **Nunca** `com.fasterxml.jackson.databind.ObjectMapper`/`JsonMapper`: o Boot 4 não cria esse bean, e a aplicação não sobe ("required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"). Só as anotações continuam em `com.fasterxml.jackson.annotation`. No Jackson 3, `asText()` virou `asString()`.

> **Parâmetros de rota em objeto:** controller não recebe uma fila de `@RequestParam`. Com mais de dois parâmetros, agrupe num record: `@ModelAttribute FiltroX filtro` para consultas `GET` (o Spring preenche os campos pelos nomes da query) e `@RequestBody PedidoX pedido` para comandos `POST`/`PUT`. `@PathVariable` (identificadores como `codigo` e `dataBase`) continua separado.

> **Tipagem forte (Java 21):** dentro do domínio e das portas, nada de `Map<String, Object>`, `Object[]`, `Object` genérico ou `String` com JSON dentro. Valores fechados viram `enum`; dados viram `record`; variantes viram `sealed interface` com records. JSON cru só na borda (controller, cliente HTTP, coluna `cModDado`), desserializado direto num record; consultas nativas devolvem projeção em record.

> **Hexagonal (aprendido na `curves` v1.2):** `domain` e `application` não importam `adapter`; o controller chama um **use case** (`application/port/in`), nunca o service concreto, e não importa tipo de `port/out`. Um tipo de resposta tem **uma** cópia, em arquivo próprio em `domain` (ou DTO no `adapter/in`); record não vive dentro de interface (exceção: variantes de uma `sealed interface` de domínio, como `ResultadoConstrucao`).

> **Comentários e nomes:** javadoc de **uma linha**, só o que a coisa faz (o motivo vai neste guia). Sem "canônico". Método privado de uma linha que só embrulha outro método e é usado uma vez não existe.

> **Tempo:** o fuso é fixado uma vez (`TimeZoneConfig`) e o código usa `now()` sem zona; sem constante `ZoneId`.

> **Spec não crava código real:** nomes de pacote, de exceção, de handler e de log seguem o repositório real; este guia dá o padrão e você troca pelos nomes que existem lá.

Três partes: a correção da NTN-B (§1), a SOFR (§2) e a conferência de estrutura (§3). Linhas citadas são as do relatório de inspeção (`docs/inspecao/inspecao-engine.md` do poc); confira no código, porque podem ter mudado.

## 1. NTN-B: vencimento nominal exato

**Onde:** `domain/construcao/NtnbBootstrapAnbima.java:108-124`; o `withDayOfMonth(15)` está em `:113-116`.

**Dado real (13/10/2026):** nos arquivos `ms` de 02 a 13/10/2026 as 14 NTN-B por dia vêm com `Codigo SELIC` `760199` e vencimento no dia 15 (`2027-05-15` a `2060-08-15`), sem nenhum `760198`. O processor grava `prazoDiasCorridos = vencimento − dataBase`. Então `dataBase + vVertcCurva` já é o dia 15, e a mudança **não altera** o resultado da carga real.

### 1.1 O que fazer

```java
// antes: LocalDate vencimento = base.plusDays(prazo).withDayOfMonth(15);
LocalDate vencimentoNominal = base.plusDays(vertice.vVertcCurva());      // V exato

// eventos: sempre a partir de V (minusMonths em cadeia deriva em fim de mês)
for (int k = 0; ; k++) {
    LocalDate nominal = vencimentoNominal.minusMonths(6L * k);
    LocalDate pagamento = calendario.ajustar(nominal, BusinessDayConvention.Following);
    if (!pagamento.isAfter(base)) {
        break;
    }
    // fluxo: c em cada evento e c + 100 no vencimento (k == 0); DU_i = diasUteis(base, pagamento)
}
```

- Troque `vertice.vVertcCurva()`, `calendario.ajustar(...)` e `BusinessDayConvention` pelos nomes reais do modelo (a inspeção cita `DayCounter` e `Calendario`; o ajuste por `Following` já existe em `domain/calendario/Calendario.java`).
- Não ponha nenhum `withDayOfMonth` no modelo. Se o prazo vier errado, a curva sai diferente e a conferência de vetores (v2) mostra.
- O cupom (`c = 100 × (1,06^(1/2) − 1)`) e o bootstrap por bisseção não mudam.

### 1.2 Teste do modelo

Três casos, com data-base `2026-09-28` e o calendário `Brazil`/`Settlement` (troque pelos helpers de teste que o modelo já usa):

| Caso | Entrada | Esperado |
|---|---|---|
| Dia 15 em dia não útil | `vVertcCurva` = `229` | `V` = `2027-05-15` (sábado); pagamento `2027-05-17`; evento anterior nominal `2026-11-15` (domingo), pagamento `2026-11-16` |
| Fora do dia 15 | `vVertcCurva` = `232` | `V` = `2027-05-18`; eventos nominais `2027-05-18`, `2026-11-18`, sem ir para o dia 15 |
| Prazo não positivo | `vVertcCurva` = `0` ou menor | título descartado como hoje |

No primeiro caso, confira também o fluxo: o pagamento de `2026-11-16` entra (posterior a `2026-09-28`) e o de `2026-05-15` não.

### 1.3 Conferir que ninguém mais mexe no prazo

Abra `application/service/ProcessarCargaService.java:93-105` e `domain/interpolacao/PreparacaoVertices.java:27-30`. O processor só encaminha a carga; a preparação trata prazo não positivo e repetido. Se nenhum altera o prazo da NTN-B, registre **manter** com `arquivo:linha`.

## 2. SOFR: conjunto esperado de tenores

**Onde:** `domain/construcao/SofrZeroBloomberg.java:48-107, 110-158, 160-173` (parse dos tenores, membro, datas e duplicatas já existem).

### 2.1 Antes: a lista real
A spec do engine fala em 21 tenores e a reserva do processor tem 20 (`TickersSofrReserva`). **Confirme a lista com quem conhece o membro `S0490Z`** e use a mesma nos dois lados. Sem isso, não escreva a constante.

### 2.2 O que fazer

```java
// no modelo: o conjunto esperado, em ordem de tenor (a lista final vem da 2.1)
private static final Set<String> TENORES_ESPERADOS = Set.of(/* "1D", "1M", "3M", ... */);

// depois de descartar o duplicado idêntico e antes de montar os pontos:
Set<String> lidos = nosPorTenor.keySet();
Set<String> faltam = new TreeSet<>(TENORES_ESPERADOS);
faltam.removeAll(lidos);
Set<String> sobram = new TreeSet<>(lidos);
sobram.removeAll(TENORES_ESPERADOS);
if (!faltam.isEmpty() || !sobram.isEmpty()) {
    throw /* a exceção que o modelo já usa para INSUMO_INVALIDO */(
        "SOFR de " + dataBase + ": faltam " + faltam + " e sobram " + sobram
            + "; carregue o arquivo completo da Bloomberg");
}
```

- Troque `nosPorTenor` e a exceção pelos nomes reais do modelo (o modelo já falha com `INSUMO_INVALIDO` em outros casos: use o mesmo mecanismo).
- A mensagem é completa e explicada (o front só mostra o texto). Sem código de erro novo.
- Ordene por prazo (não por texto) se quiser a lista legível: `TreeSet` com comparador pela data do tenor, ou deixe em texto, que já informa.

### 2.3 Teste
Quatro casos, com data-base `2026-09-14` e o conjunto esperado do modelo:

| Caso | Entrada | Esperado |
|---|---|---|
| Completo | todos os tenores | um ponto por tenor |
| Ausente | sem `15M` | `INSUMO_INVALIDO`, mensagem com `faltam [15M]`, nada gravado |
| A mais | com `18M` | `INSUMO_INVALIDO`, mensagem com `sobram [18M]` |
| Duplicado idêntico | `1D` duas vezes, mesmo valor | fica um nó; completo |

## 3. Conferência de estrutura

Cada subseção traz **o que olhar**, **o que registrar** e **como corrigir** (o padrão vem da `curves` v1.2). Resultado de cada item: **manter**, **alterar** ou **criar**.

### 3.1 Imports proibidos
Em cada arquivo de `application/service/` e `domain/`, olhe só os `import`. Proibido em `domain` e `application`: `br.com.poc.adapter.*`, `org.springframework.data.*`, `jakarta.persistence.*`, `com.fasterxml.jackson.databind.*`, `tools.jackson.*`. Permitido: anotações de Jackson, `@Service`, `@Transactional` e `lombok`. **Correção:** troque a classe de adaptador pela porta de saída (`application/port/out/...Port`).

### 3.2 Controller chama use case
Padrão da `curves`:

```java
// application/port/in/CurvaUseCase.java
public interface CurvaUseCase {
    ResultadoConstrucao construir(PedidoConstrucao pedido);
}

// application/service/ConstruirCurvaService.java
@Service
public class ConstruirCurvaService implements CurvaUseCase { ... }

// adapter/in/api/rest/controller/CurvaController.java
private final CurvaUseCase useCase;      // e não ConstruirCurvaService
```

Registre também o controller que importa tipo de `application/port/out` (na `curves`, foi o `RespostaEngine` da porta de saída usado direto no controller): o tipo passa a ser um record de `domain`, usado pela porta e pelo use case.

### 3.3 Tipos fortes e um tipo por arquivo
Olhe os arquivos de `application/port/` e os três de `domain/curva/`. Registre `Map<String, Object>`, `Object`, `String` com JSON e record dentro de interface ou duplicado. **Correção:** record próprio em `domain`; as variantes de `ResultadoConstrucao` ficam como estão (sealed).

### 3.4 Nomes e comentários
Registre: nome com "canônico"; método privado de uma linha usado uma vez; javadoc com mais de uma linha. **Correção:** renomear (Shift+F6), inlinar o método, cortar o javadoc para uma linha.

### 3.5 Jackson e parâmetros em objeto
Registre `com.fasterxml.jackson.databind` e controller com mais de dois `@RequestParam`. **Correção:** `tools.jackson.databind.ObjectMapper` ou `JsonMapper`; `@ModelAttribute FiltroX` (GET) e `@RequestBody PedidoX` (POST e PUT).

### 3.6 Tempo
`config/TimeZoneConfig.java` deve fixar `America/Sao_Paulo` e recusar subida em outro fuso. Registre `ZoneId.of(...)` em services e `now(zona)`. **Correção:** `LocalDate.now()` e `OffsetDateTime.now()`.

### 3.7 Swagger completo
Para cada rota dos 4 controllers: `@Operation(summary, description)`, `@Parameter(description, example)` em `codigo`, `dataBase` e nos filtros, `@ApiResponses` no nível da classe e `produces = APPLICATION_JSON_VALUE`. Nos DTOs: `@Schema(description, example)` por campo. Padrão (da `curves`):

```java
@RestController
@RequestMapping(value = "/api/v1/curvas", produces = MediaType.APPLICATION_JSON_VALUE)
@ApiResponses({
    @ApiResponse(responseCode = "404", description = "Curva não encontrada"),
    @ApiResponse(responseCode = "422", description = "Dados inválidos (a mensagem diz qual campo)")
})
@Tag(name = "Curvas", description = "Construção e consulta das curvas")
public class CurvaController {

    @PostMapping("/{codigo}/{dataBase}/construcao")
    @Operation(summary = "Construir curva da data", description = "Constrói os vértices e a interpolada; forcarRecalculo=true refaz data já construída")
    public ResponseEntity<ResultadoConstrucao> construir(
            @Parameter(description = "Código da curva", example = "PRE") @PathVariable String codigo,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14") @PathVariable LocalDate dataBase, ...
```

Imports: `io.swagger.v3.oas.annotations.Operation`, `Parameter`, `responses.ApiResponse`, `responses.ApiResponses` e `media.Schema`. Cada recusa tem **mensagem completa**: o front só mostra o texto.

### 3.8 Testes
Em `src/test/java/br/com/poc/adapter/out/persistence/`: um `...AdapterTest` que só mocka o repositório e confere delegação ou `toDomain` é apagado (a persistência é conferida pelo teste manual). Confirme que `ConstruirCurvaService`, `ConstruirDataService`, `InterpolarCurvaService` e os três modelos de construção têm teste de regra com valor de referência, e que os testes de controller (`@WebMvcTest`) conferem JSON e status.

### 3.9 Registro
No fim do `tasks.md`, uma tabela por item: **o que olhei** (arquivo:linha), **resultado** (manter, alterar ou criar) e **o que mudou**. Nada de infra nova: se uma correção pedir tópico, tabela ou cache, deixe `TODO(revisao)`.
