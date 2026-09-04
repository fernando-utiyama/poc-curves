package com.poccurves.processor.domain;

import java.math.BigDecimal;

/**
 * Converte texto numérico da fonte para {@link BigDecimal}, tratando
 * explicitamente qual caractere é o separador decimal — nunca assume ponto.
 * Preserva a escala exata do texto original (D8 do design.md: nunca passa
 * por {@code double}).
 */
public final class ConversorDecimal {

    private ConversorDecimal() {
    }

    /**
     * @param valorBruto        texto numérico como veio da fonte, ex. "14.129" ou "14,129"
     * @param separadorDecimal  caractere que representa a casa decimal na fonte (ex. '.' ou ',')
     * @throws IllegalArgumentException se valorBruto for nulo/em branco, ou não for numérico
     *                                   depois de normalizado
     */
    public static BigDecimal paraBigDecimal(String valorBruto, char separadorDecimal) {
        if (valorBruto == null || valorBruto.isBlank()) {
            throw new IllegalArgumentException("valorBruto não pode ser nulo ou vazio");
        }
        String normalizado = separadorDecimal == '.'
                ? valorBruto
                : valorBruto.replace(String.valueOf(separadorDecimal), ".");
        try {
            return new BigDecimal(normalizado);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "valor não numérico após normalizar separador decimal '" + separadorDecimal
                            + "': \"" + valorBruto + "\"", e);
        }
    }
}
