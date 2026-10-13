package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.CurvaPrimrDataBase;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Uma data com dado bruto gravado de uma curva")
public record CurvaPrimrDataBaseResponse(
    @Schema(description = "Código da curva (opcional)", example = "PRE") String codigo,
    @Schema(description = "Nome da curva", example = "DIxPRE") String nome,
    @Schema(description = "Situação da curva", example = "ATIVO") SituacaoCurva situacao,
    @Schema(description = "Data-base do dado bruto", example = "2026-09-14")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataBase,
    @Schema(description = "Quantidade de vértices gravados na data", example = "278") long quantidadeVertices,
    @Schema(description = "Códigos da curva no provedor (tCurvaPrvdr)", example = "[\"PRE\"]") List<String> tickersProvedor,
    @Schema(description = "A data já foi construída pelo engine", example = "true") boolean curvaConstruida
) {

    public static CurvaPrimrDataBaseResponse fromDomain(CurvaPrimrDataBase d) {
        return new CurvaPrimrDataBaseResponse(
            d.codigo(), d.nome(), d.situacao(), d.dataBase(), d.quantidadeVertices(), d.tickersProvedor(), d.curvaConstruida());
    }
}
