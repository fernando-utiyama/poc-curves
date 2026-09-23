package br.com.poc.application.port.out;

import br.com.poc.application.model.scheduler.Tarefa;

/**
 * Porta de saída para notificação de mudanças no agendamento de tarefas.
 *
 * Implementações podem enviar webhooks, publicar eventos ou integrar com outros sistemas
 * de notificação.
 */
public interface WebhookNotifierPort {
    void notifyScheduleChanged(String eventType, Tarefa tarefa);
}
