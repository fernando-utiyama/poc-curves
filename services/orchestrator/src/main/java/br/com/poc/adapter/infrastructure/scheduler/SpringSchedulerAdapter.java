package br.com.poc.adapter.infrastructure.scheduler;

import br.com.poc.application.model.scheduler.SchedulerTaskStatus;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.model.scheduler.TarefaStatus;
import br.com.poc.application.port.in.SchedulerPort;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.CronExpression;
import java.time.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Adapter de agendamento baseado no scheduler do Spring.
 *
 * Suporta duas formas de agendamento fornecidas pela `Tarefa`:
 * - `regraCron`: expressão cron para agendamentos recorrentes.
 * - `regraIntervalo`: instante/intervalo em formato ISO-8601 para agendamento pontual.
 *
 * Implementação:
 * - Mantém um mapa de `ScheduledFuture` para permitir parada individual ou de todas as tarefas.
 * - Converte a `regraIntervalo` de acordo com os formatos ISO aceitos (OffsetDateTime ou LocalDateTime).
 */
@Component
public class SpringSchedulerAdapter implements SchedulerPort {

    private static final String ORIGIN = "SPRING_SCHEDULER";
    private static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    private final TaskScheduler taskScheduler;
    private final TaskExecutor scheduledTaskExecutor;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<String, SchedulerTaskStatus> taskStatuses = new ConcurrentHashMap<>();

    public SpringSchedulerAdapter(TaskScheduler taskScheduler, TaskExecutor scheduledTaskExecutor) {
        this.taskScheduler = taskScheduler;
        this.scheduledTaskExecutor = scheduledTaskExecutor;
    }

    @Override
    public void stop(String taskName) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskName);
        if (future != null) {
            future.cancel(false);
        }

        SchedulerTaskStatus status = taskStatuses.computeIfAbsent(taskName, ignored -> new SchedulerTaskStatus());
        status.setStatus(TarefaStatus.PRONTA.name());
        status.setTeveErro(false);
        status.setUltimaMensagem("Agendamento removido com sucesso");
        status.setProximaExecucao(null);
    }

    @Override
    public void schedule(Tarefa tarefa, Runnable action) {
        validarTarefa(tarefa);

        String taskName = schedulerKey(tarefa);
        stop(taskName);

        Runnable wrappedAction = () -> {
            SchedulerTaskStatus status = taskStatuses.computeIfAbsent(taskName, ignored -> new SchedulerTaskStatus());
            status.setTarefaId(tarefa.getId());
            status.setStatus(TarefaStatus.EXECUTANDO.name());
            status.setTeveErro(false);
            status.setUltimaMensagem("Execucao automatica iniciada");

            try {
                scheduledTaskExecutor.execute(action);
                status.setStatus(TarefaStatus.AGENDADA.name());
                status.setTeveErro(false);
                status.setUltimaMensagem("Execucao automatica concluida com sucesso");
                status.setUltimaExecucao(LocalDateTime.now());
                status.setProximaExecucao(calcularProximaExecucao(tarefa));
            } catch (RuntimeException ex) {
                status.setStatus(TarefaStatus.ERRO.name());
                status.setTeveErro(true);
                status.setUltimaMensagem(ex.getMessage());
                status.setUltimaExecucao(LocalDateTime.now());
                throw ex;
            }
        };

        ScheduledFuture<?> future;
        if (hasText(tarefa.getRegraCron())) {
            future = taskScheduler.schedule(
                wrappedAction,
                new CronTrigger(tarefa.getRegraCron(), APP_ZONE)
            );
        } else if (hasText(tarefa.getRegraIntervalo())) {
            String regra = tarefa.getRegraIntervalo();
            Instant instant = null;
            Duration duration = null;
            try {
                instant = Instant.parse(regra);
            } catch (java.time.format.DateTimeParseException ignored) {
            }
            try {
                duration = Duration.parse(regra);
            } catch (java.time.format.DateTimeParseException ignored) {
            }

            if (instant != null) {
                future = taskScheduler.schedule(wrappedAction, instant);
            } else if (duration != null) {
                future = taskScheduler.scheduleAtFixedRate(
                    wrappedAction,
                    Instant.now().plus(duration),
                    duration
                );
            } else {
                throw new InvalidInputException(ORIGIN, "schedule", "regraIntervalo deve estar em formato ISO-8601");
            }
        } else {
            throw new InvalidInputException(ORIGIN, "schedule", "Tarefa nao possui regra de agendamento");
        }

        scheduledTasks.put(taskName, future);

        SchedulerTaskStatus status = taskStatuses.computeIfAbsent(taskName, ignored -> new SchedulerTaskStatus());
        status.setTarefaId(tarefa.getId());
        status.setStatus(TarefaStatus.AGENDADA.name());
        status.setTeveErro(false);
        status.setUltimaMensagem("Tarefa agendada com sucesso");
        status.setProximaExecucao(calcularProximaExecucao(tarefa));
    }

    @Override
    public void stopAll() {
        scheduledTasks.keySet().forEach(this::stop);
        scheduledTasks.clear();
    }

    @Override
    public SchedulerTaskStatus getTaskStatus(String taskName) { return taskStatuses.get(taskName); }

    private void validarTarefa(Tarefa tarefa) {
        if (tarefa == null) {
            throw new InvalidInputException(ORIGIN, "schedule", "Tarefa nao informada");
        }
        if (tarefa.getId() == null) {
            throw new InvalidInputException(ORIGIN, "schedule", "Id da tarefa nao informado");
        }
        if (!hasText(tarefa.getRegraCron()) && !hasText(tarefa.getRegraIntervalo())) {
            throw new InvalidInputException(ORIGIN, "schedule", "Tarefa nao possui regra de agendamento");
        }
    }

    private String schedulerKey(Tarefa tarefa) { return "tarefa-" + tarefa.getId(); }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private LocalDateTime calcularProximaExecucao(Tarefa tarefa) {
        if (hasText(tarefa.getRegraCron())) {
            try {
                CronExpression cronExpression =
                    CronExpression.parse(tarefa.getRegraCron());

                return cronExpression.next(LocalDateTime.now(APP_ZONE));
            } catch (IllegalArgumentException ex) {
                throw new InvalidInputException(
                    ORIGIN,
                    "schedule",
                    "regraCron invalida: " + tarefa.getRegraCron()
                );
            }
        }

        if (hasText(tarefa.getRegraIntervalo())) {
            String regraIntervalo = tarefa.getRegraIntervalo();

            try {
                Instant instant = Instant.parse(regraIntervalo);

                return LocalDateTime.ofInstant(
                    instant,
                    ZoneId.systemDefault()
                );
            } catch (java.time.format.DateTimeParseException ignored) {
                // Continua para tentar interpretar como Duration.
            }

            try {
                Duration duration = Duration.parse(regraIntervalo);
                return LocalDateTime.now().plus(duration);
            } catch (java.time.format.DateTimeParseException ex) {
                throw new InvalidInputException(
                    ORIGIN,
                    "schedule",
                    "regraIntervalo deve estar em formato ISO-8601"
                );
            }
        }

        return null;
    }
}
