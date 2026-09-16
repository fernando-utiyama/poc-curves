package com.poccurves.orchestrator.application.port;
import com.poccurves.orchestrator.application.model.Agendamento;


import java.util.List;
import java.util.UUID;

public interface AgendamentoSchedulerPort {
    void registrar(Agendamento agendamento);
    void cancelar(UUID id);
    void reagendar(Agendamento agendamento);

    /**
     * Converge o registro local (por réplica) para o conjunto de agendamentos ativos informado
     * (openspec/changes/orchestrator-multi-instance-scheduling): registra o que é novo, cancela o
     * que não está mais na lista, e reagenda o que mudou de expressão de horário ou fuso horário.
     */
    void reconciliar(List<Agendamento> ativos);
}
