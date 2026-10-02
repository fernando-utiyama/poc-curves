package br.com.poc.adapter.out.client.feign.dto;

import br.com.poc.application.model.scheduler.Tarefa;

import java.time.LocalDateTime;

/**
 * Payload enviado ao webhook externo quando há alteração no agendamento de tarefas.
 *
 * Campos:
 * - `eventType`: tipo de evento (CRIADA, ATUALIZADA, DELETADA, EXECUTADA, ERRO, ...)
 * - `eventDateTime`: instante do evento
 * - `tarefa`: representação da `Tarefa` relacionada ao evento
 */
public record SchedulerWebhookRequest(
    String eventType,
    LocalDateTime eventDateTime,
    Tarefa tarefa
) {
}
