# Guia de implementação: engine-v1-1 (para fazer na mão)

> **Jackson 3 (Spring Boot 4):** para injetar o mapper do Spring, use `tools.jackson.databind.ObjectMapper` (ou `tools.jackson.databind.json.JsonMapper`), com `tools.jackson.core.type.TypeReference` e `JsonNode` de `tools.jackson.databind`. **Nunca** `com.fasterxml.jackson.databind.ObjectMapper`/`JsonMapper`: o Boot 4 não cria esse bean, e a aplicação não sobe ("required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"). Só as anotações continuam em `com.fasterxml.jackson.annotation`. No Jackson 3, `asText()` virou `asString()`.

> **Parâmetros de rota em objeto:** controller não recebe uma fila de `@RequestParam`. Com mais de dois parâmetros, agrupe num record: `@ModelAttribute FiltroX filtro` para consultas `GET` (o Spring preenche os campos pelos nomes da query) e `@RequestBody PedidoX pedido` para comandos `POST`/`PUT`. `@PathVariable` (identificadores como `codigo` e `dataBase`) continua separado.

> **Tipagem forte (Java 21):** dentro do domínio e das portas, nada de `Map<String, Object>`, `Object[]`, `Object` genérico ou `String` com JSON dentro. Valores fechados viram `enum`; dados viram `record`; variantes viram `sealed interface` com records. JSON cru só na borda (controller, cliente HTTP, coluna `cModDado`), desserializado direto num record; consultas nativas devolvem projeção em record.

Duas mudanças pequenas no engine v1 já aplicado. Linhas citadas são as do relatório de inspeção (`docs/inspecao/inspecao-engine.md` do poc); confira no código, porque podem ter mudado.

## 1. `situacao` na resposta da construção

**Onde:** `domain/curva/ResultadoConstrucao.java:6-60` (interface selada com as variantes `Construida`, `Reconstruida`, `Existente`, `Ignorada`, `SemInsumo`, `Falhou`). A resposta sai direto em `CurvaController.java:84-95`.

**O que fazer:** um método `default` na interface, anotado para o Jackson, que devolve o nome do resultado. Não mexa nos campos das variantes.

```java
import com.fasterxml.jackson.annotation.JsonProperty;   // as anotações continuam neste pacote no Jackson 3

public sealed interface ResultadoConstrucao
        permits ResultadoConstrucao.Construida, ResultadoConstrucao.Reconstruida, ResultadoConstrucao.Existente,
                ResultadoConstrucao.Ignorada, ResultadoConstrucao.SemInsumo, ResultadoConstrucao.Falhou {

    @JsonProperty("situacao")
    default String situacao() {
        return switch (this) {
            case Construida c   -> "CONSTRUIDA";
            case Reconstruida r -> "RECONSTRUIDA";
            case Existente e    -> "EXISTENTE";
            case Ignorada i     -> "IGNORADA";
            case SemInsumo s    -> "SEM_INSUMO";
            case Falhou f       -> "FALHOU";
        };
    }
    // ... variantes como já estão
}
```

- Use os nomes reais das variantes e do `permits` (se as variantes forem arquivos separados, o `switch` é o mesmo).
- Sem `default` no `switch`: se alguém criar uma variante nova, o compilador avisa.
- Se o mapper do projeto não pegar o método `default` (teste abaixo falha), ponha `@JsonProperty("situacao") public String situacao()` em cada record, devolvendo a constante.

**Conferir:**

```java
@Test
void situacaoSaiNoJson() throws Exception {
    var mapper = /* o JsonMapper que o MVC usa (o da JsonConfiguration) */;
    String json = mapper.writeValueAsString(/* uma Reconstruida montada à mão */);
    assertThat(json).contains("\"situacao\":\"RECONSTRUIDA\"");
}
```

E no Swagger: `POST /api/v1/curvas/PRE/{data}/construcao?forcarRecalculo=true` devolve `"situacao": "RECONSTRUIDA"`; sem `forcarRecalculo`, numa curva já construída, `"EXISTENTE"`.

## 2. Vértice sem dias úteis com eixo `Business252`

**Onde:** `domain/interpolacao/PreparacaoVertices.java:27-30` usa `0` quando `diasUteis` é nulo.

**Primeiro, conferir se acontece:** abra os três modelos (`TaxaSwapB3`, `NtnbBootstrapAnbima`, `SofrZeroBloomberg`) e veja o que cada um põe em `diasUteis` do vértice:

| Modelo | O que olhar |
|---|---|
| `TaxaSwapB3` | usa o `cDiaUtil` publicado (deve vir sempre preenchido) |
| `NtnbBootstrapAnbima` | já calcula: `prazoDu = cal.diasUteis(base, p)` e passa ao `VerticeConstruido` (conferido em 2026-10-06) |
| `SofrZeroBloomberg` | o eixo da SOFR costuma ser `Actual360`; se for, os dias úteis não importam |

Se nenhum modelo com eixo `Business252` deixa `diasUteis` nulo, **não altere** e anote a prova (arquivo e linha).

**Se acontecer, corrigir assim** (troque pelos nomes reais da `PreparacaoVertices` e do `Calendario`):

```java
// antes: int du = v.diasUteis() == null ? 0 : v.diasUteis();
int du = v.diasUteis() != null
        ? v.diasUteis()
        : calendario.diasUteisEntre(dataBase, v.data());   // contagem (B, d], o mesmo método que o resto da construção usa
```

- O `calendario` é o da curva (o mesmo que a construção já resolve); passe-o para a `PreparacaoVertices` se ela ainda não o recebe.
- O cálculo vem **antes** das checagens de prazo não positivo e repetido.
- O nome do método de contagem está em `domain/calendario/Calendario.java:20-77`.

**Conferir:** um teste com um vértice em `2027-05-17` sem dias úteis, data-base `2026-09-28`, eixo `Business252` e `new Brazil()`: o vértice não é descartado e os dias úteis são os do calendário entre as duas datas.
