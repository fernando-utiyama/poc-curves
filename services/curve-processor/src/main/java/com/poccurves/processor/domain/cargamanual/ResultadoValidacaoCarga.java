package com.poccurves.processor.domain.cargamanual;

/** Espelha o CHECK constraint ck_validacao_curva_resultado de db/migration/V4__versao_curva.sql. */
public enum ResultadoValidacaoCarga {
    APROVADO,
    REPROVADO,
    NAO_APLICAVEL
}
