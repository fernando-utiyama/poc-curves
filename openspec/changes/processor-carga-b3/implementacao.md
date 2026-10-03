# Guia de implementação: processor-carga-b3

Guia passo a passo para aplicar esta change com o mínimo de decisões. A spec manda; este guia diz **onde** e **como**. Ordem: seção 1 (processor), seção 2 (testes, ao final), seção 3 (verificação ponta a ponta). Cada passo tem arquivo, o que fazer e o teste que prova.

Este guia é o lado do processor da divisão do antigo `conector-b3-webhook-ingest`. O conector (change `conector-b3-webhook-ingest`, com guia próprio) obtém o arquivo, arquiva no Blob e publica o aviso de carga em `tp-event-b3-curve`; o formato do aviso é o da spec `b3-taxaswap-publicacao` desse change. O processor consome esse aviso. Os dois são implantados juntos.

Regras para quem implementa:
- Não invente nome, rota, variável, tabela ou coluna fora deste guia e das specs.
- Não crie tópico Kafka, fila, tabela, sequência nem índice.
- Mensagens para o usuário em pt-BR, com acentuação. Instantes no horário de Brasília (`America/Sao_Paulo`), com deslocamento.
- Arquivos de configuração existentes (`services/processor/pom.xml`, `src/main/resources/application*.yml`, `KafkaConfig.java`, `FeignConfiguration.java`): **não reescrever**. Só verificar se têm o que este guia indica e, se faltar algo, acrescentar apenas esse item.

Vetor de teste real (use em todos os testes de ponta a ponta), `docs/TaxaSwap.txt`:
- 30.481 linhas de 72 caracteres, fim de linha `\r\n`, 2.255.594 bytes, data de geração `20260914`, 114 códigos (`PRE`, `DCL`, `DPL`, `INP`, `PTX` com 278 linhas cada).
- Forma canônica (a que o conector arquiva e o processor lê do Blob): 2.225.113 bytes, SHA-256 `46a249c60bec1ac111d69934b8486213eedecdf9b772246df86a11b5f120d9e8`.
- `idCarga`: `B3-TS-20260914-46a249c60bec`.

---

## 1. Processor (`services/processor`, Java 21, Spring Boot)

Pacote base: no poc, `br.com.poc.starter.srv.hex`; no real, o do serviço. **O código real manda nos detalhes** (mesma regra dos guias do engine e do curves): classes, configuração do Kafka, nome do tópico, biblioteca de log e registro de beans são os que o serviço já tem; o que não muda é o comportamento das specs. **Mesma base do engine** (guia do `engine-construcao-curvas`, seção 0):
- **Hexagonal, no layout que o serviço já tem:** `application/model` é o domínio (Java puro, sem Spring, JPA, Jackson ou Azure); `application/port/in` e `application/port/out` são as portas; `application/service` implementa os casos de uso; `adapter/in` (Kafka) e `adapter/out` (Blob, banco, cliente do engine) implementam as portas. O serviço só conhece as portas, nunca o adaptador.
- **Java 21 nativo:** records para todo dado, sealed para as falhas, `switch` com pattern matching, `HexFormat` e `MessageDigest` para o SHA-256, `String.lines()`, `Thread.sleep(Duration)`, `ThreadLocalRandom`. Nada de tipo próprio de data, relógio ou "utils".
- **Virtual threads:** `spring.threads.virtual.enabled: true`. Conferir se o listener do Kafka de fato roda em virtual thread: uma fábrica de listener própria que não passa pelo configurador do Spring Boot (`ConcurrentKafkaListenerContainerFactoryConfigurer`) ignora essa propriedade e as `spring.kafka.listener.*` (é o caso do processor real). Nesse caso, configurar a fábrica pelo configurador; o comportamento das novas tentativas não depende disso, só o uso de thread do sistema durante a espera. Sem `synchronized`.
- **Fuso da JVM:** o `main` faz `TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))` antes do Spring, e um `ApplicationRunner` impede a subida com outro fuso. Os instantes de log (`CARGA_FALHOU` etc.) saem de `OffsetDateTime.now()`, já com `-03:00`.
- **Sem Lombok nem MapStruct no código novo:** conversões em métodos estáticos dos records.

### 1.1 Remover

