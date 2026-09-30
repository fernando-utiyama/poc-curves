package br.com.poc.application.dto;

import java.time.LocalDate;

/**
 * Contrato comum dos campos mutáveis compartilhados pelos DTOs de criação e atualização da curva,
 * permitindo que o mapper aplique-os ao domínio a partir de um único método.
 */
public interface BloombergCurvaPrimrMutableFields {

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
