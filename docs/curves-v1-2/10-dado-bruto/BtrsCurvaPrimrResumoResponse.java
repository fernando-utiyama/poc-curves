package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.SituacaoCurva;
import br.com.poc.domain.cadastro.BtrsCurvaPrimrResumo;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record BtrsCurvaPrimrResumoResponse(
    String codigo,
    String nome,
    SituacaoCurva situacao,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataBase,
    long quantidadeVertices,
    List<String> tickersProvedor,
    boolean curvaConstruida
) {

    public static BtrsCurvaPrimrResumoResponse fromDomain(BtrsCurvaPrimrResumo d) {
        return new BtrsCurvaPrimrResumoResponse(
            d.codigo(),
            d.nome(),
            d.situacao(),
            d.dataBase(),
            d.quantidadeVertices(),
            d.tickersProvedor(),
            d.curvaConstruida()
        );
    }
}
