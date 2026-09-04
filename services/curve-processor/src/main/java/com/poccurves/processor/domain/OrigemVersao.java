package com.poccurves.processor.domain;

/**
 * Espelha o CHECK constraint ck_versao_curva_origem_versao de
 * db/migration/V4__versao_curva.sql. O curve-processor só produz versões
 * IMPORTADA (curva pronta da B3) e CARREGADA (carga manual) — CALCULADA é
 * exclusiva do curve-engine (bootstrapping).
 */
public enum OrigemVersao {
    CALCULADA,
    IMPORTADA,
    CARREGADA
}
