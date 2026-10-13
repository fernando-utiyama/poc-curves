package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimrInput;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnbmaCurvaPrimrVerticeRequest(
    Integer prazoDiasCorridos,
    BigDecimal taxa,
    String tipoInstrumento,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vencimento
) {

    public AnbmaCurvaPrimrInput toDomain() {
        return new AnbmaCurvaPrimrInput(prazoDiasCorridos, taxa, tipoInstrumento, vencimento);
    }
}
