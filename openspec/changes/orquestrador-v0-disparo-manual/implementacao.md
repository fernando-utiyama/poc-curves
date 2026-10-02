# Guia de implementação: orquestrador v0, disparo manual

A spec manda; este guia diz onde e como. Pacotes, exceções, handler de erro e log seguem o repositório real. Os nomes novos são sugestão. Ordem: seções 1 a 6; testes no fim.

## 1. Fuso (tarefa 1.1)

- `Application.main`, primeira linha: `TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))`; logo depois, se `ZoneId.systemDefault()` não for `America/Sao_Paulo`, lançar exceção e não subir.
- `application.yml`: remover `spring.jackson.time-zone: UTC`. Não mexer no resto do arquivo.
- Remover `APP_ZONE` de `SpringSchedulerAdapter` e `UTC_3`/`APP_ZONE` de `TarefaCrudService`; usar `LocalDateTime.now()` e `ZoneId.systemDefault()`.

## 2. Mapper (tarefa 1.2)

`TarefaJpaMapper`: `action` ↔ `cAcaoOperSist`, `descricao` ↔ `rTrefa`, nos dois sentidos.

## 3. Destinos e cliente de saída (tarefa 1.3)

```yaml
orquestrador:
  http:
    destinos:
      conector-b3:        { base-url: ${DESTINO_CONECTOR_B3_URL},        timeout-segundos: 120 }
      conector-anbima:    { base-url: ${DESTINO_CONECTOR_ANBIMA_URL},    timeout-segundos: 120 }
      conector-bloomberg: { base-url: ${DESTINO_CONECTOR_BLOOMBERG_URL}, timeout-segundos: 120 }
      engine:             { base-url: ${DESTINO_ENGINE_URL},             timeout-segundos: 30 }   # usado pela v1
```

Na v0, as três URLs `DESTINO_CONECTOR_*` são a base-URL do `services/processor`.

- `DestinosProperties` (`@ConfigurationProperties("orquestrador.http")`, record com `Map<String, Destino>`; `Destino(URI baseUrl, int timeoutSegundos)`).
- `ChamadaSaidaClient` (adapter out): um `RestClient` por destino, criado na subida com o tempo limite do destino (`JdkClientHttpRequestFactory` com `setReadTimeout`); método `ResponseEntity<String> get(String destino, String caminho, String correlationId)`.
  - destino ausente do mapa → `DESTINO_NAO_CADASTRADO`, sem chamada;
  - caminho que não começa com `/`, ou contém `://`, `//`, `..`, `\`, ou `@` → `CAMINHO_INVALIDO`, sem chamada;
  - só `X-Correlation-Id`; nenhum outro cabeçalho; nunca `Authorization`;
  - não lança exceção por status HTTP (`.onStatus(s -> true, (req, res) -> {})`): quem classifica é a `action`.
- `HttpTaskActionAdapter`: parâmetros `destino`, `caminho` e `method` (opcional, padrão `GET`); remover `url`, `header.*`, `body` e `contentType`; chamar pelo `ChamadaSaidaClient` (que ganha um método genérico por `HttpMethod`); não 2xx continua sendo falha.

## 4. Reivindicação (tarefa 1.4)

No `TaskRepositoryPort`/`TaskJpaPersistenceAdapter`, dois métodos com SQL nativo:

```sql
-- reivindicar (transação curta, @Transactional(propagation = REQUIRES_NEW))
SELECT cSit FROM tTrefaAgnda WHERE cIdtfdTrefa = ?;                                    -- situação de origem (trim)
UPDATE tTrefaAgnda SET cSit = 'EXECUTANDO'
 WHERE cIdtfdTrefa = ? AND cSit = ?;                                                   -- ? = origem lida; 0 linhas = alguém passou na frente → 409
INSERT INTO tLogTrefa (cIdtfdTrefa, dAtaCriac, cSitExcuc, rLogTrefa) VALUES (?, ?, 102, ?);   -- JSON {origem, dataBase, usuario, instancia, correlationId}

