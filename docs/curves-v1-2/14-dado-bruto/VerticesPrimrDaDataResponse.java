package br.com.poc.adapter.in.api.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Vértices brutos de uma data e se ela já foi construída. */
@Schema(description = "Vértices do dado bruto de uma data")
public record VerticesPrimrDaDataResponse<T>(
    @Schema(description = "A data já foi construída pelo engine (o bruto não reconstrói a curva)", example = "false")
    boolean curvaConstruida,
    @Schema(description = "Vértices gravados na data") List<T> vertices
) {
}
