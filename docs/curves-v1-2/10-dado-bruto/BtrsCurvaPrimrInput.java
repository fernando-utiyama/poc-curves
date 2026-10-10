package br.com.poc.domain.cadastro;

import java.math.BigDecimal;

/** Campos editáveis de um vértice da B3. */
public record BtrsCurvaPrimrInput(
    Integer diasCorridos,
    Integer diasUteis,
    BigDecimal valor,
    BigDecimal fatorAcumulado,
    BigDecimal fatorDia
) {
}
