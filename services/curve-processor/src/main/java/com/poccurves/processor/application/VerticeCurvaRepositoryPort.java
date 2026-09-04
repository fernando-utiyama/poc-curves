package com.poccurves.processor.application;
import com.poccurves.processor.domain.curva.VerticeCurva;


import java.util.List;
import java.util.UUID;

public interface VerticeCurvaRepositoryPort {

    void inserirTodos(UUID versaoCurvaId, List<VerticeCurva> vertices);
}
