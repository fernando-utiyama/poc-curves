package br.com.poc.adapter.out.persistence.entity;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tCurvaMercd", schema = "dbo")
public class CurvaMercdEntity {

    @Id
    @Column(name = "cTickerIndcd", nullable = false, length = 50)
    private String cTickerIndcd;

    @Column(name = "cFamlInsttFincr", length = 50)
    private String cFamlInsttFincr;

    @Column(name = "cClassAtivo", length = 50)
    private String cClassAtivo;

    @Column(name = "cMoedaNegoc", length = 1024)
    private String cMoedaNegoc;

    @Column(name = "cPaisInstt", length = 20)
    private String cPaisInstt;

    @Column(name = "cTpoVlr", length = 30)
    private String cTpoVlr;

    @Column(name = "cTpoCotac", length = 30)
    private String cTpoCotac;

    @Column(name = "cNormaDia", length = 20)
    private String cNormaDia;

    @Column(name = "cTpoJuro", length = 20)
    private String cTpoJuro;

    @Column(name = "cCurvaReft", length = 50)
    private String cCurvaReft;

    @Column(name = "dValidAte")
    private LocalDate dValidAte;

    @Column(name = "cTickerIdtfdUnic", length = 50)
    private String cTickerIdtfdUnic;

    @Column(name = "cClasfInstt", length = 50)
    private String cClasfInstt;

    @Column(name = "cConfgIdtfd")
    private Integer cConfgIdtfd;

    @Column(name = "cPprioDado", length = 50)
    private String cPprioDado;

    @Column(name = "rAtivoIndcd", length = 1024)
    private String rAtivoIndcd;

    @Column(name = "dCriacReg")
    private LocalDateTime dCriacReg;

    @Column(name = "dUltAtulz")
    private LocalDateTime dUltAtulz;

    @Column(name = "cUsuarAtulz", length = 100)
    private String cUsuarAtulz;

    @Column(name = "cUsuarCalc", length = 100)
    private String cUsuarCalc;

    @Column(name = "cSitReg", length = 20)
    private String cSitReg;

    @Column(name = "iPrvdrDados", length = 1024)
    private String iPrvdrDados;

    @Column(name = "dInicVgcia")
    private LocalDate dInicVgcia;

    @Column(name = "dBaseReft")
    private LocalDate dBaseReft;

    @Column(name = "cIndxdAtivo", length = 50)
    private String cIndxdAtivo;

    @Column(name = "vFatorMultiAtivo", precision = 28, scale = 12)
    private BigDecimal vFatorMultiAtivo;
}