- `adapter/out/persistence/inmemory/entity/B3CurveRawEntity.java`, `repository/B3JpaRepository.java`, `adapter/B3CurveRepositoryAdapter.java`;
- `application/model/B3CurveRaw.java`, `application/port/in/ProcessB3CurveUseCase.java`, `application/port/out/B3CurveRepositoryPort.java`, `application/service/ProcessB3CurveService.java`;
- testes em `src/test/java/br/com/poc/starter/srv/hex/`: `adapter/out/persistence/inmemory/adapter/B3CurveRepositoryAdapterTest.java`, `adapter/out/persistence/inmemory/entity/B3CurveRawEntityTest.java`, `application/service/ProcessB3CurveServiceTest.java`; reescrever `adapter/in/consumer/kafka/B3KafkaConsumerTest.java` (ver 1.9).
- Os consumidores das outras fontes (Anbima, Bloomberg, CME, LCH, Treasury) ficam como estão.

### 1.2 Dependências (`pom.xml`)

Verificar se existem `com.azure:azure-storage-blob` e `com.azure:azure-identity` (versão pelo BOM `com.azure:azure-sdk-bom` **1.3.8** em `dependencyManagement`, a mesma do engine); se faltarem, acrescentar só elas. `spring-boot-starter-data-jpa` já traz `JdbcTemplate`; `spring-boot-starter-actuator` já traz o Micrometer.

### 1.3 Configuração (verificar em `application.yml`)

Conferir que as chaves abaixo existem com estes valores; acrescentar só as que faltarem, sem mexer nas demais.

```yaml
processor:
  blob:
    endpoint: ${PROCESSOR_BLOB_ENDPOINT}        # https://<conta>.blob.core.windows.net
    container: ${PROCESSOR_BLOB_CONTAINER}      # o mesmo B3_BLOB_CONTAINER do conector
  engine:
    url: ${PROCESSOR_ENGINE_URL}                # endereço do engine atrás do balanceador
    timeout-segundos: 150
  repeticao:
    gravacao-minutos: 5
    aviso-minutos: 10
    alerta-aviso-minutos: 2
spring:
  threads:
    virtual:
      enabled: true                            # listener e requisições em virtual threads
  kafka:
    consumer:
      enable-auto-commit: false
      max-poll-records: 1
      properties:
        max.poll.interval.ms: 1800000
    listener:
      ack-mode: manual
```

Verificar na configuração do Kafka do serviço (no poc, `adapter/common/json/config/KafkaConfig.java`) que a fábrica de listener usa `AckMode.MANUAL` e tem o tratador de erro ligado (`setCommonErrorHandler`); se não, ajustar só isso.

### 1.4 Modelo `application/model/AvisoCargaB3.java`

```java
public record AvisoCargaB3(String idCarga, String fonte, String produto, LocalDate dataBase,
                           Arquivo arquivo, String origem, String usuario, OffsetDateTime geradoEm) {
    public record Arquivo(String caminho, String sha256, long bytes) {}
}
```

Validação (classe `application/model/ValidadorAvisoCargaB3.java`, Java puro, método `static void validar(AvisoCargaB3 a)` que lança `FalhaDefinitiva("VALIDACAO", motivo)`): todas as regras da spec (campos obrigatórios; `fonte` `B3`; `produto` `TS`; `idCarga` casa `^B3-TS-\d{8}-[0-9a-f]{12}$` e a data do `idCarga` = `dataBase`; `caminho` = `b3/{AAAAMMDD}/cargas/{idCarga}/TaxaSwap.txt`; `sha256` casa `^[0-9a-f]{64}$`; `origem` em `DOWNLOAD`, `REPROCESSAMENTO`, `UPLOAD`). JSON inválido → a mesma exceção.

Falhas novas em `application/exception/` (ao lado de `BusinessException` e `InfrastructureException`, que continuam para o resto do serviço), fechadas por `sealed`:

```java
public abstract sealed class FalhaCarga extends RuntimeException permits FalhaDefinitiva, FalhaTransitoria {
  private final String etapa;                                   // LEITURA, VALIDACAO, GRAVACAO, AVISO
  protected FalhaCarga(String etapa, String mensagem, Throwable causa) { super(mensagem, causa); this.etapa = etapa; }
  public String etapa() { return etapa; }
}
public final class FalhaDefinitiva extends FalhaCarga { public FalhaDefinitiva(String etapa, String motivo) { super(etapa, motivo, null); } }
public final class FalhaTransitoria extends FalhaCarga { public FalhaTransitoria(String etapa, Throwable causa) { super(etapa, causa.getMessage(), causa); } }
```

Nas seções abaixo, `FalhaDefinitivaException` e `FalhaTransitoriaException` significam `FalhaDefinitiva` e `FalhaTransitoria`.

