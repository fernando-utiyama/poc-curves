package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record AnbmaCurvaPrimrVerticeResponse(
    Integer id,
    Integer prazoDiasCorridos,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vencimento,
    String taxa
) {

    public static AnbmaCurvaPrimrVerticeResponse fromDomain(AnbmaCurvaPrimr v) {
        Integer prazo = v.vertice() != null ? v.vertice().intValue() : null;
        return new AnbmaCurvaPrimrVerticeResponse(
            v.id(),
            prazo,
            prazo != null && v.dataBase() != null ? v.dataBase().plusDays(prazo) : null,
            v.taxa() != null ? v.taxa().toPlainString() : null);
    }
}
