package com.poccurves.api.application;

import com.poccurves.api.domain.DefinicaoCurva;
import com.poccurves.api.domain.EstadoDefinicaoCurva;
import com.poccurves.api.domain.ModoOrigem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DefinicaoCurvaRepositoryPort {

    boolean existeCodigo(String codigo);

    Optional<DefinicaoCurva> buscarPorCodigo(String codigo);

    Optional<DefinicaoCurva> buscarPorId(UUID id);

    void salvar(DefinicaoCurva def);

    void atualizar(DefinicaoCurva def);

    List<DefinicaoCurva> listar(
            String codigo,
            String nome,
            String moeda,
            ModoOrigem modoOrigem,
            EstadoDefinicaoCurva estado,
            int offset,
            int limit
    );

    int contar(
            String codigo,
            String nome,
            String moeda,
            ModoOrigem modoOrigem,
            EstadoDefinicaoCurva estado
    );
}
