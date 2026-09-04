package com.poccurves.orchestrator.domain;

/**
 * Espelha o CHECK constraint ck_execucao_curva_estado de db/migration/V3__execucao_curva.sql.
 */
public enum EstadoExecucao {
    PENDENTE,
    EXECUTANDO,
    CONSTRUINDO,
    EM_RISCO,
    ATRASADA,
    CONCLUIDA,
    SEM_DADO,
    FALHOU
}
