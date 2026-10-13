package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimr;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Vértice de dado bruto da Anbima")
public record AnbmaCurvaPrimrVerticeResponse(
    @Schema(description = "Id do vértice", example = "1") Long id,
    @Schema(description = "Prazo em dias corridos até o vencimento do título", example = "229") Integer prazoDiasCorridos,
    @Schema(description = "Vencimento do título (o gravado; sem ele, data-base mais o prazo)", example = "2027-05-01")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vencimento,
    @Schema(description = "Tipo do título", example = "NTN-B") String tipoInstrumento,
    @Schema(description = "Taxa, como gravada (texto, sem arredondar)", example = "10.500000000000") String taxa
) {

    public static AnbmaCurvaPrimrVerticeResponse fromDomain(AnbmaCurvaPrimr v) {
        Integer prazo = v.vertice() != null ? v.vertice().intValue() : null;
        LocalDate vencimento = v.vencimento() != null
            ? v.vencimento()
            : prazo != null && v.dataBase() != null ? v.dataBase().plusDays(prazo) : null;
        return new AnbmaCurvaPrimrVerticeResponse(
            v.id(),
            prazo,
            vencimento,
            v.tipoInstrumento(),
            v.taxa() != null ? v.taxa().toPlainString() : null);
    }
}
