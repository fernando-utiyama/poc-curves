package com.poccurves.orchestrator.domain;

/** Espelha o CHECK constraint ck_pendencia_dlq_estado de db/migration/V6__pendencia_dlq.sql. */
public enum EstadoPendenciaDlq {
    ABERTA,
    EM_REPROCESSAMENTO,
    RESOLVIDA,
    DESCARTADA,
    OBSOLETA
}
