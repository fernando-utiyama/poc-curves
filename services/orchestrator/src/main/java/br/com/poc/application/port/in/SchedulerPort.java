package br.com.poc.application.port.in;

import br.com.poc.application.model.scheduler.SchedulerTaskStatus;
import br.com.poc.application.model.scheduler.Tarefa;

/**
 * Porta de agendamento que abstrai o mecanismo de scheduling (ex: Spring Scheduler).
 *
 * Permite agendar tarefas por regra cron ou por instante (regraIntervalo) e
 * fornece operações para interromper agendamentos individuais ou todos.
 */
public interface SchedulerPort {

    void stop(String taskName);

    void schedule(Tarefa tarefa, Runnable action);

    void stopAll();

    SchedulerTaskStatus getTaskStatus(String taskName);
}
