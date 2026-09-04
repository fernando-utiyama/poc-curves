package com.poccurves.processor.domain;

/**
 * Nenhum DatasetParser registrado para o dataset declarado no envelope.
 * Falha permanente — nunca vale retentar, vai direto para a dead-letter
 * como UNKNOWN_DATASET.
 */
public final class DatasetDesconhecidoException extends RuntimeException {

    public DatasetDesconhecidoException(String dataset) {
        super("UNKNOWN_DATASET: nenhum parser registrado para o dataset \"" + dataset + "\"");
    }
}
