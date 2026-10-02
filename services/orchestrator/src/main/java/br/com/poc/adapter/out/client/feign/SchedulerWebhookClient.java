package br.com.poc.adapter.out.client.feign;

import br.com.poc.adapter.out.client.feign.dto.SchedulerWebhookRequest;

/**
 * Cliente de webhook com destino dinamico.
 *
 * A URL final eh resolvida em tempo de execucao para permitir o disparo para
 * multiplos endpoints configurados no orquestrador.
 */
public interface SchedulerWebhookClient {

    void notify(String url, SchedulerWebhookRequest request);
}