Portas de saída (`application/port/out/`), uma por dependência:

```java
public interface ArquivoCargaPort { byte[] ler(String caminho); }                                   // Blob
public interface B3CurvaPrimariaPort {                                                               // tBtrsCurvaPrimr
  Map<String, List<String>> curvasPorCodigo();
  void gravar(LocalDate dataBase, Map<String, List<String>> curvasPorCodigo, Map<String, List<B3CurvaPrimaria>> porCodigo);
}
public interface EngineCargaPort { RespostaEngine avisar(NotificacaoCarga corpo, String correlationId); }  // engine
public record NotificacaoCarga(String idCarga, String fonte, String produto, LocalDate dataBase, Map<String, Integer> verticesPorCodigo) {}
public record RespostaEngine(int status, String corpo) {}
```

### 1.5 Leitura do arquivo: `adapter/out/blob/ArquivoCargaBlobAdapter.java` (implementa `ArquivoCargaPort`)

`byte[] ler(String caminho)` com um único `BlobContainerClient` criado na subida (bean), com `BlobServiceClientBuilder().endpoint(endpoint).credential(new DefaultAzureCredentialBuilder().build())`. 404 → `FalhaDefinitivaException("LEITURA", "arquivo inexistente: " + caminho)`; outras falhas de rede/5xx/408/429 → `FalhaTransitoriaException("LEITURA", e)`. Depois de ler, no serviço: `bytes.length == arquivo.bytes` e `HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(arquivo.sha256)`; senão `FalhaDefinitivaException("LEITURA", "divergência de tamanho|hash")`.

### 1.6 Parse e validação: `application/model/LeiauteTaxaSwap.java` (Java puro)

```java
public record B3CurvaPrimaria(String codigo, int diasCorridos, int diasUteis, BigDecimal valor, int linha) {}
public record ResultadoParse(LocalDate dataBase, Map<String, List<B3CurvaPrimaria>> porCodigo, Map<String, String> invalidos /* código → "linha N: motivo" */) {}
public static ResultadoParse interpretar(byte[] arquivo, LocalDate dataBaseEsperada);
```

Regras exatas (posições 1-based → `substring(inicio-1, fim)`):
- `var linhas = new String(arquivo, StandardCharsets.ISO_8859_1).lines().filter(l -> !l.isBlank()).toList();` (`String.lines()` já trata `\r\n`, `\n` e `\r`).
- Arquivo sem linhas, linha com tamanho ≠ 72, data (12–19) diferente entre linhas ou de `dataBaseEsperada`, ou código (22–26, `strip()`) vazio → `FalhaDefinitivaException("VALIDACAO", "linha N: <motivo>")`.
- Por linha: `dc = 42–46`, `du = 47–51`, `sinal = 52`, `taxa = 53–66`. Código fica inválido (registrar em `invalidos`, parar de ler as linhas daquele código) se: `dc` ou `du` não são só dígitos; `dc < 1`, `du < 1` ou `du > dc`; `sinal` não é `+` nem `-`; `taxa` não é só dígitos; `dc` repetido no código.
- `valor = new BigDecimal(taxa).movePointLeft(7)` com o sinal (`negate()` se `-`); escala 7. Nunca `double`.

Casos de teste (escritos na seção 2): linha da spec `0049060010120260914T1DCL  CUPOM LIMPO - S0000100001-00001179600000F00001` → `DCL`, 1, 1, `-117.9600000`. Com `docs/TaxaSwap.txt`: 114 códigos, 278 vértices em `PRE`/`DCL`/`DPL`/`INP`/`PTX`, nenhum inválido; primeiro vértice da `PRE` = `13.9000000`.

### 1.7 Gravação: `adapter/out/persistence/jdbc/B3CurvaPrimariaJdbcAdapter.java` (implementa `B3CurvaPrimariaPort`)

Consultas com o `JdbcClient` do Spring (mapeia direto para records); inclusões em lote com `JdbcTemplate.batchUpdate`. Ambos já vêm com o `spring-boot-starter-data-jpa`.

Colunas conforme o `001_SCRIPT_INICIAL.sql`: `tBtrsCurvaPrimr(cIdtfdUnic int NOT NULL, cTickerIndcd varchar(50) NOT NULL, cDiaCorri int, cDiaUtil int, dBaseReft date, vFatorAcum decimal(28,16), vPrecoTx decimal(28,12), vFatorDia decimal(28,16))` e `tCurvaPrvdr(cIdtfdUnic, cPriorCsumo, cPrvdrMercd varchar(50), cTickerIndcd varchar(50), cTickerPrvdr varchar(1024), iPrvdrDados varchar(1024))`. `vPrecoTx` recebe o `BigDecimal` de escala 7 sem arredondar.

