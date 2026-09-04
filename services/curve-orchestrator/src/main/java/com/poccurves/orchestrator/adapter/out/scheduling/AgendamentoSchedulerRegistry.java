package com.poccurves.orchestrator.adapter.out.scheduling;

import com.poccurves.orchestrator.application.AgendamentoSchedulerPort;
import com.poccurves.orchestrator.application.DisparoAgendadoExecutor;
import com.poccurves.orchestrator.domain.Agendamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class AgendamentoSchedulerRegistry implements AgendamentoSchedulerPort {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoSchedulerRegistry.class);

    private final TaskScheduler taskScheduler;
    private final DisparoAgendadoExecutor executor;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> agendamentos = new ConcurrentHashMap<>();

    public AgendamentoSchedulerRegistry(
            @Qualifier("agendamentoTaskScheduler") TaskScheduler taskScheduler,
            DisparoAgendadoExecutor executor
    ) {
        this.taskScheduler = taskScheduler;
        this.executor = executor;
    }

    @Override
    public void registrar(Agendamento agendamento) {
        if (agendamentos.containsKey(agendamento.id())) {
            log.warn("Tentativa de registrar agendamento que já está registrado. ID: {}", agendamento.id());
            return;
        }

        CronTrigger trigger = new CronTrigger(agendamento.expressaoHorario(), ZoneId.of(agendamento.fusoHorario()));
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executor.executar(agendamento.id()),
                trigger
        );

        agendamentos.put(agendamento.id(), future);
        log.info("Agendamento registrado com sucesso. ID: {}", agendamento.id());
    }

    @Override
    public void cancelar(UUID id) {
        ScheduledFuture<?> future = agendamentos.remove(id);
        if (future != null) {
            future.cancel(false);
            log.info("Agendamento cancelado com sucesso. ID: {}", id);
        } else {
            log.warn("Tentativa de cancelar agendamento que não está registrado. ID: {}", id);
        }
    }

    @Override
    public void reagendar(Agendamento agendamento) {
        cancelar(agendamento.id());
        registrar(agendamento);
    }
}
