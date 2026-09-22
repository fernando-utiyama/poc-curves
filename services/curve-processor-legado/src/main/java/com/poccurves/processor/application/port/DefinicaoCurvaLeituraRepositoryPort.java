package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.DefinicaoCurvaResumo;


import java.util.Optional;

public interface DefinicaoCurvaLeituraRepositoryPort {

    Optional<DefinicaoCurvaResumo> resolverPorCodigo(String codigo);
}