SQL exato:
```sql
-- mapeamento
SELECT cTickerPrvdr, cTickerIndcd FROM tCurvaPrvdr WHERE iPrvdrDados = 'B3' AND cPrvdrMercd = 'TS';
-- por curva, dentro da transação única, em ordem crescente do nome da curva
SELECT cTickerIndcd FROM tCurvaMercd WITH (UPDLOCK, ROWLOCK) WHERE cTickerIndcd = ?;   -- trava da curva, espera até 60 s
DELETE FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;
SELECT ISNULL(MAX(cIdtfdUnic), 0) FROM tBtrsCurvaPrimr WITH (UPDLOCK, HOLDLOCK);
INSERT INTO tBtrsCurvaPrimr (cIdtfdUnic, cTickerIndcd, dBaseReft, cDiaCorri, cDiaUtil, vPrecoTx, vFatorAcum, vFatorDia)
VALUES (?, ?, ?, ?, ?, ?, NULL, NULL);                 -- batchUpdate, ids = max+1, max+2, …
SELECT COUNT(*) FROM tBtrsCurvaPrimr WHERE cTickerIndcd = ? AND dBaseReft = ?;  -- tem de ser = vértices do código
```

- `gravar` é `@Transactional` (uma transação para a carga inteira). Contagem divergente → exceção que desfaz tudo (`FalhaDefinitivaException("GRAVACAO", …)`).
- Trava da curva: antes de apagar e inserir, travar a linha de cada curva mapeada em `tCurvaMercd` com `UPDLOCK, ROWLOCK`, em ordem crescente do nome da curva (evita deadlock entre cargas), com tempo limite de 60 s no próprio comando (dica de consulta `jakarta.persistence.query.timeout`=60000, ou `JdbcTemplate.setQueryTimeout(60)`; não usar `SET LOCK_TIMEOUT`). É a mesma trava da edição manual no curves e da construção no engine. Tempo esgotado → falha transitória.
- Geração de `cIdtfdUnic`: `MAX + 1` lido com `UPDLOCK, HOLDLOCK` na mesma transação da gravação, como o serviço de cadastro faz na edição manual; isolar num método `proximoId()`, com o mesmo tempo limite de 60 s.
- Nunca escrever em `tCurvaMercd` nem `tCurvaPrvdr`.
- Deadlock, timeout de comando ou perda de conexão → `FalhaTransitoriaException("GRAVACAO", e)`; violação de FK/chave → `FalhaDefinitivaException("GRAVACAO", …)`.

### 1.8 Aviso ao engine: `adapter/out/client/EngineCargaClient.java` (implementa `EngineCargaPort`)

`RespostaEngine avisar(NotificacaoCarga corpo, String correlationId)` com `RestClient` (um só, criado na subida), sem autenticação:
- `POST {processor.engine.url}/api/v1/cargas`, JSON `{ idCarga, fonte:"B3", produto:"TS", dataBase:"AAAA-MM-DD", verticesPorCodigo:{ código: quantidade } }`;
- cabeçalho `X-Correlation-Id`, e nenhum `Authorization` (o engine não exige autenticação; o `DefaultAzureCredential` é só do Blob);
- tempo limite de leitura `timeout-segundos`;
- 2xx → sucesso (guardar o corpo para o log); rede, timeout, 409, 429, 5xx → `FalhaTransitoriaException("AVISO", e)`; outro 4xx → `FalhaDefinitivaException("AVISO", corpo da resposta)`.

`verticesPorCodigo` = só os códigos gravados em pelo menos uma curva. Se vazio, não chamar o engine e registrar no log.

### 1.9 Orquestração: `application/service/ProcessarCargaB3Service.java` e o consumidor

```java
public void processar(String mensagem, String correlationId);
```
1. `AvisoCargaB3 aviso` (Jackson) + `ValidadorAvisoCargaB3.validar`;
2. `repetir("LEITURA", gravacaoMinutos, () -> blob.ler(caminho))` + conferência de tamanho e hash;
3. `LeiauteTaxaSwap.interpretar(bytes, aviso.dataBase())`;
4. `repetir("GRAVACAO", gravacaoMinutos, () -> jdbc.gravar(...))` (códigos sem curva: ignorados e contados);
5. `repetir("AVISO", avisoMinutos, () -> engine.avisar(...))`, com `AVISO_ATRASADO` (log `ERRO` + métrica) se passar de `alertaAvisoMinutos` desde o commit;
6. log JSON da carga (campos da spec) e métricas.

