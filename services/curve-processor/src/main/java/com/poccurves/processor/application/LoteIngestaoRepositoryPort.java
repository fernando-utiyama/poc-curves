package com.poccurves.processor.application;

import com.poccurves.processor.domain.LoteIngestao;
import com.poccurves.processor.domain.LoteJaExisteException;

import java.util.Optional;

public interface LoteIngestaoRepositoryPort {

    Optional<LoteIngestao> buscarPorLoteExternoId(String loteExternoId);

    /** @throws LoteJaExisteException se a restrição UNIQUE de lote_externo_id for violada (corrida entre faixas). */
    void inserir(LoteIngestao lote);

    void atualizar(LoteIngestao lote);
}
