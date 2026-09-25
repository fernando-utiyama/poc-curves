package com.poccurves.engine.application.port;
import com.poccurves.engine.application.construcao.Vertice;


import java.util.List;
import java.util.UUID;

/** Leitura de {@code vertice_curva} (schema antigo) — ver {@link VersaoCurvaRepositoryPort}. */
public interface VerticeCurvaRepositoryPort {
    List<Vertice> buscarPorVersaoCurva(UUID versaoCurvaId);
}