`repetir(etapa, minutos, acao)`: repete só em `FalhaTransitoria`, esperando 1 s, 2 s, 4 s… até 60 s, com ±10% aleatório, até a janela acabar; cada nova tentativa gera log `AVISO`. Janela esgotada → `FalhaDefinitiva(etapa, "janela esgotada")`. Só Java:

```java
static <T> T repetir(String etapa, Duration janela, Supplier<T> acao) {
  var fim = System.nanoTime() + janela.toNanos();
  var espera = Duration.ofSeconds(1);
  for (int tentativa = 1; ; tentativa++) {
    try { return acao.get(); }
    catch (FalhaTransitoria f) {
      if (System.nanoTime() + espera.toNanos() > fim) throw new FalhaDefinitiva(etapa, "janela esgotada após " + tentativa + " tentativas");
      log.warn(...);                                                  // AVISO com etapa, tentativa e causa
      long ms = espera.toMillis();
      sleep(Duration.ofMillis(ms + ThreadLocalRandom.current().nextLong(-ms / 10, ms / 10 + 1)));
      espera = espera.multipliedBy(2).compareTo(Duration.ofSeconds(60)) > 0 ? Duration.ofSeconds(60) : espera.multipliedBy(2);
    }
  }
}
// sleep: Thread.sleep(Duration); InterruptedException → reinterrompe a thread e lança FalhaDefinitiva(etapa, "interrompido")
```

O tratamento final usa `switch` com pattern matching sobre a falha (`case FalhaDefinitiva d -> ...`), e o `sealed` garante que não sobra caso.

Qualquer `FalhaDefinitivaException` → log `CARGA_FALHOU` (nível `ERRO`, campos: `idCarga`, `dataBase`, `motivo`, `etapa`, `estado` = `GRAVADA_SEM_AVISO` se a etapa for `AVISO`, senão `NAO_GRAVADA`, `tentativas`, instante de Brasília) + métrica, e **retornar normalmente** (a mensagem é confirmada).

`B3KafkaConsumer`: mantém o `@KafkaListener` que já existe, com o tópico pela propriedade (`topics = "${spring.kafka.topics.b3.name}"`; no poc o valor é `tp-event-b3-curve`), o `groupId` e a fábrica que o serviço já usa; recebe `String message`, o cabeçalho `X-Correlation-Id` (`@Header(name = "X-Correlation-Id", required = false) byte[]`, na falta `UUID.randomUUID()`) e `Acknowledgment`; chama `processar` e depois `ack.acknowledge()` sempre, a não ser que `processar` lance exceção inesperada (aí não confirma e relança).

Métricas (Micrometer): `processor_b3_carga_total{resultado}` (`SUCESSO`, `SUCESSO_COM_CODIGOS_INVALIDOS`, `FALHOU`), `processor_b3_carga_falhou_total{etapa,estado}`, `processor_b3_aviso_atrasado_total`, `processor_b3_carga_duracao_segundos`.

Casos de teste (escritos na seção 2; JUnit + Mockito, sem banco real; o que só o banco prova fica para a homologação, seção 3): um por cenário da spec `b3-carga-processor`, incluindo mensagem malformada, hash divergente, linha de 60 caracteres, `DPL` com taxa `0000ABC1859000`, 114 códigos com 5 mapeados (278 linhas por curva, 109 ignorados), trava da curva em `tCurvaMercd` enviada antes do `DELETE` e em ordem do nome, tempo esgotado na trava como falha transitória, `SLP` sem mapeamento, `PRE` ligado a duas curvas, engine fora 3 min (`AVISO_ATRASADO`), 409 seguido de 200, janela de aviso esgotada (`GRAVADA_SEM_AVISO`), mensagem duplicada, banco fora 5 min (`NAO_GRAVADA`), e nada gravado em `mkt.B3CurveRaw`.

---

## 2. Testes (ao final da implementação)

Só depois de todo o código da seção 1 estar pronto e compilando. Os casos de teste citados na seção 1 são a lista do que cobrir; aqui está a ordem de trabalho. Ordem fixa: verificar, adaptar, criar, rodar.

### 2.1 Verificar

