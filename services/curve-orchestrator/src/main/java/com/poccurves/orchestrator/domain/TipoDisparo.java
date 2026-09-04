package com.poccurves.orchestrator.domain;

/**
 * Espelha o CHECK constraint ck_execucao_curva_disparo de db/migration/V3__execucao_curva.sql.
 */
public enum TipoDisparo {
    AGENDADO,
    MANUAL,
    BACKFILL,
    CARGA_MANUAL
}
