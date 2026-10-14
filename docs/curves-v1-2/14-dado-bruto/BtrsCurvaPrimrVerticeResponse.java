package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Vértice de dado bruto da B3")
public record BtrsCurvaPrimrVerticeResponse(
    @Schema(description = "Id do vértice", example = "1") Integer id,
    @Schema(description = "Dias corridos até o vértice", example = "30") Integer diasCorridos,
    @Schema(description = "Dias úteis até o vértice", example = "21") Integer diasUteis,
    @Schema(description = "Data do vértice (data-base mais os dias corridos)", example = "2026-10-14")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVertice,
    @Schema(description = "Taxa do vértice, como gravada (texto, sem arredondar)", example = "13.900000000000") String valor,
    @Schema(description = "Fator acumulado (texto)", example = "1.0114000000000000") String fatorAcumulado,
    @Schema(description = "Fator do dia (texto)", example = "1.0004000000000000") String fatorDia
) {

    public static BtrsCurvaPrimrVerticeResponse fromDomain(BtrsCurvaPrimr v) {
        return new BtrsCurvaPrimrVerticeResponse(
            v.id(), v.diasCorridos(), v.diasUteis(), v.dataVertice(),
            v.valor() != null ? v.valor().toPlainString() : null,
            v.fatorAcumulado() != null ? v.fatorAcumulado().toPlainString() : null,
            v.fatorDia() != null ? v.fatorDia().toPlainString() : null);
    }
}
