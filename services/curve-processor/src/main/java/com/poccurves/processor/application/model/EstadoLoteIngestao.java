package com.poccurves.processor.application.model;

/** Espelha o CHECK constraint ck_lote_ingestao_estado de db/migration/V2__dado_mercado.sql. */
public enum EstadoLoteIngestao {
    ABERTO,
    COMPLETO,
    INCOMPLETO
}
