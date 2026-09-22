package br.com.poc.starter.srv.hex.adapter.out.persistence.inmemory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "AnbimaCurveRaw", schema = "mkt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnbimaCurveRawEntity extends BaseEntity {

    private String ticker;

    @Column(name = "ref_date")
    private LocalDate refDate;

    private Double vertices;

    private Double valor;
}
