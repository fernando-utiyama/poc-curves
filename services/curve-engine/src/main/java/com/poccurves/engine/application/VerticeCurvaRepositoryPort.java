package com.poccurves.engine.application;
import com.poccurves.engine.domain.curva.Vertice;


import java.util.List;
import java.util.UUID;

public interface VerticeCurvaRepositoryPort {
    void inserirTodos(UUID versaoCurvaId, List<Vertice> vertices);
    List<Vertice> buscarPorVersaoCurva(UUID versaoCurvaId);
}
