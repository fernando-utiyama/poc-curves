package br.com.poc.application.model.scheduler;

import java.util.List;
import java.util.Map;

/**
 * Objeto de domínio que representa o status do scheduler, contendo os status
 * das tarefas conhecidas pelo scheduler.
 *
 * É construído de forma imutável a partir do mapa fornecido.
 */
public class SchedulerStatus {

    private final List<SchedulerTaskStatus> tasks;
    private final Map<String, Boolean> runningTasks;

    /**
     * <summary>
     * Construtor responsável por criar uma instância imutável
     * do status do Scheduler.
     * </summary>
     *
     * @param runningTasks mapa contendo o nome da tarefa e seu status de agendamento
     * @param tasks status das tarefas conhecidas pelo scheduler
     */
    public SchedulerStatus(Map<String, Boolean> runningTasks, List<SchedulerTaskStatus> tasks) {
        this.runningTasks = Map.copyOf(runningTasks);
        this.tasks = List.copyOf(tasks);
    }

    /**
     * <summary>
     * Retorna os status das tarefas.
     * </summary>
     *
     * @return lista imutável de status das tarefas
     */
    public List<SchedulerTaskStatus> getTasks() { return tasks; }

    public Map<String, Boolean> getRunningTasks() { return runningTasks; }
}
