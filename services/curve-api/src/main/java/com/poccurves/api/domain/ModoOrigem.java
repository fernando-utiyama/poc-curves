package com.poccurves.api.domain;

/** Espelha o CHECK constraint ck_definicao_curva_modo_origem de db/migration/V1__definicao_curva.sql. */
public enum ModoOrigem {
    BOOTSTRAPPED,
    IMPORTED
}
