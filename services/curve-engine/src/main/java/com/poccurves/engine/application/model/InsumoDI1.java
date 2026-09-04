package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Insumo de dados de mercado para a construção da curva PRE DI1.
 */
public record InsumoDI1(
        String ticker,
        BigDecimal taxaAjuste,
        Integer diasUteisVencimento,
        LocalDate dataVencimento
) {
}
