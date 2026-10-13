package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BtrsCurvaPrimrVerticeResponse(
    Integer id,
    Integer diasCorridos,
    Integer diasUteis,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVertice,
    String valor,
    String fatorAcumulado,
    String fatorDia
) {

    public static BtrsCurvaPrimrVerticeResponse fromDomain(BtrsCurvaPrimr v) {
        return new BtrsCurvaPrimrVerticeResponse(
            v.id(), v.diasCorridos(), v.diasUteis(), v.dataVertice(),
            texto(v.valor()), texto(v.fatorAcumulado()), texto(v.fatorDia()));
    }

    private static String texto(BigDecimal valor) {
        return valor != null ? valor.toPlainString() : null;
    }
}
