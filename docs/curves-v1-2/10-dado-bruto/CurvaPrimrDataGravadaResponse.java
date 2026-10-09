package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.CurvaPrimrDataGravada;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record CurvaPrimrDataGravadaResponse(
    String codigo,
    String nome,
    SituacaoCurva situacao,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataBase,
    long quantidadeVertices,
    List<String> tickersProvedor,
    boolean curvaConstruida
) {

    public static CurvaPrimrDataGravadaResponse fromDomain(CurvaPrimrDataGravada d) {
        return new CurvaPrimrDataGravadaResponse(
            d.codigo(), d.nome(), d.situacao(), d.dataBase(), d.quantidadeVertices(), d.tickersProvedor(), d.curvaConstruida());
    }
}
