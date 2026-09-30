package br.com.poc.application.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateBloombergCurvaPrimrRequest(@NotBlank @Size(max = 50) String cTickerIndcd,
                                               BigDecimal vPrecoLiqdc,
                                               BigDecimal vPrecoMed,
                                               BigDecimal vPrecoUlt,
                                               Integer cDiaVcto,
                                               LocalDate dLiqdcFincr,
                                               @Size(max = 50) String cTickerBberg,
                                               @Size(max = 20) String cFormaLiqdc,
                                               LocalDate dVctoContr,
                                               LocalDate dUltNegoc,
                                               LocalDate dBaseReft) implements BloombergCurvaPrimrMutableFields {
}
