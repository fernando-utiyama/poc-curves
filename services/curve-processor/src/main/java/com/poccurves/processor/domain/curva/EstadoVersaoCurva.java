package com.poccurves.processor.domain.curva;

/** Espelha o CHECK constraint ck_versao_curva_estado de db/migration/V4__versao_curva.sql. */
public enum EstadoVersaoCurva {
    EM_VALIDACAO,
    PUBLICADA,
    REPROVADA,
    SUBSTITUIDA
}
