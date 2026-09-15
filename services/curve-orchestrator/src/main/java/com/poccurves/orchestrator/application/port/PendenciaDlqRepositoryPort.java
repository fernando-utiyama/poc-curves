package com.poccurves.orchestrator.application.port;
import com.poccurves.orchestrator.application.model.PendenciaDlq;


import java.util.Optional;

public interface PendenciaDlqRepositoryPort {
    void inserir(PendenciaDlq pendencia);
    void atualizar(PendenciaDlq pendencia);
    Optional<PendenciaDlq> buscarPorIdEvento(String idEvento);
    Optional<PendenciaDlq> buscarPorId(Long id);
}
