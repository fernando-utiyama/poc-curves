package br.com.poc.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dado bruto da B3. Campos com o nome da coluna sem o prefixo (c/d/v), como na ANBIMA e na Bloomberg;
 * colunas `d...` ganham "data" na frente. A ordem dos campos é a do construtor que o adaptador usa.
 */
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
