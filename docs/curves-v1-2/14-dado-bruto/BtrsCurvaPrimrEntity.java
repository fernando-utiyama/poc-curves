package br.com.poc.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vértice bruto da B3 (tBtrsCurvaPrimr). */
@Entity
@Table(name = "tBtrsCurvaPrimr", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "idtfdUnic")
public class BtrsCurvaPrimrEntity {

    @Id
    @Column(name = "cIdtfdUnic", nullable = false)
    private Integer idtfdUnic;

    @Column(name = "cTickerIndcd", nullable = false, length = 50)
    private String tickerIndcd;

    @Column(name = "cDiaCorri")
    private Integer diaCorri;

    @Column(name = "cDiaUtil")
    private Integer diaUtil;

    @Column(name = "dBaseReft")
    private LocalDate dataBaseReft;

    @Column(name = "vFatorAcum", precision = 28, scale = 16)
    private BigDecimal fatorAcum;

    @Column(name = "vPrecoTx", precision = 28, scale = 12)
    private BigDecimal precoTx;

    @Column(name = "vFatorDia", precision = 28, scale = 16)
    private BigDecimal fatorDia;
}