1. Rodar a suíte como está: `mvn test` em `services/processor`.
2. Anotar cada teste que falha e o motivo: import de arquivo removido, nome mudado, assinatura mudada ou regra mudada.
3. Buscar referências que sobraram, que não podem existir no fim: `B3CurveRaw`, `B3JpaRepository`, `B3CurveRepositoryPort`, `ProcessB3CurveUseCase`, `ProcessB3CurveService`, `mkt.B3CurveRaw`.

### 2.2 Adaptar os testes que continuam

Processor (`services/processor/src/test/java/br/com/poc/starter/srv/hex/`):

| Arquivo | Ação |
|---|---|
| `adapter/in/consumer/kafka/B3KafkaConsumerTest.java` | reescrever para o consumidor novo: chama `processar` com a mensagem e o `X-Correlation-Id`, confirma com `acknowledge()`, gera correlação quando o cabeçalho falta, não confirma em exceção inesperada |
| `adapter/common/json/config/KafkaConfigTest.java` | verificar; acrescentar o caso do `AckMode.MANUAL` se faltar |
| `adapter/out/persistence/inmemory/adapter/B3CurveRepositoryAdapterTest.java`, `.../entity/B3CurveRawEntityTest.java`, `application/service/ProcessB3CurveServiceTest.java` | remover |
| testes das outras fontes (Anbima, Bloomberg, CME, LCH, Treasury) e os demais | não mexer; têm de continuar passando |

### 2.3 Criar os testes novos

Processor (mesmo pacote base, em `src/test/java`):
- `application/service/ValidadorAvisoCargaB3Test.java`: um caso por regra de 1.4;
- `application/service/LeiauteTaxaSwapTest.java`: linha da spec, `docs/TaxaSwap.txt` completo e um caso por regra de linha inválida de 1.6;
- `adapter/out/blob/ArquivoCargaBlobAdapterTest.java`: 404 definitivo, 503 transitório;
- `adapter/out/persistence/jdbc/B3CurvaPrimariaJdbcAdapterTest.java` (`JdbcTemplate` simulado): SQL e parâmetros enviados, ids `max+1, max+2...`, contagem divergente lança a falha que desfaz a transação;
- `adapter/out/client/EngineCargaClientTest.java` (servidor HTTP simulado): 2xx, 409/429/5xx transitórios, 400 definitivo, cabeçalhos;
- `application/service/ProcessarCargaB3ServiceTest.java`: cenários da spec `b3-carga-processor` listados em 1.9, com as janelas reduzidas por configuração.

### 2.4 Rodar e fechar

1. `mvn verify` no processor: tudo passa, inclusive os testes das outras fontes.
2. As buscas de 2.1 item 3 não encontram nada.
3. Se algo falhar, corrigir o código (não o teste) quando o teste reflete a spec; só mudar o teste quando ele contradiz a spec.

## 3. Verificação ponta a ponta (na homologação)

No ambiente de homologação, com Kafka, Blob e SQL Server do projeto, **e o conector deste mesmo release já implantado** (change `conector-b3-webhook-ingest`, que publica o aviso e expõe `b3/taxa-swap/reprocessamento`). É aqui que se confere o que os testes simulados não provam: a transação no banco, `cIdtfdUnic` sem colisão em duas cargas simultâneas e a troca de linhas numa carga nova da mesma data:
1. cadastrar em `tCurvaMercd` e `tCurvaPrvdr` as curvas `DIxPRE`, `Cupom limpo de dólar`, `Cupom Limpo DI X IPCA`, `IBOVESPA`, `PTAX - USD` ligadas a `B3`/`TS`/`PRE`, `DCL`, `DPL`, `INP`, `PTX`;
2. colocar `docs/TaxaSwap.txt` em `recebidos/TaxaSwap.txt` e chamar `POST /api/b3/taxa-swap/reprocessamento` (do conector) sem data → `idCarga` `B3-TS-20260914-46a249c60bec`, publicado no tópico e consumido pelo processor;
3. conferir 278 linhas por curva em `tBtrsCurvaPrimr` com `dBaseReft` = `2026-09-14`, primeiro `vPrecoTx` da `DIxPRE` = `13.9000000`;
4. chamar de novo com `dataBase=2026-09-14` → mesmo `idCarga`, mesmas linhas, aviso repetido ao engine (simulado);
5. obter o mesmo conteúdo pelo download do `.ex_` (B3 simulada) e pelo upload → mesmo `idCarga`, mesmas linhas, três avisos idênticos ao engine simulado;
6. `openspec validate processor-carga-b3 --strict`.
