package com.poccurves.orchestrator.domain;

/**
 * Espelha o CHECK constraint ck_execucao_curva_momento_curva de db/migration/V3__execucao_curva.sql.
 */
public enum MomentoCurva {
    ABERTURA,
    INTRADIA,
    FECHAMENTO
}
