package br.com.poc.application.model;

import java.math.BigDecimal;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BloombergCurvaPrimr {

    private Integer cldtfdUnic;
    private String cTickerIndcd;
    private BigDecimal vPrecoLiqdc;
    private BigDecimal vPrecoMed;
    private BigDecimal vPrecoUlt;
    private Integer cDiaVcto;
    private LocalDate dLiqdcFincr;
    private String cTickerBberg;
    private String cFormaLiqdc;
    private LocalDate dVctoContr;
    private LocalDate dUltNegoc;
    private LocalDate dBaseReft;
}
