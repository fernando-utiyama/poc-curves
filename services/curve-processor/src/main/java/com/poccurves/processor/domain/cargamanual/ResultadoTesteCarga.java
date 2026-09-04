package com.poccurves.processor.domain.cargamanual;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado de um teste de validação executado sobre os vértices de uma
 * carga manual de curva, espelhando os campos de validacao_curva
 * (db/migration/V4__versao_curva.sql) que o próprio teste produz — id,
 * versao_curva_id e executado_em são atribuídos na persistência, não aqui.
 */
public record ResultadoTesteCarga(
        String identificador,
        Classificacao classificacao,
        ResultadoValidacaoCarga resultado,
        BigDecimal medidaObservada,
        BigDecimal limiteAplicado,
        String detalhe
) {
    public ResultadoTesteCarga {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException("identificador não pode ser nulo ou vazio");
        }
        Objects.requireNonNull(classificacao, "classificacao não pode ser nula");
        Objects.requireNonNull(resultado, "resultado não pode ser nulo");
    }
}
