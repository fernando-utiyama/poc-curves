package br.com.poc.adapter.in.api.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BloombergCurvaPrimrResponse(Integer cIdtfdUnic,
                                          String cTickerIndcd,
                                          BigDecimal vPrecoLiqdc,
                                          BigDecimal vPrecoMed,
                                          BigDecimal vPrecoUlt,
                                          Integer cDiaVcto,
                                          LocalDate dLiqdcFincr,
                                          String cTickerBberg,
                                          String cFormaLiqdc,
                                          LocalDate dVctoContr,
                                          LocalDate dUltNegoc,
                                          LocalDate dBaseReft) {
}
