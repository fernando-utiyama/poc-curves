package br.com.poc.domain.model;

import java.math.BigDecimal;

public record PontoInterpolado(
    int prazo,
    BigDecimal taxa,
    BigDecimal fatorAcumulado,
    boolean extrapolado
) {
}
