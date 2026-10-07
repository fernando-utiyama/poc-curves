# Parâmetros de cálculo tipados (engine)

Troca o `Map<String, Object>` lido de `cModDado` por `ParametrosCalculo` (record com os enums que o engine já tem) e renomeia `CurvaMercadoLida` para `CadastroCurva`. O JSON de `cModDado` é o mesmo que a curves grava.

Pacotes estão como `br.com.poc`: troque por `br.com.bradesco`.

## Ordem

1. **Renomear com a IDE** (Shift+F6 sobre o record): `CurvaMercadoLida` → `CadastroCurva`. Atualiza adaptador, validador e testes de uma vez.
2. **Copiar** (por cima, em `domain/cadastro/`):
   - `ParametrosCalculo.java` (novo)
   - `CadastroCurva.java`
   - `ValidadorCadastro.java`
3. **Ajustar à mão o `CurvaMercadoJpaAdapter.montarLida`** (abaixo).
4. **Testes** que montam `CurvaMercadoLida`/`CadastroCurva` com `Map` (abaixo).

## O que muda no `CadastroCurva` (antigo `CurvaMercadoLida`)

| Antes | Depois |
|---|---|
| `Map<String, Object> parametrosJson` | `ParametrosCalculo parametros` |
| `String erroJson` | `String erroParametros` |
| `int contagemConfiguracoesVigentes` | `int configuracoesVigentes` |
| `String usuarioCalculo` (sem uso) | sai |
| `String jsonParametrosRaw` | fica (vai para a proveniência da `CurvaMercado`) |

## `CurvaMercadoJpaAdapter`

Campo novo, montado uma vez no construtor (Jackson 3):

```java
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;

private final ObjectReader leitorParametros;

// no construtor, depois de receber o objectMapper:
this.leitorParametros = objectMapper.readerFor(ParametrosCalculo.class)
        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)    // chave desconhecida → CADASTRO_INVALIDO
        .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT);      // 7.9 em CASAS_DECIMAIS → CADASTRO_INVALIDO
```

Se o adaptador usa `@RequiredArgsConstructor`, troque por um construtor escrito à mão com os mesmos campos mais essa linha.

No `montarLida`, troque o bloco que lia o `Map` (o `TypeReference<Map<String, Object>>`, o `params` e o `erroJson`) por:

```java
ParametrosCalculo parametros = null;
String erroParametros = null;
if (rawJson != null && !rawJson.isBlank()) {
    try {
        parametros = leitorParametros.readValue(rawJson);
    } catch (JacksonException e) {
        erroParametros = e.getOriginalMessage();
    }
}
```

E no `new CadastroCurva(...)`: passe `parametros` e `erroParametros` no lugar de `params` e `erroJson`, e tire o argumento do `usuarioCalculo`. Apague os imports de `TypeReference`, `Map` e `Collections` que sobrarem.

## O que muda no comportamento

- Chave desconhecida, tipo errado ou valor fora de um enum continuam dando `CADASTRO_INVALIDO`, mas com **um** detalhe (`cModDado`, "JSON inválido: ...") com a mensagem do Jackson, em vez de um detalhe por chave.
- `FREQUENCY` = `NoFrequency`, `Once` ou `OtherFrequency` passa a ser recusada (era aceita).
- `MODO_ARREDONDAMENTO` aceita só `HALF_UP`, `HALF_EVEN` e `DOWN`.
- Regras de combinação, calendário, origens e modelo ficam iguais.

## Testes

Onde um teste monta `CurvaMercadoLida` com um `Map` de parâmetros, troque por um `ParametrosCalculo`, por exemplo:

```java
static ParametrosCalculo pre() {
    return new ParametrosCalculo(
        BaseInterpolacao.Discount, DayCounter.Business252, Frequency.Annual,
        "Brazil", "Settlement", BusinessDayConvention.Following,
        Extrapolacao.Disabled, Extrapolacao.FlatValue, "60Y", 7, RoundingMode.HALF_UP,
        null, null, null, Map.of());
}
```

Testes que verificavam um detalhe por chave inválida (tipo errado, chave desconhecida) passam a testar o adaptador: o JSON inválido gera `erroParametros`, e o validador devolve o detalhe `cModDado`. Se algum quebrar, mande a foto.

`TabelaParametros` fica como está (o `/valores-cadastro` continua usando).
