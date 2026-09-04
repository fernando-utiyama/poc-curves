package com.poccurves.engine.application;
import com.poccurves.engine.domain.validacao.ResultadoTeste;


import java.util.List;
import java.util.UUID;

public interface ValidacaoCurvaRepositoryPort {
    void inserirTodos(UUID versaoCurvaId, List<ResultadoTeste> resultados);
    List<ResultadoTeste> buscarPorVersaoCurva(UUID versaoCurvaId);
}
