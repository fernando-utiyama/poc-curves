# Guia de implementação: processor-bloomberg (para fazer na mão)

> **Jackson 3 (Spring Boot 4):** para injetar o mapper do Spring, use `tools.jackson.databind.ObjectMapper` (ou `tools.jackson.databind.json.JsonMapper`), com `tools.jackson.core.type.TypeReference` e `JsonNode` de `tools.jackson.databind`. **Nunca** `com.fasterxml.jackson.databind.ObjectMapper`/`JsonMapper`: o Boot 4 não cria esse bean, e a aplicação não sobe ("required a bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found"). Só as anotações continuam em `com.fasterxml.jackson.annotation`. No Jackson 3, `asText()` virou `asString()`.

> **Parâmetros de rota em objeto:** controller não recebe uma fila de `@RequestParam`. Com mais de dois parâmetros, agrupe num record: `@ModelAttribute FiltroX filtro` para consultas `GET` (o Spring preenche os campos pelos nomes da query) e `@RequestBody PedidoX pedido` para comandos `POST`/`PUT`. `@PathVariable` (identificadores como `codigo` e `dataBase`) continua separado.

> **Tipagem forte (Java 21):** dentro do domínio e das portas, nada de `Map<String, Object>`, `Object[]`, `Object` genérico ou `String` com JSON dentro. Valores fechados viram `enum`; dados viram `record`; variantes viram `sealed interface` com records. JSON cru só na borda (controller, cliente HTTP, coluna `cModDado`), desserializado direto num record; consultas nativas devolvem projeção em record.

> **Hexagonal:** o provedor implementa `ProvedorCargaPort` (porta de saída da base comum); o cliente HTTP, o leiaute e a inserção ficam em `adapter/out` e em `application/model/leiaute`; `application` não importa `adapter`. Um tipo por arquivo, record fora de interface, comentários de uma linha, `now()` sem zona (o fuso é fixado no `main`).

> **Base comum primeiro:** esta change assume a base da `processor-v0` (seção 1: rotas, `CargaArquivoService`, Blob, persistência, `AvisoEnginePort`) já pronta, com o `BloombergDataLicenseProvedor` provisório lançando `NotImplementedException`. Aqui ele é implementado.

> **Spec não crava código real:** nomes de pacote, de exceção e de erro seguem o repositório real; os caminhos abaixo são a partir de `src/main/java/<pacote raiz>/`.

## 1. Onde fica cada coisa

| Arquivo | Para quê |
|---|---|
| `adapter/out/client/bloomberg/BloombergDataLicenseClient` | token, criar e consultar o pedido, baixar a resposta |
| `adapter/out/client/bloomberg/BloombergDataLicenseProvedor` | implementa `ProvedorCargaPort` (`baixar`, `preparar`, `dataBase`, `interpretar`) |
| `adapter/out/client/bloomberg/TickersSofrReserva` | os 20 tickers, temporária |
| `application/model/leiaute/LeiauteRespostaDataLicense` | lê a resposta e confere os tickers (Java puro, sem Spring) |
| `adapter/out/persistence/jdbc/BbergCurvaPrimrInsercao` | o `INSERT` em `tBbergCurvaPrimr` |
| `processor.bloomberg` no `application.yml` | `base-url`, `catalogo`, `campo`, `faixa-pedido`, `espera` |

## 2. Antes de codar: o pedido de teste (tarefa 1.1)

Com a conta do Data License, faça **um** pedido de histórico (qualquer data útil recente) com os 20 tickers e os campos `PX_MID` e `PX_LAST`, e registre no `design.md`:
1. o host, o catálogo e os endpoints exatos (os `[A CONFIRMAR]` do §3);
2. o formato da resposta (CSV? colunas e separador) e uma amostra de 3 linhas, **sem segredo**;
3. se o `PX_MID` vem preenchido para os 20; se vier vazio para algum, use `PX_LAST` e mude só `processor.bloomberg.campo`.

Sem isso, o §3 e o §4 ficam com `TODO(revisao)`.

## 3. `BloombergDataLicenseClient`

