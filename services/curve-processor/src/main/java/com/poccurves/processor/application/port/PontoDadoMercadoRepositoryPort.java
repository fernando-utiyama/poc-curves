package com.poccurves.processor.application.port;
import com.poccurves.processor.application.model.DivergenciaValor;
import com.poccurves.processor.application.model.PontoDadoMercado;


import java.util.List;
import java.util.Optional;

public interface PontoDadoMercadoRepositoryPort {

    List<PontoDadoMercado> buscarPorLoteIngestaoId(long loteIngestaoId);

    Optional<DivergenciaValor> upsert(PontoDadoMercado ponto, long loteIngestaoId);
}
