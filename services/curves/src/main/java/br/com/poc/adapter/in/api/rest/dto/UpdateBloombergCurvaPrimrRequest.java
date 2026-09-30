package br.com.poc.adapter.in.api.rest.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateBloombergCurvaPrimrRequest(BigDecimal vPrecoLiqdc,
                                               BigDecimal vPrecoMed,
                                               BigDecimal vPrecoUlt,
                                               Integer cDiaVcto,
                                               LocalDate dLiqdcFincr,
                                               @Size(max = 50) String cTickerBberg,
                                               @Size(max = 20) String cFormaLiqdc,
                                               LocalDate dVctoContr,
                                               LocalDate dUltNegoc,
                                               LocalDate dBaseReft) {
}
