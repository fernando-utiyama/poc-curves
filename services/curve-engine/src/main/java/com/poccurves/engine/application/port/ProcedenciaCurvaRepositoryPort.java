package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.ProcedenciaCurva;


import java.util.Optional;
import java.util.UUID;

public interface ProcedenciaCurvaRepositoryPort {
    void inserir(ProcedenciaCurva procedencia);
    Optional<ProcedenciaCurva> buscarPorVersaoCurva(UUID versaoCurvaId);
}
