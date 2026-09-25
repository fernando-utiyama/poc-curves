package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "tAnbmaCurvaPrimr", schema = "dbo")
@AttributeOverride(name = "uuid", column = @Column(name = "cldtfdUnic", nullable = false))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnbimaCurveRawEntity extends BaseEntity {

    @Column(name = "cTickerIndcd")
    private String cTickerIndcd;

    @Column(name = "dBaseReft")
    private LocalDate dBaseReft;

    @Column(name = "vPrecoTx")
    private Double vPrecoTx;

    @Column(name = "vVertcCurva")
    private Double vVertcCurva;
}
