package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.Agendamento;

import java.util.UUID;

public interface AgendamentoSchedulerPort {
    void registrar(Agendamento agendamento);
    void cancelar(UUID id);
    void reagendar(Agendamento agendamento);
}
