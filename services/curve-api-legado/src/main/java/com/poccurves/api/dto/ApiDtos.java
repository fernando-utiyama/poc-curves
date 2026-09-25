package com.poccurves.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class ApiDtos {

    public record CurvaMercadoResponse(
            String tickerIndcd,
            String classfInstt,
            String classAtivo,
            String moedaNegoc,
            LocalDate inicVigencia,
            String usuarCalc
    ) {}

    public record CatalogoCurvasResponse(List<CurvaMercadoResponse> curvas) {}

    public record PontoCurvaDTO(LocalDate dataVertice, BigDecimal valor) {}

    public record CurvaDadosResponse(String tickerIndcd, LocalDate dataReferencia, List<PontoCurvaDTO> pontos) {}

    public record ComparacaoCurvasRequest(LocalDate dataReferencia, String tickerA, String tickerB) {}

    public record ItemComparacaoCurvasDTO(LocalDate dataVertice, BigDecimal valorA, BigDecimal valorB) {}

    public record ComparacaoCurvasResponse(
            LocalDate dataReferencia,
            String tickerA,
            String tickerB,
            List<ItemComparacaoCurvasDTO> pontos
    ) {}

    public record ErroResposta(
            String codigo,
            String mensagem,
            List<String> detalhes,
            Instant timestamp
    ) {
        public ErroResposta(String codigo, String mensagem) {
            this(codigo, mensagem, null, Instant.now());
        }

        public ErroResposta(String codigo, String mensagem, List<String> detalhes) {
            this(codigo, mensagem, detalhes, Instant.now());
        }
    }
}
