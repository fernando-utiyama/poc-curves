package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.ModeloCurva;


import java.util.List;
import java.util.Optional;

public interface ModeloCurvaRepositoryPort {
    void inserir(ModeloCurva modelo);
    void atualizar(ModeloCurva modelo);
    Optional<ModeloCurva> buscarPorCodigo(String codigo);
    List<ModeloCurva> listarAtivos();
    List<ModeloCurva> listarTodos();
}
