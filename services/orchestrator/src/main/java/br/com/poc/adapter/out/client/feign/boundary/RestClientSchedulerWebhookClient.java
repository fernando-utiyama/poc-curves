package br.com.poc.adapter.out.client.feign.boundary;

import br.com.poc.adapter.out.client.feign.SchedulerWebhookClient;
import br.com.poc.adapter.out.client.feign.dto.SchedulerWebhookRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static java.util.Objects.nonNull;

@Component
public class RestClientSchedulerWebhookClient implements SchedulerWebhookClient {

    private final RestClient restClient;

    public RestClientSchedulerWebhookClient() { this(RestClient.builder().build()); }

    public RestClientSchedulerWebhookClient(RestClient restClient) { this.restClient = restClient; }

    @Override
    public void notify(String url, SchedulerWebhookRequest request) {
        restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> propagateAuthorizationHeader(headers))
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }

    private void propagateAuthorizationHeader(HttpHeaders headers) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }

        String headerValue = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (nonNull(headerValue) && !headerValue.isBlank()) {
            headers.add(HttpHeaders.AUTHORIZATION, headerValue);
        }
    }
}
