package com.poccurves.engine.application.model;

/**
 * Espelha o CHECK constraint ck_versao_curva_origem_versao de db/migration/V4__versao_curva.sql.
 * Esses três valores são EXATAMENTE os mesmos do campo versionOrigin do evento curve-published.
 */
public enum OrigemVersao {
    CALCULADA,
    IMPORTADA,
    CARREGADA
}
