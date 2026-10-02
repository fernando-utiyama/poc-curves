# Guia de implementação: orquestrador v0, disparo manual

A spec manda; este guia diz onde e como. Pacotes, exceções, handler de erro e log seguem o repositório real. Os nomes novos são sugestão. Ordem: seções 1 a 4b (base), **pausa** (seção 4c), seções 5 a 6b; testes no fim.

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

## 4b. Calendários (tarefa 1.5)

Copiar `Calendario`, `Brazil`, `UnitedStates` e `CalendarioPorLista` da seção 4 do guia do engine para `domain/calendario/`, sem mudar nada. Um `CalendariosRegistry` (bean) resolve `nome/mercado` para a instância (`Brazil/Settlement`, `UnitedStates/FederalReserve`); outro valor = `PARAMETRO_INVALIDO`. A sincronização de feriados com o engine é da v1.

Data-base padrão (na `action`):

```java
LocalDate dataBasePadrao(LocalDate hoje, int defasagem, List<Calendario> calendarios) {
    LocalDate d = hoje;
    for (int restantes = defasagem; restantes > 0; ) {
        d = d.minusDays(1);
        LocalDate x = d;
        if (calendarios.stream().allMatch(c -> c.isBusinessDay(x))) restantes--;
    }
    return d;
}
```

`calendarios` ausente = `[Brazil/Settlement]`; `defasagemDiasUteis` ausente = 0; fora de 0 a 10 = `PARAMETRO_INVALIDO`.

## 4c. PAUSA depois da base (tarefa 1.6)

Concluídas as seções 1 a 4b (tarefas 1.1 a 1.5), **parar**. Rodar `mvn compile` e os testes das tarefas 1.x, registrar o que foi feito e o que ficou pendente, e **aguardar a revisão** antes de começar a seção 5. Não adiantar nada das tarefas 2.x.

## 5. Actions de carga (tarefa 2.1)

`CargaFonteTaskActionAdapter implements TaskActionPort`, `getActionName()` = `carga-download-site` e `getSupportedActionNames()` = `["carga-download-site", "carga-data-license"]`; em `carga-data-license`, `tickers` é obrigatório. Como a `action` precisa receber a data-base e devolver um resultado, criar uma porta própria para ela, sem mexer na interface das outras actions:

```java
public interface ExecucaoManualActionPort {           // implementada só pelas actions de carga na v0
    String getActionName();
    ResultadoExecucao executar(Tarefa tarefa, LocalDate dataBase, boolean incluirDownload, String correlationId);
}

public record ResultadoExecucao(Resultado resultado, String fonte, LocalDate dataBase, boolean incluirDownload, String idCarga,
                                Integer statusHttp, String detalhe) {}
public enum Resultado { SUCESSO, NAO_RECEBIDA, NAO_IMPLEMENTADA, ERRO }
```

Passos:
1. Ler `fonte`, `destino`, `caminhoDownload`, `caminhoReprocessamento`, `tickers`, `defasagemDiasUteis` e `calendarios` (`inicioHorario` e `limiteHorario` são aceitos e ignorados).
2. `hoje = LocalDate.now()`; `padrao = dataBasePadrao(hoje, defasagem, calendarios)`; `dataBase` nula → `padrao`; `dataBase.isAfter(hoje)` → 400; `!dataBase.isBefore(padrao)` → `caminhoDownload`; senão, `incluirDownload` → `caminhoDownload`; senão `caminhoReprocessamento` ou, ausente, `caminhoDownload`.
3. Em `carga-data-license`, `tickers` ausente ou vazio → `ERRO` com detalhe "parâmetro tickers ausente", sem chamada. Trocar `{dataBase}` por `dataBase.toString()`; se o caminho tem `{tickers}`: sem o parâmetro → o mesmo `ERRO`, sem chamada; com ele → `URLEncoder.encode(tickers, UTF_8)` com `+` trocado por `%20`.
4. `ChamadaSaidaClient.get(destino, caminho, correlationId)`; `ResourceAccessException` (rede ou tempo) → `NAO_RECEBIDA`.
5. Classificar pela tabela da spec. No 200, ler `dataBase` e `idCarga` do corpo JSON; corpo ilegível → `ERRO`.
6. `detalhe`: `codigoErro` e `mensagem` do corpo de erro, se houver, cortados em 500 caracteres; nunca o corpo inteiro.

