package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimrInput;

import java.math.BigDecimal;

public record AnbmaCurvaPrimrVerticeRequest(
    Integer prazoDiasCorridos,
    BigDecimal taxa
) {

    public AnbmaCurvaPrimrInput toDomain() {
        return new AnbmaCurvaPrimrInput(prazoDiasCorridos, taxa);
    }
}
