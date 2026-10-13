package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.BtrsCurvaPrimrInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Requisição para inclusão/alteração de vértice de dado bruto da B3")
public record BtrsCurvaPrimrVerticeRequest(
    @NotNull @Schema(description = "Dias corridos até o vértice", example = "30") Integer diasCorridos,
    @NotNull @Schema(description = "Dias úteis até o vértice", example = "21") Integer diasUteis,
    @NotNull @Schema(description = "Taxa do vértice (% ao ano)", example = "13.900000000000") BigDecimal valor,
    @Schema(description = "Fator acumulado (opcional)", example = "1.0114000000000000") BigDecimal fatorAcumulado,
    @Schema(description = "Fator do dia (opcional)", example = "1.0004000000000000") BigDecimal fatorDia
) {

    public BtrsCurvaPrimrInput toDomain() {
        return new BtrsCurvaPrimrInput(diasCorridos, diasUteis, valor, fatorAcumulado, fatorDia);
    }
}