Passos (endpoints `[A CONFIRMAR]` com a documentação da conta):
1. Token OAuth2 com a credencial do Key Vault, em memória até expirar.
2. Identificador: `sofr` + `AAAAMMDD` + 8 primeiros do SHA-256 hexa da lista ordenada de tickers unida por `,` + a faixa (`HHmm` de Brasília arredondado para baixo em `faixa-pedido`). Ex.: `sofr20260914a1b2c3d41800`.
3. Criar o pedido (universo = tickers, campo = `campo`, datas = data-base, saída CSV); "identificador já existe" = seguir para o passo 4.
4. Consultar a cada 5 s até `espera`; pronta, baixar. Não pronta = 503 `ARQUIVO_INDISPONIVEL` com o identificador.

```java
public ArquivoObtido buscar(LocalDate dataBase, List<String> tickers) {
    String id = identificador(dataBase, tickers);
    garantirPedido(id, dataBase, tickers);
    byte[] resposta = aguardarResposta(id);          // 503 ARQUIVO_INDISPONIVEL se passar de `espera`
    return new ArquivoObtido(resposta, id + ".csv");
}
```

- Um `RestClient` próprio do cliente, com o tempo limite da configuração; sem o cabeçalho `X-Correlation-Id` nas chamadas de saída.
- 401 ou 403 do Data License = 502 `FONTE_INDISPONIVEL` com mensagem sobre a credencial.

## 4. `LeiauteRespostaDataLicense` e o provedor

```java
public record NoSofr(String ticker, BigDecimal valor) {}

public List<NoSofr> ler(byte[] bytes, LocalDate dataBase, List<String> esperados) {
    // 1 linha por ticker: data e valor numérico; BigDecimal direto do texto, sem double
    // faltou valor, ticker fora da lista ou a mais: lança com a lista dos que faltam e dos que sobram
}
```

- `dataBase(arquivo)`: a data lida da resposta (422 se ilegível).
- `interpretar`: devolve `Map<String, List<CurvaPrimr>>` com a chave = membro (`S0490Z`) e os 20 `BbergCurvaPrimr(ticker, valor)`.
- Download: tickers = os do corpo, ou os da reserva (`TICKERS_RESERVA` no log). Upload: tickers = os do arquivo; a conferência dos 20 vale também aqui (422 citando os que faltam e os que sobram).
- Faltou ticker no download = 503; no upload = 422 (a base comum já traduz pela origem).

## 5. `BbergCurvaPrimrInsercao`

```sql
INSERT INTO tBbergCurvaPrimr (cTickerIndcd, dBaseReft, cTickerBberg, vPrecoUlt) VALUES (?, ?, ?, ?);
```

Sem `cIdtfdUnic` (a coluna é `IDENTITY`). `cTickerBberg` = ticker completo (`VARCHAR(50)`). O resto da gravação (trava da curva, apagar e inserir, conferir contagem) é da base comum (`processor-v0`, seção 7).

## 6. `TickersSofrReserva`

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

Usada só em `CargaController.parametros(...)`, quando `tickers` vem ausente ou vazio.

## 7. Configuração

```yaml
processor:
  bloomberg:
    base-url: ${BLOOMBERG_DL_BASE_URL}     # [A CONFIRMAR] host da API do Data License
    catalogo: ${BLOOMBERG_DL_CATALOGO}     # [A CONFIRMAR] catálogo da conta
    campo: PX_MID                          # valor do fechamento; gravado em vPrecoUlt
    faixa-pedido: 30m
    espera: 90s
    # id e segredo da credencial: Key Vault, nunca no yml
```

## 8. Testes (poucos e amplos)

Com o Data License **falso** (um servidor de teste, sem rede):
- `BloombergCargaTest`: pedido novo; pedido já existente na mesma faixa (não faz outro); resposta atrasada (503); ticker sem valor (503 no download, 422 no upload); credencial recusada (502); `TICKERS_RESERVA` quando a chamada vem sem `tickers`.
- `LeiauteRespostaDataLicenseTest`: os 20 tickers (ok); 19 (cita o que falta); 21 com um fora da lista (cita o que sobra); valor lido como `BigDecimal` sem arredondar.
- Gravação: o `INSERT` sem `cIdtfdUnic` e a contagem de 20 conferida.
