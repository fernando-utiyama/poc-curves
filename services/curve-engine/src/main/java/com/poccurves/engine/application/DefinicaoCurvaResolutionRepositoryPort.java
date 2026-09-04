package com.poccurves.engine.application;
import com.poccurves.engine.domain.interpolacao.ConfiguracaoInterpolacao;
import com.poccurves.engine.domain.versao.DefinicaoResolvida;


import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DefinicaoCurvaResolutionRepositoryPort {
    Optional<DefinicaoResolvida> resolverVigente(String codigoCurva, LocalDate dataReferencia);
    Optional<UUID> resolverIdPorCodigo(String codigo);
    Optional<ConfiguracaoInterpolacao> resolverConfiguracaoInterpolacao(String codigoCurva, LocalDate dataReferencia);
}
