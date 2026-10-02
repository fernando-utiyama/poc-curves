# Guia de implementação: processor v0

A spec manda; este guia diz onde e como. Pacotes, nomes de exceção, handler de erro, log e configuração seguem o que o repositório real já usa. Os nomes abaixo são sugestão. Siga a ordem da seção 11. Os testes ficam para o fim.

## 1. Base comum: contrato hexagonal e onde fica cada coisa

A base comum (tarefas 1.1 a 1.8) é feita primeiro, por uma pessoa. Ao fim dela, as nove rotas chegam ao caso de uso, o roteiro comum roda, e cada fonte responde 501 `PROVEDOR_NAO_IMPLEMENTADO`. Em seguida, a mesma pessoa implementa a ANBIMA (tarefas 3.1 e 3.2), que fica como modelo de provedor. Aí vem a **pausa** (tarefa 3.3, seção 11): parar e aguardar a revisão. Depois, cada dev implementa um dos provedores restantes (tarefas 2 ou 4) seguindo o modelo da ANBIMA e trocando só as classes da sua fonte.

Em hexagonal, o "caso de uso" é a **porta de entrada** (interface em `application/port/in`), implementada por um serviço em `application/service`. Os controllers (adaptadores de entrada) só conhecem a porta. O que muda por fonte fica atrás de uma **porta de saída**, `ProvedorCargaPort`, com uma implementação por fonte.

```
adapter/in/web/                     CargaB3Controller, CargaAnbimaController, CargaBloombergController   (base)
application/port/in/                CargaArquivoUseCase                                                   (base)
application/port/out/               ProvedorCargaPort, ArquivoOriginalPort, CurvaPrimariaPort, EngineCargaPort (base)
application/service/                CargaArquivoService (roteiro comum), AvisoEngineService               (base)
application/model/                  Fonte, OrigemCarga, ParametrosFonte, ArquivoObtido, CargaInterpretada, ResultadoCarga,
                                    LinhaBruta (sealed), LinhaB3, TituloAnbima, NoSofr                    (base)
adapter/out/blob/                   ArquivoOriginalBlobAdapter                                            (base)
adapter/out/persistence/            CurvaPrimariaJdbcAdapter (comum)                                      (base)
                                    InsercaoB3, InsercaoAnbima, InsercaoBloomberg                         (uma por provedor)
adapter/out/client/                 EngineCargaClient                                                     (base)
adapter/out/provedor/b3/            B3DownloadSiteProvedor, B3TaxaSwapClient, LeiauteTaxaSwap                    (dev B3)
adapter/out/provedor/anbima/        AnbimaDownloadSiteProvedor, AnbimaMsClient, LeiauteAnbimaMs                  (dev ANBIMA)
adapter/out/provedor/bloomberg/     BloombergDataLicenseProvedor, BloombergDataLicenseClient,
                                    LeiauteRespostaDataLicense, TickersSofrReserva                        (dev Bloomberg)
domain/calendario/                  Calendario, Brazil, UnitedStates, CalendarioPorLista (cópia do engine) (dev ANBIMA)
```

Os consumidores Kafka e as tabelas `mkt.*Raw` não mudam.

### 1.1 Porta de entrada

```java
public interface CargaArquivoUseCase {
    ResultadoCarga baixar(Fonte fonte, LocalDate data, ParametrosFonte parametros);
    ResultadoCarga reprocessar(Fonte fonte, LocalDate data);
    ResultadoCarga receber(Fonte fonte, byte[] conteudo, String nomeArquivo, String usuario);
}

public enum Fonte {
    B3("B3", "TS", "b3"), ANBIMA("ANBIMA", "MS", "anbima"), BLOOMBERG("BLOOMBERG", "BLC2", "bloomberg");
    // fonte e produto (tCurvaPrvdr e webhook do engine), pasta no Blob; idCarga = {fonte}-{produto}-{AAAAMMDD}-{12 do hash}
}

public record ParametrosFonte(List<String> tickers) {}                       // só a Bloomberg usa
public record ResultadoCarga(String idCarga, LocalDate dataBase, String hashArquivo, OrigemCarga origem,
                             Map<String, Integer> linhasPorCodigo, String usuario) {}
```

