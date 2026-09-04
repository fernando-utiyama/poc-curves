package com.poccurves.processor.application;
import com.poccurves.processor.domain.curva.DefinicaoCurvaResumo;


import java.util.Optional;

public interface DefinicaoCurvaLeituraRepositoryPort {

    Optional<DefinicaoCurvaResumo> resolverPorCodigo(String codigo);
}
