package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.BtrsCurvaPrimrInput;

import java.math.BigDecimal;

public record BtrsCurvaPrimrVerticeRequest(
    Integer diasCorridos,
    Integer diasUteis,
    BigDecimal valor,
    BigDecimal fatorAcumulado,
    BigDecimal fatorDia
) {

    public BtrsCurvaPrimrInput toDomain() {
        return new BtrsCurvaPrimrInput(diasCorridos, diasUteis, valor, fatorAcumulado, fatorDia);
    }
}