### 1.2 Porta de saída de cada fonte

```java
public interface ProvedorCargaPort {
    Fonte fonte();
    ArquivoObtido baixar(LocalDate data, ParametrosFonte parametros);   // ArquivoIndisponivelException (503), FonteIndisponivelException (502)
    ArquivoObtido preparar(byte[] conteudo, String nomeArquivo);         // upload: extrair/canonizar; ArquivoInvalidoException (422)
    LocalDate dataBase(ArquivoObtido arquivo);                           // ArquivoInvalidoException (422)
    CargaInterpretada interpretar(ArquivoObtido arquivo, LocalDate dataBase);   // ArquivoInvalidoException (422)
}

public record ArquivoObtido(byte[] bytes, String nomeArquivo) {}
public record CargaInterpretada(Map<String, List<LinhaBruta>> linhasPorCodigo, Map<String, String> codigosRejeitados) {}
public sealed interface LinhaBruta permits LinhaB3, TituloAnbima, NoSofr {}
public record LinhaB3(int diasCorridos, int diasUteis, BigDecimal valor) implements LinhaBruta {}
public record TituloAnbima(int prazoDiasUteis, BigDecimal taxa) implements LinhaBruta {}      // taxa pode ser null
public record NoSofr(String ticker, BigDecimal valor) implements LinhaBruta {}
```

O `CargaArquivoService` recebe todos os `ProvedorCargaPort` por injeção (`List<ProvedorCargaPort>`) e monta um `EnumMap<Fonte, ProvedorCargaPort>`; faltar uma fonte derruba a subida.

### 1.3 Provedores provisórios (base)

Cada fonte nasce com a sua classe implementando `ProvedorCargaPort` e lançando `ProvedorNaoImplementadoException` (501 `PROVEDOR_NAO_IMPLEMENTADO`) em todos os métodos. O dev da fonte substitui o corpo dessa classe; nada fora da pasta da sua fonte e da sua classe `Insercao*` precisa mudar.

### 1.4 Inserção por tabela

```java
public interface InsercaoLinhaBruta {
    Fonte fonte();
    String tabela();                                           // tBtrsCurvaPrimr, tAnbmaCurvaPrimr, tBbergCurvaPrimr
    void inserir(JdbcTemplate jdbc, int id, String curva, LocalDate dataBase, LinhaBruta linha);
}
```

O `CurvaPrimariaJdbcAdapter` faz a parte comum (mapeamento, trava, apagar, `MAX + 1`, contagem) e chama a `InsercaoLinhaBruta` da fonte para cada linha. A base cria as três com `inserir` lançando `ProvedorNaoImplementadoException`.

## 2. Configuração (só chaves novas)

```yaml
processor:
  blob:
    account-url: ${PROCESSOR_BLOB_ACCOUNT_URL}       # Managed Identity; string de conexão só no perfil local
    container: ${PROCESSOR_BLOB_CONTAINER}
  fontes:
    b3-url: https://www.b3.com.br/pesquisapregao/download?filelist=TS{AAMMDD}.ex_
    anbima-url: https://www.anbima.com.br/informacoes/merc-sec/arqs/ms{AAMMDD}.txt
    timeout-segundos: 60
  bloomberg:
    base-url: ${BLOOMBERG_DL_BASE_URL}               # [A CONFIRMAR] host da API do Data License
    catalogo: ${BLOOMBERG_DL_CATALOGO}               # [A CONFIRMAR] catálogo da conta
    campo: PX_LAST
    faixa-pedido-minutos: 30
    espera-segundos: 90
    # id e segredo da credencial: Key Vault, nunca no yml
  engine:
    base-url: ${ENGINE_BASE_URL}
    timeout-segundos: 150
    aviso-janela-minutos: 10
    aviso-alerta-minutos: 2
  upload:
    tamanho-maximo-mb: 10
spring.servlet.multipart.max-file-size: 10MB
spring.servlet.multipart.max-request-size: 11MB
```

