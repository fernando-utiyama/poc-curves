package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimrInput;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Requisição para inclusão/alteração de vértice de dado bruto da Anbima")
public record AnbmaCurvaPrimrVerticeRequest(
    @NotNull @Schema(description = "Prazo em dias corridos até o vencimento do título", example = "229") Integer prazoDiasCorridos,
    @Schema(description = "Taxa (opcional)", example = "10.500000000000") BigDecimal taxa,
    @Size(max = 50) @Schema(description = "Tipo do título (opcional)", example = "NTN-B") String tipoInstrumento,
    @Schema(description = "Vencimento do título (opcional; sem ele, vale data-base mais o prazo)", example = "2027-05-15")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vencimento
) {

    public AnbmaCurvaPrimrInput toDomain() {
        return new AnbmaCurvaPrimrInput(prazoDiasCorridos, taxa, tipoInstrumento, vencimento);
    }
}
