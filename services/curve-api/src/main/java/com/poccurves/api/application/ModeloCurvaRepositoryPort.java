package com.poccurves.api.application;

import java.util.Optional;
import java.util.UUID;

public interface ModeloCurvaRepositoryPort {

    record ModeloCurvaRegistro(
            UUID id,
            String codigo,
            String nome,
            String tipo,
            String estado,
            String codigoFonte,
            String checksum
    ) {}

    Optional<ModeloCurvaRegistro> buscarPorId(UUID id);

    Optional<ModeloCurvaRegistro> buscarPorCodigo(String codigo);
}
