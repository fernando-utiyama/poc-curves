package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "tDadoCurva", schema = "dbo")
@IdClass(DadoCurvaEntity.DadoCurvaId.class)
public class DadoCurvaEntity {

    @Id
    @Column(name = "dBaseReft", nullable = false)
    private LocalDate baseReft;

    @Id
    @Column(name = "cTickerIndcd", length = 50, nullable = false)
    private String tickerIndcd;

    @Id
    @Column(name = "dtVerticeReferencia", nullable = false)
    private LocalDate verticeReferencia;

    @Column(name = "vPrecoTx", precision = 28, scale = 12)
    private BigDecimal precoTx;

    @Column(name = "cDiaUtil")
    private Integer diaUtil;

    @Column(name = "cQtdDiaReft")
    private Integer qtdDiaReft;

    @Column(name = "vDiaFator", precision = 28, scale = 12)
    private BigDecimal diaFator;

    @Column(name = "vFatorCalc", precision = 28, scale = 16)
    private BigDecimal fatorCalc;

    public DadoCurvaEntity() {}

    public LocalDate getBaseReft() { return baseReft; }
    public void setBaseReft(LocalDate baseReft) { this.baseReft = baseReft; }

    public String getTickerIndcd() { return tickerIndcd; }
    public void setTickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; }

    public LocalDate getVerticeReferencia() { return verticeReferencia; }
    public void setVerticeReferencia(LocalDate verticeReferencia) { this.verticeReferencia = verticeReferencia; }

    public BigDecimal getPrecoTx() { return precoTx; }
    public void setPrecoTx(BigDecimal precoTx) { this.precoTx = precoTx; }

    public Integer getDiaUtil() { return diaUtil; }
    public void setDiaUtil(Integer diaUtil) { this.diaUtil = diaUtil; }

    public Integer getQtdDiaReft() { return qtdDiaReft; }
    public void setQtdDiaReft(Integer qtdDiaReft) { this.qtdDiaReft = qtdDiaReft; }

    public BigDecimal getDiaFator() { return diaFator; }
    public void setDiaFator(BigDecimal diaFator) { this.diaFator = diaFator; }

    public BigDecimal getFatorCalc() { return fatorCalc; }
    public void setFatorCalc(BigDecimal fatorCalc) { this.fatorCalc = fatorCalc; }

    public static class DadoCurvaId implements Serializable {
        private LocalDate baseReft;
        private String tickerIndcd;
        private LocalDate verticeReferencia;

        public DadoCurvaId() {}

        public DadoCurvaId(LocalDate baseReft, String tickerIndcd, LocalDate verticeReferencia) {
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
            DadoCurvaId that = (DadoCurvaId) o;
            return Objects.equals(baseReft, that.baseReft) &&
                Objects.equals(tickerIndcd, that.tickerIndcd) &&
                Objects.equals(verticeReferencia, that.verticeReferencia);
        }

        @Override
        public int hashCode() { return Objects.hash(baseReft, tickerIndcd, verticeReferencia); }
    }
}