## 6. Rota de execução manual (tarefa 2.2)

- `SchedulerAPI.executar`: `@PostMapping("/tarefas/{id}/executar")` com `@RequestParam(required = false) LocalDate dataBase`, `@RequestParam(defaultValue = "false") boolean incluirDownload`, `@RequestHeader(name = "X-Usuario", required = false) String usuario` e `@RequestHeader(name = "X-Correlation-Id", required = false) String correlationId` (ausente: `UUID.randomUUID()`).
- `SchedulerService.executeTask(id, dataBase, incluirDownload, usuario, correlationId)`:
  1. `dataBase` nula → a `action` usa a data-base padrão (seção 4b) e a devolve no resultado;
  2. reivindicar (seção 4);
  3. fora de transação: se a `action` da tarefa tem `ExecucaoManualActionPort`, chamar `executar(...)`; senão, o caminho antigo (`TaskActionExecutor.execute`), com resultado `SUCESSO` ou `ERRO`;
  4. gravar o log `200` (`SUCESSO`) ou `500` (demais) com o JSON da spec;
  5. devolver à situação de origem (seção 4), num `finally`;
  6. responder 200 com o JSON.
- Remover deste caminho o `runningTasks` e o `prepararNovoCicloParaExecucaoManual`; o resto do `SchedulerService` fica como está até a v1.

## 6b. bff e front (tarefas 2.3 e 2.4)

- bff: `POST /api/v1/tarefas/{id}/executar?dataBase=&incluirDownload=` (opcionais), segurança e perfil como as outras ações de operação do bff; repassa ao orquestrador `POST /api/v1/agendador/tarefas/{id}/executar` com a mesma `dataBase` (ou sem ela) e o `incluirDownload`, `X-Usuario` = usuário do token, `X-Correlation-Id`, sem `Authorization`; tempo limite de 150 s (acima dos 120 s do orquestrador para a function).
- Front: na lista de tarefas, botão "Executar" abre um campo "Data-base" (`dd/mm/aaaa`, opcional, com a dica "Vazio: data-base padrão da tarefa"), a caixa "Baixar de novo da fonte" (desmarcada; habilitada só com data informada) e "Confirmar"; enquanto espera, o botão fica desabilitado; o resultado mostra a situação em pt-BR (`SUCESSO` → "Sucesso", `NAO_RECEBIDA` → "Arquivo ainda não recebido", `NAO_IMPLEMENTADA` → "Fonte ainda não implementada", `ERRO` → "Erro"), a data-base usada em `dd/mm/aaaa`, o identificador da carga e o detalhe.

## 7. Testes (ao final)

Um por cenário da spec: `ChamadaSaidaClientTest` (servidor simulado com `MockRestServiceServer` ou `MockWebServer`), `CargaFonteTaskActionAdapterTest` (as quatro classificações, data-base padrão e data passada com e sem `incluirDownload`, três fontes, as duas `actions`, `tickers` com espaço codificado como `%20`, `carga-data-license` sem `tickers`), `DataBasePadraoTest` (defasagem 0 em sábado = sábado; 1 na segunda = sexta; 1 depois de feriado nacional; 1 na Bloomberg com feriado só americano; defasagem 11 = erro), `CalendarioTest` (mesmos dias úteis do engine), `ReivindicacaoTarefaTest` (repositório simulado: 409, 400, origem devolvida), `SchedulerControllerTest` (MockMvc: `dataBase` e `incluirDownload` opcionais, data futura 400, resposta com o JSON), `FusoTest` (sobe com UTC no sistema e usa Brasília). Rodar a suíte inteira e ver que os testes existentes continuam passando, ajustando os que dependiam de `url` na action `http`.
