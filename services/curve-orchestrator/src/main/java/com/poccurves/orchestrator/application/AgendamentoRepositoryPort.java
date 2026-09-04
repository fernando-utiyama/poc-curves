package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.AgendamentoComUltimaExecucao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendamentoRepositoryPort {
    void inserir(Agendamento agendamento);
    void atualizar(Agendamento agendamento);
    Optional<Agendamento> buscarPorId(UUID id);
    List<Agendamento> listarAtivos();
    List<Agendamento> listarTodos();
    void vincularExecucao(UUID execucaoId, UUID agendamentoId);
    List<AgendamentoComUltimaExecucao> buscarComUltimaExecucao();
}
