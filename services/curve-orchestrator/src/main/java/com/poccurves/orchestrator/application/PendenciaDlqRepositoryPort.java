package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.PendenciaDlq;

import java.util.Optional;

public interface PendenciaDlqRepositoryPort {
    void inserir(PendenciaDlq pendencia);
    void atualizar(PendenciaDlq pendencia);
    Optional<PendenciaDlq> buscarPorIdEvento(String idEvento);
    Optional<PendenciaDlq> buscarPorId(Long id);
}
