package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.ResultadoTesteCarga;


import java.util.List;
import java.util.UUID;

public interface ValidacaoCurvaRepositoryPort {

    void inserirTodos(UUID versaoCurvaId, List<ResultadoTesteCarga> resultados);
}
