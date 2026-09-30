package br.com.poc.adapter.out.persistence.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@lombok.EqualsAndHashCode(of = "cldtfdUnic")
@Entity
@Table(name = "tBbergCurvaPrimr", schema = "dbo")
public class BloombergCurvaPrimrEntity {
    @Id
    @Column(name = "cldtfdUnic", nullable = false)
    private Integer cldtfdUnic;

    @Column(name = "cTickerIndcd", nullable = false, length = 50)
    private String cTickerIndcd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cTickerIndcd", referencedColumnName = "cTickerIndcd", insertable = false, updatable = false)
    private CurvaMercdEntity curvaMercd;

    @Column(name = "vPrecoLiqdc", precision = 28, scale = 12)
    private BigDecimal vPrecoLiqdc;

    @Column(name = "vPrecoMed", precision = 28, scale = 12)
    private BigDecimal vPrecoMed;

    @Column(name = "vPrecoUlt", precision = 28, scale = 12)
    private BigDecimal vPrecoUlt;

    @Column(name = "cDiaVcto")
    private Integer cDiaVcto;

    @Column(name = "dLiqdcFincr")
    private LocalDate dLiqdcFincr;

    @Column(name = "cTickerBberg", length = 50)
    private String cTickerBberg;

    @Column(name = "cFormaLiqdc", length = 20)
    private String cFormaLiqdc;

    @Column(name = "dVctoContr")
    private LocalDate dVctoContr;

    @Column(name = "dUltNegoc")
    private LocalDate dUltNegoc;

    @Column(name = "dBaseReft")
    private LocalDate dBaseReft;
}
