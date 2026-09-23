package br.com.poc.application.port.in;

import br.com.poc.application.model.scheduler.SchedulerStatus;
import br.com.poc.application.model.scheduler.SchedulerTaskStatus;

/**
 * <summary>
 * Interface que define os casos de uso do Scheduler.
 * <p>
 * Representa a porta de entrada da camada de aplicação.
 * </summary>
 */
public interface SchedulerUseCase {

    void startAll();

    void stopAll();

    SchedulerStatus status(String status, String nomeTarefa);

    void scheduleTask(Long tarefaId);

    void stopTask(Long tarefaId);

    void executeTask(Long tarefaId);

    void cancelTask(Long tarefaId);

    void resetTask(Long tarefaId);

    SchedulerTaskStatus getTaskStatus(Long tarefaId);
}
