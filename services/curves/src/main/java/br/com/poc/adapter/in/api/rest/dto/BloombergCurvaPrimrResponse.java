package br.com.poc.adapter.in.api.rest.dto;

import java.time.LocalDate;

public record BloombergCurvaPrimrResponse(Integer cldtfdUnic,
                                          String cTickerIndcd,
                                          Double vPrecoLiqdc,
                                          Double vPrecoMed,
                                          Double vPrecoUlt,
                                          Integer cDiaVcto,
                                          LocalDate dLiqdcFincr,
                                          String cTickerBberg,
                                          String cFormaLiqdc,
                                          LocalDate dVctoContr,
                                          LocalDate dUltNegoc,
                                          LocalDate dBaseReft) {
}
