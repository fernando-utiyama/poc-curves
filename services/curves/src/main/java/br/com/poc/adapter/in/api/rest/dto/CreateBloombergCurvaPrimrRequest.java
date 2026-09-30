package br.com.poc.adapter.in.api.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateBloombergCurvaPrimrRequest(@NotBlank @Size(max = 50) String cTickerIndcd,
                                               Double vPrecoLiqdc,
                                               Double vPrecoMed,
                                               Double vPrecoUlt,
                                               Integer cDiaVcto,
                                               LocalDate dLiqdcFincr,
                                               @Size(max = 50) String cTickerBberg,
                                               @Size(max = 20) String cFormaLiqdc,
                                               LocalDate dVctoContr,
                                               LocalDate dUltNegoc,
                                               LocalDate dBaseReft) {
}
