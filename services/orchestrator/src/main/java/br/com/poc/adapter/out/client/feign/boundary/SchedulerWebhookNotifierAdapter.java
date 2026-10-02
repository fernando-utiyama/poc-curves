package br.com.poc.adapter.out.client.feign.boundary;

import br.com.poc.adapter.out.client.feign.SchedulerWebhookClient;
import br.com.poc.adapter.out.client.feign.dto.SchedulerWebhookRequest;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.port.out.WebhookNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter responsável por notificar um webhook externo sobre alterações no ciclo
 * de vida das tarefas. Usa `SchedulerWebhookClient` para realizar a chamada HTTP
 * quando o webhook estiver habilitado via propriedade `scheduler.webhook.enabled`.
 * Aceita uma lista de URLs e paths separados por virgula.
 */
@Component
public class SchedulerWebhookNotifierAdapter implements WebhookNotifierPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerWebhookNotifierAdapter.class);

    private final SchedulerWebhookClient schedulerWebhookClient;
    private final boolean enabled;
    private final List<String> destinationUrls;

    public SchedulerWebhookNotifierAdapter(
        SchedulerWebhookClient schedulerWebhookClient,
        //TODO ALTERAR PARA TRUE
        @Value("${scheduler.webhook.enabled:false}") boolean enabled,
        @Value("${scheduler.webhook.url:}") String webhookUrls,
        @Value("${scheduler.webhook.path:/}") String webhookPaths) {
        this.schedulerWebhookClient = schedulerWebhookClient;
        this.enabled = enabled;
        this.destinationUrls = resolveDestinationUrls(webhookUrls, webhookPaths);
    }

    @Override
    public void notifyScheduleChanged(String eventType, Tarefa tarefa) {
        if (!enabled) {
            LOGGER.debug("Webhook desabilitado para evento {} da tarefa {}", eventType, tarefa.getId());
            return;
        }

        if (destinationUrls.isEmpty()) {
            LOGGER.warn("Webhook habilitado, mas scheduler.webhook.url nao foi configurada corretamente. Evento {} da tarefa {} nao sera enviado", eventType, tarefa.getId());
            return;
        }

        SchedulerWebhookRequest request = new SchedulerWebhookRequest(eventType, LocalDateTime.now(), tarefa);

        for (String destinationUrl : destinationUrls) {
            try {
                schedulerWebhookClient.notify(destinationUrl, request);
                LOGGER.info("Webhook enviado para evento {} da tarefa {} em {}", eventType, tarefa.getId(), destinationUrl);
            } catch (RuntimeException ex) {
                LOGGER.warn("Falha ao enviar webhook para evento {} da tarefa {} em {}", eventType, tarefa.getId(), destinationUrl, ex);
            }
        }
    }

    private List<String> resolveDestinationUrls(String webhookUrls, String webhookPaths) {
        List<String> urls = splitCsv(webhookUrls);
        if (urls.isEmpty()) {
            return List.of();
        }

        List<String> paths = splitCsv(webhookPaths);
        if (paths.isEmpty()) {
            paths = List.of("/");
        }

        if (paths.size() != 1 && paths.size() != urls.size()) {
            LOGGER.warn("Configuracao invalida de scheduler.webhook.path: esperado 1 path ou {} paths, recebido {}", urls.size(), paths.size());
            return List.of();
        }

        List<String> resolvedUrls = new ArrayList<>(urls.size());
        for (int index = 0; index < urls.size(); index++) {
            String path = paths.size() == 1 ? paths.getFirst() : paths.get(index);
            resolvedUrls.add(joinUrl(urls.get(index), path));
        }
        return List.copyOf(resolvedUrls);
    }

    private List<String> splitCsv(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }

        String[] parts = rawValue.split(",");
        List<String> values = new ArrayList<>(parts.length);
        for (String part : parts) {
            String value = part.trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String joinUrl(String baseUrl, String path) {
        String sanitizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        String sanitizedPath = path == null || path.isBlank() ? "/" : path.trim();

        if (sanitizedBaseUrl.isBlank()) {
            return sanitizedBaseUrl;
        }

        boolean baseEndsWithSlash = sanitizedBaseUrl.endsWith("/");
        boolean pathStartsWithSlash = sanitizedPath.startsWith("/");

        if (baseEndsWithSlash && pathStartsWithSlash) {
            return sanitizedBaseUrl + sanitizedPath.substring(1);
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return sanitizedBaseUrl + "/" + sanitizedPath;
        }
        return sanitizedBaseUrl + sanitizedPath;
    }
}
