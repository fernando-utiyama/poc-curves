package com.poccurves.engine.application.model;

/** Espelha o CHECK constraint ck_validacao_curva_resultado de db/migration/V4__versao_curva.sql. */
public enum ResultadoValidacao {
    APROVADO,
    REPROVADO,
    NAO_APLICAVEL
}
