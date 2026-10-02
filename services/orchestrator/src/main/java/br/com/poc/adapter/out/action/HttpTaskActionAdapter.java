package br.com.poc.adapter.out.action;

import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.exception.ServiceUnavailableException;
import br.com.poc.application.model.scheduler.ParametroTarefa;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.port.out.TaskActionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter que implementa `TaskActionPort` para executar chamadas HTTP como ações de
 * tarefa.
 *
 * Convenção de parâmetros:
 * - `url`: URL a ser chamada (obrigatório).
 * - `method`: método HTTP (opcional, default GET).
 * - `body`: payload da requisição (opcional).
 * - `contentType`: content-type do payload (opcional).
 * - `header.<nome>`: cabeçalhos adicionais (ex: `header.Authorization`).
 *
 * O adapter converte a lista de `ParametroTarefa` em um mapa normalizado e realiza a
 * chamada usando `RestClient`. Em caso de status HTTP não 2xx lança `ServiceUnavailableException`.
 */
@Component
public class HttpTaskActionAdapter implements TaskActionPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTaskActionAdapter.class);

    private final RestClient restClient;

    public HttpTaskActionAdapter() { this(RestClient.builder().build()); }

    public HttpTaskActionAdapter(RestClient restClient) { this.restClient = restClient; }

    @Override
    public String getActionName() { return "http"; }

    @Override
    public List<String> getSupportedActionNames() { return List.of("http", "httpaction"); }

    @Override
    public void execute(Tarefa tarefa) {
        Map<String, String> parametros = toParameterMap(tarefa);
        String url = parametros.get("url");
        if (url == null || url.isBlank()) {
            throw new InvalidInputException("HTTP_ACTION", "execute", "Parametro 'url' e obrigatorio para action http");
        }

        String body = parametros.get("body");
        String contentType = parametros.get("contenttype");

        try {
            HttpMethod method = HttpMethod.valueOf(parametros.getOrDefault("method", "GET").toUpperCase(Locale.ROOT));
            RestClient.RequestBodySpec request = restClient.method(method).uri(url);

            request.headers(headers -> parametros.forEach((nome, valor) -> {
                if (nome.startsWith("header.")) {
                    headers.add(nome.substring("header.".length()), valor);
                }
            }));

            if (contentType != null && !contentType.isBlank()) {
                request.contentType(MediaType.parseMediaType(contentType));
            }

            ResponseEntity<String> response = body == null || body.isBlank()
                ? request.retrieve().toEntity(String.class)
                : request.body(body).retrieve().toEntity(String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new ServiceUnavailableException("TASK_ACTION_HTTP",
                    "Falha ao executar chamada HTTP da tarefa " + tarefa.getNome());
            }

            LOGGER.info("HttpAction [{}] retornou status {} para {}", tarefa.getNome(), response.getStatusCode().value(), url);
            LOGGER.debug("HttpAction [{}] response body: {}", tarefa.getNome(), safeBody(response.getBody()));
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException("HTTP_ACTION", "execute",
                "Metodo HTTP invalido para tarefa " + tarefa.getNome(), ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("TASK_ACTION_HTTP",
                "Falha ao executar chamada HTTP da tarefa " + tarefa.getNome(), ex);
        }
    }

    private Map<String, String> toParameterMap(Tarefa tarefa) {
        Map<String, String> parametros = new LinkedHashMap<>();
        if (tarefa == null || tarefa.getParametros() == null) {
            return parametros;
        }

        for (ParametroTarefa parametro : tarefa.getParametros()) {
            if (parametro == null || parametro.getNome() == null || parametro.getNome().isBlank()) {
                continue;
            }

            String nomeNormalizado = parametro.getNome().trim().toLowerCase(Locale.ROOT).replace(':', '.');
            parametros.put(nomeNormalizado, parametro.getValor());
        }
        return parametros;
    }

    private String safeBody(String body) { return body == null ? "" : body; }
}
