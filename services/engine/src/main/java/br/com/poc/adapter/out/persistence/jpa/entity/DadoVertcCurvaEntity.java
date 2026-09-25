package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "tDadoVertcCurva", schema = "dbo")
@IdClass(DadoVertcCurvaEntity.DadoVertcCurvaId.class)
public class DadoVertcCurvaEntity {

    @Id
    @Column(name = "dBaseReft", nullable = false)
    private LocalDate baseReft;

    @Id
    @Column(name = "cTickerIndcd", length = 50, nullable = false)
    private String tickerIndcd;

    @Id
    @Column(name = "dtVerticeReferencia", nullable = false)
    private LocalDate verticeReferencia;

    @Column(name = "cDiaUtil")
    private Integer diaUtil;

    @Column(name = "vFatorDia", precision = 28, scale = 12)
    private BigDecimal fatorDia;

    @Column(name = "vFatorAcum", precision = 28, scale = 16)
    private BigDecimal fatorAcum;

    @Column(name = "vPrecoTx", precision = 28, scale = 12)
    private BigDecimal precoTx;

    @Column(name = "cQtdDiaPer")
    private Integer qtdDiaPer;

    @Column(name = "cQtdDiaReft")
    private Integer qtdDiaReft;

    public DadoVertcCurvaEntity() {}

    public DadoVertcCurvaEntity(
        LocalDate baseReft,
        String tickerIndcd,
        LocalDate verticeReferencia,
        Integer diaUtil,
        BigDecimal fatorDia,
        BigDecimal fatorAcum,
        BigDecimal precoTx,
        Integer qtdDiaPer,
        Integer qtdDiaReft
    ) {
        this.baseReft = baseReft;
        this.tickerIndcd = tickerIndcd;
        this.verticeReferencia = verticeReferencia;
        this.diaUtil = diaUtil;
        this.fatorDia = fatorDia;
        this.fatorAcum = fatorAcum;
        this.precoTx = precoTx;
        this.qtdDiaPer = qtdDiaPer;
        this.qtdDiaReft = qtdDiaReft;
    }

    public static Builder builder() { return new Builder(); }

    public LocalDate getBaseReft() { return baseReft; }
    public void setBaseReft(LocalDate baseReft) { this.baseReft = baseReft; }

    public String getTickerIndcd() { return tickerIndcd; }
    public void setTickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; }

    public LocalDate getVerticeReferencia() { return verticeReferencia; }
    public void setVerticeReferencia(LocalDate verticeReferencia) { this.verticeReferencia = verticeReferencia; }

    public Integer getDiaUtil() { return diaUtil; }
    public void setDiaUtil(Integer diaUtil) { this.diaUtil = diaUtil; }

    public BigDecimal getFatorDia() { return fatorDia; }
    public void setFatorDia(BigDecimal fatorDia) { this.fatorDia = fatorDia; }

    public BigDecimal getFatorAcum() { return fatorAcum; }
    public void setFatorAcum(BigDecimal fatorAcum) { this.fatorAcum = fatorAcum; }

    public BigDecimal getPrecoTx() { return precoTx; }
    public void setPrecoTx(BigDecimal precoTx) { this.precoTx = precoTx; }

    public Integer getQtdDiaPer() { return qtdDiaPer; }
    public void setQtdDiaPer(Integer qtdDiaPer) { this.qtdDiaPer = qtdDiaPer; }

    public Integer getQtdDiaReft() { return qtdDiaReft; }
    public void setQtdDiaReft(Integer qtdDiaReft) { this.qtdDiaReft = qtdDiaReft; }

    public static class Builder {
        private LocalDate baseReft;
        private String tickerIndcd;
        private LocalDate verticeReferencia;
        private Integer diaUtil;
        private BigDecimal fatorDia;
        private BigDecimal fatorAcum;
        private BigDecimal precoTx;
        private Integer qtdDiaPer;
        private Integer qtdDiaReft;

        public Builder baseReft(LocalDate baseReft) { this.baseReft = baseReft; return this; }
        public Builder tickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; return this; }
        public Builder verticeReferencia(LocalDate verticeReferencia) { this.verticeReferencia = verticeReferencia; return this; }
        public Builder diaUtil(Integer diaUtil) { this.diaUtil = diaUtil; return this; }
        public Builder fatorDia(BigDecimal fatorDia) { this.fatorDia = fatorDia; return this; }
        public Builder fatorAcum(BigDecimal fatorAcum) { this.fatorAcum = fatorAcum; return this; }
        public Builder precoTx(BigDecimal precoTx) { this.precoTx = precoTx; return this; }
        public Builder qtdDiaPer(Integer qtdDiaPer) { this.qtdDiaPer = qtdDiaPer; return this; }
        public Builder qtdDiaReft(Integer qtdDiaReft) { this.qtdDiaReft = qtdDiaReft; return this; }

        public DadoVertcCurvaEntity build() {
            return new DadoVertcCurvaEntity(
                baseReft, tickerIndcd, verticeReferencia, diaUtil,
                fatorDia, fatorAcum, precoTx, qtdDiaPer, qtdDiaReft
            );
        }
    }

    public static class DadoVertcCurvaId implements Serializable {
        private LocalDate baseReft;
        private String tickerIndcd;
        private LocalDate verticeReferencia;

        public DadoVertcCurvaId() {}

        public DadoVertcCurvaId(LocalDate baseReft, String tickerIndcd, LocalDate verticeReferencia) {
            this.baseReft = baseReft;
            this.tickerIndcd = tickerIndcd;
            this.verticeReferencia = verticeReferencia;
        }

        public LocalDate getBaseReft() { return baseReft; }
        public void setBaseReft(LocalDate baseReft) { this.baseReft = baseReft; }

        public String getTickerIndcd() { return tickerIndcd; }
        public void setTickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; }

        public LocalDate getVerticeReferencia() { return verticeReferencia; }
        public void setVerticeReferencia(LocalDate verticeReferencia) { this.verticeReferencia = verticeReferencia; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DadoVertcCurvaId that = (DadoVertcCurvaId) o;
            return Objects.equals(baseReft, that.baseReft) &&
                Objects.equals(tickerIndcd, that.tickerIndcd) &&
                Objects.equals(verticeReferencia, that.verticeReferencia);
        }

        @Override
        public int hashCode() { return Objects.hash(baseReft, tickerIndcd, verticeReferencia); }
    }
}
