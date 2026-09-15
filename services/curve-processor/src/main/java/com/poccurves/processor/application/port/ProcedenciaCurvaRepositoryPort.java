package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.ProcedenciaCurva;


import java.util.Optional;
import java.util.UUID;

public interface ProcedenciaCurvaRepositoryPort {

    Optional<UUID> buscarVersaoCurvaIdPorHashArquivo(String hashArquivo);

    void inserir(UUID versaoCurvaId, ProcedenciaCurva procedencia, int numeroVersaoDefinicao);
}
