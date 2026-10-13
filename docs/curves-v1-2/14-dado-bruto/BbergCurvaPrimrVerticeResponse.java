package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BbergCurvaPrimrVerticeResponse(
    Integer id,
    String tickerBloomberg,
    String precoUltimo,
    String precoLiquidacao,
    String precoMedio,
    Integer diaVencimento,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataLiquidacaoFinanceira,
    String formaLiquidacao,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVencimentoContrato,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataUltimoNegocio
) {

    public static BbergCurvaPrimrVerticeResponse fromDomain(BbergCurvaPrimr v) {
        return new BbergCurvaPrimrVerticeResponse(
            v.id(), v.tickerBloomberg(), texto(v.precoUltimo()), texto(v.precoLiquidacao()), texto(v.precoMedio()),
            v.diaVencimento(), v.dataLiquidacaoFinanceira(), v.formaLiquidacao(), v.dataVencimentoContrato(), v.dataUltimoNegocio());
    }

    private static String texto(BigDecimal valor) {
        return valor != null ? valor.toPlainString() : null;
    }
}
