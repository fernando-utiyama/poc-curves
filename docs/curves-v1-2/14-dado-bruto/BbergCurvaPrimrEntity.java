package br.com.poc.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vértice bruto da Bloomberg (tBbergCurvaPrimr). */
@Entity
@Table(name = "tBbergCurvaPrimr", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "idtfdUnic")
public class BbergCurvaPrimrEntity {

    @Id
    @Column(name = "cIdtfdUnic", nullable = false)
    private Integer idtfdUnic;

    @Column(name = "cTickerIndcd", nullable = false, length = 50)
    private String tickerIndcd;

    @Column(name = "vPrecoLiqdc", precision = 28, scale = 12)
    private BigDecimal precoLiqdc;

    @Column(name = "vPrecoMed", precision = 28, scale = 12)
    private BigDecimal precoMed;

    @Column(name = "vPrecoUlt", precision = 28, scale = 12)
    private BigDecimal precoUlt;

    @Column(name = "cDiaVcto")
    private Integer diaVcto;

    @Column(name = "dLiqdcFincr")
    private LocalDate dataLiqdcFincr;

    @Column(name = "cTickerBberg", length = 50)
    private String tickerBberg;

    @Column(name = "cFormaLiqdc", length = 20)
    private String formaLiqdc;

    @Column(name = "dVctoContr")
    private LocalDate dataVctoContr;

    @Column(name = "dUltNegoc")
    private LocalDate dataUltNegoc;

    @Column(name = "dBaseReft")
    private LocalDate dataBaseReft;
}
