package com.poccurves.orchestrator.application.port;
import com.poccurves.orchestrator.application.model.Agendamento;


import java.util.UUID;

public interface AgendamentoSchedulerPort {
    void registrar(Agendamento agendamento);
    void cancelar(UUID id);
    void reagendar(Agendamento agendamento);
}
