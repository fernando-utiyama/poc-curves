package br.com.poc.domain.cadastro;

import java.math.BigDecimal;

/** Campos editáveis de um vértice da ANBIMA. */
public record AnbmaCurvaPrimrInput(
    Integer prazoDiasCorridos,
    BigDecimal taxa
) {
}
