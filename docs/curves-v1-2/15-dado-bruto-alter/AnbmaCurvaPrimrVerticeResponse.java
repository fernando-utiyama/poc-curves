package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record AnbmaCurvaPrimrVerticeResponse(
    Long id,
    Integer prazoDiasCorridos,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vencimento,
    String tipoInstrumento,
    String taxa
) {

    public static AnbmaCurvaPrimrVerticeResponse fromDomain(AnbmaCurvaPrimr v) {
        Integer prazo = v.vertice() != null ? v.vertice().intValue() : null;
        return new AnbmaCurvaPrimrVerticeResponse(
            v.id(),
            prazo,
            v.vencimento() != null ? v.vencimento() : prazo != null && v.dataBase() != null ? v.dataBase().plusDays(prazo) : null,
            v.tipoInstrumento(),
            v.taxa() != null ? v.taxa().toPlainString() : null);
    }
}