-- devolver (outra transação curta)
UPDATE tTrefaAgnda SET cSit = ? WHERE cIdtfdTrefa = ? AND cSit = 'EXECUTANDO';         -- ? = origem
```

- Origem `EXECUTANDO` → 409 sem tentar o `UPDATE`; `DESABILITADA` ou `REMOVIDA` → 400.
- Comparar `cSit` depois de `trim` (a coluna pode ser `CHAR`); no `UPDATE`, usar `RTRIM(cSit) = ?`.
- `instancia` = `HOSTNAME` (ou `InetAddress.getLocalHost().getHostName()`).
- As colunas reais de `tLogTrefa` seguem a entidade `LogTarefaEntity`; se a gravação for por JPA, manter as mesmas colunas.

## 5. Action de download (tarefa 2.1)

`DownloadCargaTaskActionAdapter implements TaskActionPort`, `getActionName()` = `download-carga-dia`. Como a `action` precisa receber a data-base e devolver um resultado, criar uma porta própria para ela, sem mexer na interface das outras actions:

```java
public interface ExecucaoManualActionPort {           // implementada só pela action de download na v0
    String getActionName();
    ResultadoExecucao executar(Tarefa tarefa, LocalDate dataBase, String correlationId);
}

public record ResultadoExecucao(Resultado resultado, String fonte, LocalDate dataBase, String idCarga,
                                Integer statusHttp, String detalhe) {}
public enum Resultado { SUCESSO, NAO_RECEBIDA, NAO_IMPLEMENTADA, ERRO }
```

Passos:
1. Ler `fonte`, `destino`, `caminhoDownload`, `caminhoReprocessamento`, `tickers` (os demais são aceitos e ignorados).
2. `hoje = LocalDate.now()`; `dataBase.isAfter(hoje)` → 400; `dataBase.equals(hoje)` → `caminhoDownload`; senão `caminhoReprocessamento` ou, ausente, `caminhoDownload`.
3. Trocar `{dataBase}` por `dataBase.toString()`; se o caminho tem `{tickers}`: sem o parâmetro → `ERRO` com detalhe "parâmetro tickers ausente", sem chamada; com ele → `URLEncoder.encode(tickers, UTF_8)` com `+` trocado por `%20`.
4. `ChamadaSaidaClient.get(destino, caminho, correlationId)`; `ResourceAccessException` (rede ou tempo) → `NAO_RECEBIDA`.
5. Classificar pela tabela da spec. No 200, ler `dataBase` e `idCarga` do corpo JSON; corpo ilegível → `ERRO`.
6. `detalhe`: `codigoErro` e `mensagem` do corpo de erro, se houver, cortados em 500 caracteres; nunca o corpo inteiro.

## 6. Rota de execução manual (tarefa 2.2)

- `SchedulerAPI.executar`: `@PostMapping("/tarefas/{id}/executar")` com `@RequestParam(required = false) LocalDate dataBase`, `@RequestHeader(name = "X-Usuario", required = false) String usuario` e `@RequestHeader(name = "X-Correlation-Id", required = false) String correlationId` (ausente: `UUID.randomUUID()`).
- `SchedulerService.executeTask(id, dataBase, usuario, correlationId)`:
  1. `dataBase` nula → hoje;
  2. reivindicar (seção 4);
  3. fora de transação: se a `action` da tarefa tem `ExecucaoManualActionPort`, chamar `executar(...)`; senão, o caminho antigo (`TaskActionExecutor.execute`), com resultado `SUCESSO` ou `ERRO`;
  4. gravar o log `200` (`SUCESSO`) ou `500` (demais) com o JSON da spec;
  5. devolver à situação de origem (seção 4), num `finally`;
  6. responder 200 com o JSON.
- Remover deste caminho o `runningTasks` e o `prepararNovoCicloParaExecucaoManual`; o resto do `SchedulerService` fica como está até a v1.

## 7. Testes (ao final)

Um por cenário da spec: `ChamadaSaidaClientTest` (servidor simulado com `MockRestServiceServer` ou `MockWebServer`), `DownloadCargaTaskActionAdapterTest` (as quatro classificações, hoje e data passada, três fontes, `tickers` com espaço codificado como `%20`, sem `tickers`), `ReivindicacaoTarefaTest` (repositório simulado: 409, 400, origem devolvida), `SchedulerControllerTest` (MockMvc: `dataBase` opcional, data futura 400, resposta com o JSON), `FusoTest` (sobe com UTC no sistema e usa Brasília). Rodar a suíte inteira e ver que os testes existentes continuam passando, ajustando os que dependiam de `url` na action `http`.
