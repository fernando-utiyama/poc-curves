package br.com.poc.application.model;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BloombergCurvaPrimr {

    private Integer cldtfdUnic;
    private String cTickerIndcd;
    private Double vPrecoLiqdc;
    private Double vPrecoMed;
    private Double vPrecoUlt;
    private Integer cDiaVcto;
    private LocalDate dLiqdcFincr;
    private String cTickerBberg;
    private String cFormaLiqdc;
    private LocalDate dVctoContr;
    private LocalDate dUltNegoc;
    private LocalDate dBaseReft;
}
