package com.poccurves.processor.application;
import com.poccurves.processor.domain.cargamanual.DivergenciaValor;
import com.poccurves.processor.domain.ingestao.PontoDadoMercado;


import java.util.List;
import java.util.Optional;

public interface PontoDadoMercadoRepositoryPort {

    List<PontoDadoMercado> buscarPorLoteIngestaoId(long loteIngestaoId);

    Optional<DivergenciaValor> upsert(PontoDadoMercado ponto, long loteIngestaoId);
}
