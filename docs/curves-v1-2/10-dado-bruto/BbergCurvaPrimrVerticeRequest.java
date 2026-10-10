package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.BbergCurvaPrimrInput;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BbergCurvaPrimrVerticeRequest(
    String tickerBloomberg,
    BigDecimal precoUltimo,
    BigDecimal precoLiquidacao,
    BigDecimal precoMedio,
    Integer diaVencimento,
    LocalDate dataLiquidacaoFinanceira,
    String formaLiquidacao,
    LocalDate dataVencimentoContrato,
    LocalDate dataUltimoNegocio
) {

    public BbergCurvaPrimrInput toDomain() {
        return new BbergCurvaPrimrInput(tickerBloomberg, precoUltimo, precoLiquidacao, precoMedio, diaVencimento,
            dataLiquidacaoFinanceira, formaLiquidacao, dataVencimentoContrato, dataUltimoNegocio);
    }
}
