# Parâmetros de cálculo tipados (engine)

Troca o `Map<String, Object>` lido de `cModDado` por `ParametrosCalculo` (record com os enums que o engine já tem) e renomeia `CurvaMercadoLida` para `CadastroCurva`. O JSON de `cModDado` é o mesmo que a curves grava.

Pacotes estão como `br.com.poc`: troque por `br.com.bradesco`.

## Ordem

1. **Renomear com a IDE** (Shift+F6 sobre o record): `CurvaMercadoLida` → `CadastroCurva`. Atualiza adaptador, validador e testes de uma vez.
2. **Copiar** (por cima, em `domain/cadastro/`):
   - `ParametrosCalculo.java` (novo)
   - `CadastroCurva.java`
   - `ValidadorCadastro.java`
3. **Copiar o `CurvaMercadoJpaAdapter.java`** (abaixo).
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

Copiar por cima o `CurvaMercadoJpaAdapter.java` desta pasta (em `adapter/out/persistence/jpa/`). Muda:

- o construtor guarda um `ObjectReader` de `ParametrosCalculo` com `FAIL_ON_UNKNOWN_PROPERTIES` ligado e `ACCEPT_FLOAT_AS_INT` desligado, no lugar do `ObjectMapper`;
- `montarLida` virou `montarCadastro`: sem as seis variáveis anuláveis, sem `Map` e sem `TypeReference`; a leitura do `cModDado` está em `lerParametros`, que devolve os parâmetros ou o motivo do erro;
- sai o `curva.getUsuarCalc()` (campo removido do `CadastroCurva`).

Rotas, trava, resumo e provedores ficam iguais.

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
