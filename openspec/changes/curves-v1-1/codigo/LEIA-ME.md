# Parâmetros de cálculo tipados (curves)

Troca o `Map<String, Object> parametros` por `ParametrosCalculo` (record com enums) em todo o caminho:
request → input → validador → domínio → adaptador (cModDado) → response. O JSON gravado em `cModDado`
continua com as mesmas chaves (`BASE_INTERPOLACAO`...), então o engine não muda.

Pacotes estão como `br.com.poc`: troque por `br.com.bradesco` (Ctrl+R no arquivo).

| Arquivo deste diretório | Onde fica no repositório | O que muda |
|---|---|---|
| `parametros/ParametrosCalculo.java` | `domain/cadastro/` | novo |
| `parametros/BaseInterpolacao.java`, `DayCounter.java`, `Frequency.java`, `BusinessDayConvention.java`, `Extrapolacao.java`, `ModoArredondamento.java` | `domain/cadastro/` | novos enums, um por arquivo |
| `ValidadorParametros.java` | `domain/cadastro/` | substitui: recebe `ParametrosCalculo`; os `Set<String>` para `/valores` vêm dos enums |
| `ConfiguracaoCurva.java` | `domain/cadastro/` | substitui: `ParametrosCalculo parametros`, **sem** `parametrosJson`, com `comFimVigencia(...)` |
| `CriarConfiguracaoCurvaInput.java` | `domain/cadastro/` | substitui: `ParametrosCalculo parametros` |
| `CriarConfiguracaoCurvaRequest.java` | `adapter/in/api/rest/dto/` | substitui: `ParametrosCalculo parametros` |
| `ConfiguracaoCurvaResponse.java` | `adapter/in/api/rest/dto/` | substitui: `ParametrosCalculo parametros` |
| `ConfgCurvaPersistenceAdapter.java` | `adapter/out/persistence/` | substitui: lê e grava `cModDado` como `ParametrosCalculo` |
| `ConfiguracaoCurvaService.java` | `application/service/` | substitui: sem `parametrosJson`; fechar e reabrir vigência com `comFimVigencia` |

## Também mexer, à mão

1. **`ConfiguracaoCanonicoState`**: trocar o tipo do campo `parametros` de `Map<String, Object>` para `ParametrosCalculo` (o resto fica). Confira que a ordem dos argumentos em `ConfiguracaoCurva.toCanonicoState()` bate com a do seu record.
2. **`application.yml`**: impedir que `7.9` vire `7` no `CASAS_DECIMAIS` e nas versões:
   ```yaml
   spring:
     jackson:
       deserialization:
         accept-float-as-int: false
   ```
3. **Quem mais chama `ValidadorParametros.validar`** (ex.: a conferência de alteração da curva no `CurvaMercadoService`): passa `configuracao.parametros()`, que agora já é `ParametrosCalculo`; se compilar, está certo.
4. **Imports**: se algum pacote de entidade/repositório for diferente (`adapter.out.persistence.entity`, `...repository`), ajuste pelo Alt+Enter.
5. **Trava da curva (item 6)**: se você já pôs `travarPorNome` no `criar` e no `excluir`, recoloque a linha logo depois do `obterCurva(...)` nos dois.

## O que muda no comportamento

- Valor fora da lista (`"BASE_INTERPOLACAO": "Desconto"`) ou tipo errado agora é recusado pelo Jackson antes do serviço, com a resposta de JSON inválido que o tratador já dá para corpo malformado, em vez de 422 com o campo. Se quiser 422 por campo, me mande o trecho do `AbstractRestExceptionHandler` que trata `HttpMessageNotReadableException`.
- Chave desconhecida dentro de `parametros` passa a ser ignorada (não é mais 422). Ela nunca chega ao `cModDado`, porque o JSON gravado sai do record.
- `cModDado` ilegível (gravado fora da curves) volta como `parametros = null` na consulta, como antes voltava o mapa vazio.

## Testes

Vão quebrar os que montam `Map<String, Object>` de parâmetros e os que criam `ConfiguracaoCurva` com 9 argumentos. Troque o mapa por um `ParametrosCalculo`, por exemplo:

```java
static ParametrosCalculo pre() {
    return new ParametrosCalculo(
        BaseInterpolacao.Discount, DayCounter.Business252, Frequency.Annual,
        "Brazil", "Settlement", BusinessDayConvention.Following,
        Extrapolacao.Disabled, Extrapolacao.FlatValue, "60Y", 7, ModoArredondamento.HALF_UP,
        null, null, null, Map.of());
}
```

e tire o argumento `parametrosJson` dos `new ConfiguracaoCurva(...)`. Nos testes do validador, os casos de "tipo errado" e "valor fora da lista" saem (agora é o Jackson que recusa); ficam obrigatoriedade, faixas e combinações.
