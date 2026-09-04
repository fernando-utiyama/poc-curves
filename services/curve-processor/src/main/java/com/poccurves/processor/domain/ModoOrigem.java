package com.poccurves.processor.domain;

/**
 * Espelha o CHECK constraint ck_definicao_curva_modo_origem de
 * db/migration/V1__definicao_curva.sql. Duplicado deliberadamente do
 * `ModoOrigem` de curve-api — este repo não tem módulo compartilhado de
 * domínio de curva (só o envelope de evento é compartilhado, via
 * services/common); cada serviço lê sua própria fatia da mesma tabela.
 */
public enum ModoOrigem {
    BOOTSTRAPPED,
    IMPORTED
}
