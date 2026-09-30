package br.com.poc.application.dto;

import java.time.LocalDate;

public interface BloombergCurvaPrimrUpdatableRequest {

    Double vPrecoLiqdc();

    Double vPrecoMed();

    Double vPrecoUlt();

    Integer cDiaVcto();

    LocalDate dLiqdcFincr();

    String cTickerBberg();

    String cFormaLiqdc();

    LocalDate dVctoContr();

    LocalDate dUltNegoc();

    LocalDate dBaseReft();
}
