package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.VerticeCurva;


import java.util.List;
import java.util.UUID;

public interface VerticeCurvaRepositoryPort {

    void inserirTodos(UUID versaoCurvaId, List<VerticeCurva> vertices);
}
