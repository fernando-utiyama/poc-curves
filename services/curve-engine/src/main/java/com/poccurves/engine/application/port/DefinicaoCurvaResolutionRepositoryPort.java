package com.poccurves.engine.application.port;
import com.poccurves.engine.application.construcao.ConfiguracaoInterpolacao;


import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DefinicaoCurvaResolutionRepositoryPort {
    Optional<UUID> resolverIdPorCodigo(String codigo);
    Optional<ConfiguracaoInterpolacao> resolverConfiguracaoInterpolacao(String codigoCurva, LocalDate dataReferencia);
}
