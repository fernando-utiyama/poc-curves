package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.ResultadoTeste;


import java.util.List;
import java.util.UUID;

public interface ValidacaoCurvaRepositoryPort {
    void inserirTodos(UUID versaoCurvaId, List<ResultadoTeste> resultados);
    List<ResultadoTeste> buscarPorVersaoCurva(UUID versaoCurvaId);
}
