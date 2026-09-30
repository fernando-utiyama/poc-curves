package br.com.poc.application.model;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CurvaMercd {
    private String cTickerIndcd;
    private String cFamInsttFincr;
    private String cClassAtivo;
    private String cMoedaNegoc;
    private String cPaisInstt;
    private String cTpoVlr;
    private String cTpoCotac;
    private String cNormaDia;
    private String cTpoJuro;
    private String cCurvaReft;
    private LocalDate dValidAte;
    private String cTickerIdtfdUnic;
    private String cClasfInstt;
    private Integer cConfgIdtfd;
    private String cPprioDado;
    private String rAtivoIndcd;
    private LocalDateTime dCriacReg;
    private LocalDateTime dUltAtulz;
    private String cUsuarAtulz;
    private String cUsuarCalc;
    private String cSitReg;
    private String iPrvdrDados;
    private LocalDate dInicVgcia;
    private LocalDate dBaseReft;
    private String cIndxdAtivo;
    private BigDecimal vFatorMultiAtivo;
}
