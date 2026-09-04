package com.poccurves.processor.application;

import com.poccurves.processor.domain.ResultadoTesteCarga;

import java.util.List;
import java.util.UUID;

public interface ValidacaoCurvaRepositoryPort {

    void inserirTodos(UUID versaoCurvaId, List<ResultadoTesteCarga> resultados);
}
