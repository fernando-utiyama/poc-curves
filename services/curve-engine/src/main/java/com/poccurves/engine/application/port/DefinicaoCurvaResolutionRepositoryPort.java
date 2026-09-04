package com.poccurves.engine.application.port;
import com.poccurves.engine.application.model.ConfiguracaoInterpolacao;
import com.poccurves.engine.application.model.DefinicaoResolvida;


import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DefinicaoCurvaResolutionRepositoryPort {
    Optional<DefinicaoResolvida> resolverVigente(String codigoCurva, LocalDate dataReferencia);
    Optional<UUID> resolverIdPorCodigo(String codigo);
    Optional<ConfiguracaoInterpolacao> resolverConfiguracaoInterpolacao(String codigoCurva, LocalDate dataReferencia);
}