`{AAMMDD}` é trocado pela data. Nenhum outro parâmetro da chamada entra no endereço.

## 3. Rotas e roteiro comum

| Rota | Caso de uso | `origem` |
|---|---|---|
| `GET /api/b3/taxa-swap/download?date=` | `baixar(B3, data, vazio)` | `DOWNLOAD` |
| `GET /api/b3/taxa-swap/reprocessamento?dataBase=` | `reprocessar(B3, data)` | `REPROCESSAMENTO` |
| `POST /api/b3/taxa-swap/upload` (multipart `arquivo`, `X-Usuario`) | `receber(B3, bytes, nome, usuario)` | `UPLOAD` |
| idem `/api/anbima/ms/...` | idem com `ANBIMA` | idem |
| idem `/api/bloomberg/sofr/...`; `tickers` no download (no reprocessamento, aceito e ignorado) | idem com `BLOOMBERG` e `ParametrosFonte(tickers)` | idem |

O limite de 10 MB do multipart (`MaxUploadSizeExceededException`) é mapeado para 422 `ARQUIVO_INVALIDO` no handler. Validação nos controllers: data `AAAA-MM-DD` e não futura (`LocalDate.now(ZoneId.of("America/Sao_Paulo"))`); `tickers` com `^[A-Za-z0-9]+( [A-Za-z0-9]+)*$`, até 50 caracteres cada, até 100, sem repetidos; `X-Usuario` obrigatório no upload. O mapeamento de exceção para código HTTP (seção "Rotas no contrato do orquestrador" da spec, mais 501 `PROVEDOR_NAO_IMPLEMENTADO`) fica no handler que o repositório já tem.

Roteiro do `CargaArquivoService`, igual para as três fontes:

1. obter o arquivo: `provedor.baixar(...)`, `provedor.preparar(...)` (upload) ou o original mais recente do Blob (reprocessamento);
2. `dataBase = provedor.dataBase(arquivo)`; no download, diferente da pedida = 503 `ARQUIVO_INDISPONIVEL` (sem arquivar); no upload, futura = 422. Se `dataBase(...)` lançar `ArquivoInvalidoException`: no download, arquivar sob a data pedida e responder 422; no upload, responder 422 sem arquivar (log com usuário e nome do arquivo);
3. `hash` e `idCarga`; gravar o original no Blob (antes de interpretar, para o rejeitado ficar auditável; no reprocessamento, já existe);
4. `provedor.interpretar(arquivo, dataBase)` (422 se o arquivo inteiro cai);
5. gravar no banco (seção 8);
6. responder 200 e disparar o aviso ao engine em segundo plano (seção 9).

O que é "ainda não saiu" (503 antes de arquivar) é decidido dentro do `baixar` de cada provedor: na Bloomberg, por exemplo, ticker sem valor na resposta.

## 4. Blob (`ArquivoOriginalBlobAdapter`)

- Caminho: `{b3|anbima|bloomberg}/{AAAAMMDD}/cargas/{idCarga}/{TaxaSwap.txt | ms{AAMMDD}.txt | nome da resposta}`.
- Gravação: `BlobClient.uploadWithResponse(...)` com `BlobRequestConditions().setIfNoneMatch("*")`; 409 = já existe = sucesso.
- Reprocessamento: listar o prefixo `{fonte}/{AAAAMMDD}/cargas/` e pegar o de maior data de criação; nenhum = 404 com o prefixo.
- Só originais: nada de estado, resultado ou log no Blob.

## 5. B3

- Download: `GET` no endereço; 404, corpo vazio ou sem a assinatura de zip (`PK\x03\x04`) = 503 `ARQUIVO_INDISPONIVEL`.
- Extração: `java.util.zip.ZipInputStream` no `.ex_`; dentro, o primeiro nível é outro zip; dentro dele, o único `TaxaSwap.txt`. Mais de dois níveis ou mais de um `TaxaSwap.txt` = 422.
- Forma canônica, `idCarga`, leiaute e validação: os da spec `b3-taxaswap-publicacao` e da spec `b3-carga-processor` (guia da `processor-carga-b3`, seção 1.6). Se a `processor-carga-b3` já tiver o `LeiauteTaxaSwap`, use a mesma classe.
- `linhasPorCodigo`: vértices gravados por código (278 por código, normalmente).

