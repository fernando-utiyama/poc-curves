package com.poccurves.engine.application;

import com.poccurves.engine.dto.EngineDtos.*;

import java.util.Optional;

public interface CacheInterpolacaoPort {
    Optional<ItemInterpolacaoResultado> buscar(String chave, int prazoDiasUteis);
    void armazenar(String chave, ItemInterpolacaoResultado item);
}
