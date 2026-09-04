package com.poccurves.engine.application;
import com.poccurves.engine.domain.versao.ProcedenciaCurva;


import java.util.Optional;
import java.util.UUID;

public interface ProcedenciaCurvaRepositoryPort {
    void inserir(ProcedenciaCurva procedencia);
    Optional<ProcedenciaCurva> buscarPorVersaoCurva(UUID versaoCurvaId);
}
