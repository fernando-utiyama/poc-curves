package br.com.poc.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vértice bruto da ANBIMA (tAnbmaCurvaPrimr). */
@Entity
@Table(name = "tAnbmaCurvaPrimr", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "idtfdUnic")
public class AnbmaCurvaPrimrEntity {

    @Id
    @Column(name = "cIdtfdUnic", nullable = false)
    private Integer idtfdUnic;

    @Column(name = "cTickerIndcd", nullable = false, length = 50)
    private String tickerIndcd;

    @Column(name = "dBaseReft")
    private LocalDate dataBaseReft;

    @Column(name = "vPrecoTx", precision = 28, scale = 12)
    private BigDecimal precoTx;

    @Column(name = "vVertcCurva", precision = 28, scale = 12)
    private BigDecimal vertcCurva;
}
