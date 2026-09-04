package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.Vertice;


import java.util.List;
import java.util.UUID;

public interface VerticeCurvaRepositoryPort {
    void inserirTodos(UUID versaoCurvaId, List<Vertice> vertices);
    List<Vertice> buscarPorVersaoCurva(UUID versaoCurvaId);
}
