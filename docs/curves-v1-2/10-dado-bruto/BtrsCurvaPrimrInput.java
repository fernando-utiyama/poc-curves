package br.com.poc.domain.cadastro;

import java.math.BigDecimal;

/** Campos de um vértice da B3 digitados pelo gestor. */
public record BtrsCurvaPrimrInput(
    Integer diasCorridos,
    Integer diasUteis,
    BigDecimal valor,
    BigDecimal fatorAcumulado,
    BigDecimal fatorDia
) {
}