## 6. ANBIMA (`LeiauteAnbimaMs`)

- Ler em `StandardCharsets.ISO_8859_1`; quebrar em linhas; achar a primeira que começa com `Titulo@`; as colunas são localizadas pelo nome nessa linha: `Titulo`, `Data Referencia`, `Codigo SELIC`, `Data Vencimento`, `Tx. Indicativas`.
- Linhas seguintes com `Titulo` = `NTN-B` e `Codigo SELIC` terminando em `99`.
- Datas em `AAAAMMDD` (`DateTimeFormatter.BASIC_ISO_DATE`); `dataBase(...)` devolve a `Data Referencia` (todas as linhas com a mesma, senão 422).
- Chamadas à B3, à ANBIMA e ao Data License sem `X-Correlation-Id` (só o engine recebe).
- Taxa: trocar `,` por `.` e `new BigDecimal(texto)`; vazio ou `--` = `null`.
- Prazo: `P = brazil.ajustar(vencimento, Following)`; `vVertcCurva = brazil.diasUteisEntre(dataBase, P)`, com as mesmas funções que o engine usa (seção 4 do guia do engine).
- Código na fonte: `NTN-B`; `linhasPorCodigo` = `{ "NTN-B": títulos gravados }`.
- Sem cabeçalho, sem alguma das colunas, sem NTN-B inteira ou vencimento ilegível = 422.

## 7. Bloomberg

### 7.1 Pedido de histórico (`BloombergDataLicenseClient`)

Comportamento (os endpoints exatos seguem a documentação da conta, [A CONFIRMAR]):

1. Obter o token com a credencial do Key Vault (OAuth2, como a API do Data License exige); guardar em memória até expirar.
2. Montar o identificador: `sofr` + `AAAAMMDD` + 8 primeiros do SHA-256 hexa da lista ordenada de tickers unida por `,` + faixa (`HHmm` de Brasília arredondado para baixo em `faixa-pedido-minutos`). Ex.: `sofr20260914a1b2c3d41800`.
3. Criar o pedido de histórico no catálogo com esse identificador: universo = tickers (tipo ticker), campo = `processor.bloomberg.campo`, datas inicial e final = data-base, saída em CSV. Se a API responder que o identificador já existe, seguir para o passo 4 (outra chamada ou instância já pediu).
4. Consultar, a cada 5 segundos até `espera-segundos`, se a resposta do pedido está pronta; pronta, baixar o arquivo.
5. Não pronta = 503 `ARQUIVO_INDISPONIVEL` com o identificador.

### 7.2 Resposta (`LeiauteRespostaDataLicense`)

- Linhas com ticker, data e valor ([A CONFIRMAR] nomes das colunas do CSV da conta).
- Para cada ticker pedido: uma linha com a data-base e valor numérico. Faltou algum = 503 no download, 422 no upload, citando os tickers.
- Gravação: `cTickerBberg` = o ticker completo (`S0490Z 15M BLC2 Curncy`; coluna `VARCHAR(50)` do `001_SCRIPT_INICIAL.sql`); `vPrecoUlt` = valor como veio; código na fonte = primeiro termo (`S0490Z`); `linhasPorCodigo` = `{ "S0490Z": nós gravados }`.

### 7.3 Lista de reserva (temporária)

