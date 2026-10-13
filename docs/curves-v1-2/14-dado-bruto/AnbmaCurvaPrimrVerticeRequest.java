package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AnbmaCurvaPrimrInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Requisição para inclusão/alteração de vértice de dado bruto da Anbima")
public record AnbmaCurvaPrimrVerticeRequest(
    @NotNull @Schema(description = "Prazo em dias corridos até o vencimento do título", example = "229") Integer prazoDiasCorridos,
    @Schema(description = "Taxa (opcional)", example = "10.500000000000") BigDecimal taxa
) {

    public AnbmaCurvaPrimrInput toDomain() {
        return new AnbmaCurvaPrimrInput(prazoDiasCorridos, taxa);
    }
}
