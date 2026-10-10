package br.com.poc.domain.cadastro;

import java.math.BigDecimal;

/** Campos de um vértice da ANBIMA digitados pelo gestor: prazo em dias corridos até o vencimento do título e a taxa. */
public record AnbmaCurvaPrimrInput(
    Integer prazoDiasCorridos,
    BigDecimal taxa
) {
}
