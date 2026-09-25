package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "tMtrizCurva", schema = "dbo")
@IdClass(MtrizCurvaEntity.MtrizCurvaId.class)
public class MtrizCurvaEntity {

    @Id
    @Column(name = "cTickerIndcd", length = 50, nullable = false)
    private String tickerIndcd;

    @Id
    @Column(name = "dBaseReft", nullable = false)
    private LocalDate baseReft;

    @Column(name = "cModLyoutCarga", length = 50)
    private String modLyoutCarga;

    @Column(name = "cFormtArq", length = 10)
    private String formtArq;

    @Column(name = "cVrsaoReg")
    private Integer vrsaoReg;

    @Column(name = "dCriacReg")
    private LocalDateTime criacReg;

    @Column(name = "dUltAtulz")
    private LocalDateTime ultAtulz;

    public MtrizCurvaEntity() {}

    public MtrizCurvaEntity(
        String tickerIndcd,
        LocalDate baseReft,
        String modLyoutCarga,
        String formtArq,
        Integer vrsaoReg,
        LocalDateTime criacReg,
        LocalDateTime ultAtulz
    ) {
        this.tickerIndcd = tickerIndcd;
        this.baseReft = baseReft;
        this.modLyoutCarga = modLyoutCarga;
        this.formtArq = formtArq;
        this.vrsaoReg = vrsaoReg;
        this.criacReg = criacReg;
        this.ultAtulz = ultAtulz;
    }

    public static Builder builder() { return new Builder(); }

    public String getTickerIndcd() { return tickerIndcd; }
    public void setTickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; }

    public LocalDate getBaseReft() { return baseReft; }
    public void setBaseReft(LocalDate baseReft) { this.baseReft = baseReft; }

    public String getModLyoutCarga() { return modLyoutCarga; }
    public void setModLyoutCarga(String modLyoutCarga) { this.modLyoutCarga = modLyoutCarga; }

    public String getFormtArq() { return formtArq; }
    public void setFormtArq(String formtArq) { this.formtArq = formtArq; }

    public Integer getVrsaoReg() { return vrsaoReg; }
    public void setVrsaoReg(Integer vrsaoReg) { this.vrsaoReg = vrsaoReg; }

    public LocalDateTime getCriacReg() { return criacReg; }
    public void setCriacReg(LocalDateTime criacReg) { this.criacReg = criacReg; }

    public LocalDateTime getUltAtulz() { return ultAtulz; }
    public void setUltAtulz(LocalDateTime ultAtulz) { this.ultAtulz = ultAtulz; }

    public static class Builder {
        private String tickerIndcd;
        private LocalDate baseReft;
        private String modLyoutCarga;
        private String formtArq;
        private Integer vrsaoReg;
        private LocalDateTime criacReg;
        private LocalDateTime ultAtulz;

        public Builder tickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; return this; }
        public Builder baseReft(LocalDate baseReft) { this.baseReft = baseReft; return this; }
        public Builder modLyoutCarga(String modLyoutCarga) { this.modLyoutCarga = modLyoutCarga; return this; }
        public Builder formtArq(String formtArq) { this.formtArq = formtArq; return this; }
        public Builder vrsaoReg(Integer vrsaoReg) { this.vrsaoReg = vrsaoReg; return this; }
        public Builder criacReg(LocalDateTime criacReg) { this.criacReg = criacReg; return this; }
        public Builder ultAtulz(LocalDateTime ultAtulz) { this.ultAtulz = ultAtulz; return this; }

        public MtrizCurvaEntity build() {
            return new MtrizCurvaEntity(tickerIndcd, baseReft, modLyoutCarga, formtArq, vrsaoReg, criacReg, ultAtulz);
        }
    }

    public static class MtrizCurvaId implements Serializable {
        private String tickerIndcd;
        private LocalDate baseReft;

        public MtrizCurvaId() {}

        public MtrizCurvaId(String tickerIndcd, LocalDate baseReft) {
            this.tickerIndcd = tickerIndcd;
            this.baseReft = baseReft;
        }

        public String getTickerIndcd() { return tickerIndcd; }
        public void setTickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; }

        public LocalDate getBaseReft() { return baseReft; }
        public void setBaseReft(LocalDate baseReft) { this.baseReft = baseReft; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MtrizCurvaId that = (MtrizCurvaId) o;
            return Objects.equals(tickerIndcd, that.tickerIndcd) && Objects.equals(baseReft, that.baseReft);
        }

        @Override
        public int hashCode() { return Objects.hash(tickerIndcd, baseReft); }
    }
}
