package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "B3CurveRaw", schema = "mkt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B3CurveRawEntity extends BaseEntity {

    private String ticker;

    @Column(name = "ref_date")
    private LocalDate refDate;

    @Column(name = "dias_corridos")
    private Integer diasCorridos;

    @Column(name = "dias_uteis")
    private Integer diasUteis;

    private Double valor;
}