```java
// TODO(retirar): reserva até todas as tarefas do orquestrador mandarem "tickers".
// Para retirar: apagar esta classe e, em CargaBloombergService, trocar o uso por 400 PARAMETRO_INVALIDO.
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

Uso, numa única linha do `CargaBloombergService` (com o log `TICKERS_RESERVA` quando cai na reserva):

```java
List<String> tickers = informados.isEmpty() ? TickersSofrReserva.TICKERS : informados; // TODO(retirar) reserva
```

## 8. Gravação (`CurvaPrimariaJdbcAdapter`)

Uma transação por carga (`@Transactional`), com o mesmo SQL da `processor-carga-b3` trocando a tabela:

```sql
SELECT cTickerPrvdr, cTickerIndcd FROM tCurvaPrvdr WHERE iPrvdrDados = ? AND cPrvdrMercd = ?;   -- B3/TS, ANBIMA/MS, BLOOMBERG/BLC2
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;           -- cada curva, em ordem do nome, 60 s
DELETE FROM {tabela} WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT ISNULL(MAX(cIdtfdUnic), 0) FROM {tabela} WITH (UPDLOCK, HOLDLOCK);
INSERT ...;                                                                                      -- colunas da tabela na spec
SELECT COUNT(*) FROM {tabela} WHERE cTickerIndcd = ? AND dBaseReft = ?;                         -- = linhas do código
```

`{tabela}` vem de um enum por fonte (`tBtrsCurvaPrimr`, `tAnbmaCurvaPrimr`, `tBbergCurvaPrimr`), nunca da requisição. Os valores de `iPrvdrDados` e `cPrvdrMercd` e o `trim` das colunas `CHAR` seguem o que o banco real tem cadastrado. Tempo limite de 60 s nos comandos de trava e de `MAX`.

## 9. Aviso ao engine (`AvisoEngineService`)

- Depois do commit, submeter a um executor de threads virtuais (`Executors.newVirtualThreadPerTaskExecutor()`).
- `POST {engine}/api/v1/cargas` com `{ idCarga, fonte, produto, dataBase, linhasPorCodigo }`, `X-Correlation-Id`, sem `Authorization`, tempo limite de 150 s.
- Repetir em erro de rede, 5xx e 409: espera de 1 s dobrando até 60 s, com variação, por até 10 min; aos 2 min, log `AVISO_ATRASADO`; no fim, ou em 4xx diferente de 409, log de erro `CARGA_FALHOU` e incremento da métrica (`processor.carga.falhou`, rótulo `fonte`).
- Logar o resultado por curva que o engine devolve.

## 10. bff e front

- bff: `POST /api/v1/cargas/upload`, segurança e perfil como as outras ações de operação do bff; repassar com o cliente HTTP que o bff já usa, mapeando `fonte` para o caminho de upload do processor; `X-Usuario` = nome do usuário do token; não repassar `Authorization`; tempo limite de 120 s.
- Front: tela "Carga manual de arquivo" no menu de operação; textos e datas em pt-BR (`dd/mm/aaaa`); mensagens de erro vindas do processor.

## 11. Ordem

1. **Base comum (uma pessoa):** seções 1, 2, 3, 4, 8 (parte comum), 9 e o log (tarefas 1.1 a 1.8). Entregar com as nove rotas respondendo 501.
2. **ANBIMA, provedor modelo (a mesma pessoa):** seção 6, com o calendário (tarefas 3.1 e 3.2). Entregar com as rotas da ANBIMA gravando em `tAnbmaCurvaPrimr` e B3 e Bloomberg ainda respondendo 501.
3. **PAUSA (tarefa 3.3):** parar. Rodar `mvn compile` e os testes das tarefas 1.x e 3.x, registrar o que foi feito e o que ficou pendente, e aguardar a revisão. Não começar a B3, a Bloomberg nem o bff e o front antes disso.
4. **Provedores restantes, em paralelo (um dev cada), seguindo o modelo da ANBIMA:** B3 (seção 5; tarefa 2.1) e Bloomberg com a reserva (seção 7; 4.1 e 4.2). Cada um troca só a sua pasta `adapter/out/provedor/{fonte}/` e a sua `Insercao*`, e escreve os seus testes.
5. **bff e front (em paralelo, depois da pausa):** seção 10 (5.1 e 5.2).
6. Fechamento (6.x) e homologação (7.x).

Testes: um por cenário da spec, com o `ms260928.txt` real e o `TaxaSwap.txt` de `docs/` nos recursos de teste; servidores simulados para B3, ANBIMA, Data License e engine; acesso ao banco e Blob simulados.
