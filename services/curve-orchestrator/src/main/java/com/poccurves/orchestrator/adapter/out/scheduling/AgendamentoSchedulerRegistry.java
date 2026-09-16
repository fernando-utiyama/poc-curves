package com.poccurves.orchestrator.adapter.out.scheduling;
import com.poccurves.orchestrator.application.model.Agendamento;
import com.poccurves.orchestrator.application.port.AgendamentoSchedulerPort;
import com.poccurves.orchestrator.application.usecase.DisparoAgendadoExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class AgendamentoSchedulerRegistry implements AgendamentoSchedulerPort {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoSchedulerRegistry.class);

    /** Guarda a expressão de horário/fuso junto com o future — reconciliar() precisa comparar contra o catálogo sem depender de estado externo. */
    private record RegistroAtivo(String expressaoHorario, String fusoHorario, ScheduledFuture<?> future) {
    }

    private final TaskScheduler taskScheduler;
    private final DisparoAgendadoExecutor executor;
    private final ConcurrentHashMap<UUID, RegistroAtivo> agendamentos = new ConcurrentHashMap<>();

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

        agendamentos.put(agendamento.id(), new RegistroAtivo(agendamento.expressaoHorario(), agendamento.fusoHorario(), future));
        log.info("Agendamento registrado com sucesso. ID: {}", agendamento.id());
    }

    @Override
    public void cancelar(UUID id) {
        RegistroAtivo registro = agendamentos.remove(id);
        if (registro != null) {
            registro.future().cancel(false);
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

    @Override
    public void reconciliar(List<Agendamento> ativos) {
        Set<UUID> idsAtivos = new HashSet<>();
        for (Agendamento agendamento : ativos) {
            idsAtivos.add(agendamento.id());
        }

        // Cancela o que não está mais no catálogo (desativado ou excluído).
        for (UUID idRegistrado : new HashSet<>(agendamentos.keySet())) {
            if (!idsAtivos.contains(idRegistrado)) {
                cancelar(idRegistrado);
            }
        }

        // Registra o que é novo, reagenda o que mudou de expressão de horário ou fuso horário.
        for (Agendamento agendamento : ativos) {
            RegistroAtivo atual = agendamentos.get(agendamento.id());
            if (atual == null) {
                registrar(agendamento);
            } else if (!atual.expressaoHorario().equals(agendamento.expressaoHorario())
                    || !atual.fusoHorario().equals(agendamento.fusoHorario())) {
                reagendar(agendamento);
            }
        }
    }
}
