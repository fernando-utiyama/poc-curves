package com.poccurves.processor.application;

import com.poccurves.processor.domain.ProcedenciaCurva;

import java.util.Optional;
import java.util.UUID;

public interface ProcedenciaCurvaRepositoryPort {

    Optional<UUID> buscarVersaoCurvaIdPorHashArquivo(String hashArquivo);

    void inserir(UUID versaoCurvaId, ProcedenciaCurva procedencia, int numeroVersaoDefinicao);
}
