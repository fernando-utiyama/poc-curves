package br.com.poc.application.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateBloombergCurvaPrimrRequest(Double vPrecoLiqdc,
                                               Double vPrecoMed,
                                               Double vPrecoUlt,
                                               Integer cDiaVcto,
                                               LocalDate dLiqdcFincr,
                                               @Size(max = 50) String cTickerBberg,
                                               @Size(max = 20) String cFormaLiqdc,
                                               LocalDate dVctoContr,
                                               LocalDate dUltNegoc,
                                               LocalDate dBaseReft) implements BloombergCurvaPrimrMutableFields {
}
