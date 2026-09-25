package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "tParmConfgCurva", schema = "dbo")
@IdClass(ParmConfgCurvaEntity.ParmConfgCurvaId.class)
public class ParmConfgCurvaEntity {

    @Id
    @Column(name = "cldtfdConfg", nullable = false)
    private Integer idtfdConfg;

    @Id
    @Column(name = "cConfgIdtfd", length = 50, nullable = false)
    private String confgIdtfd;

    @Column(name = "vPrecoTx", precision = 28, scale = 12)
    private BigDecimal precoTx;

    @Column(name = "iPrvdrDados", length = 50)
    private String prvdrDados;

    @Column(name = "cTpoInstt", length = 50)
    private String tpoInstt;

    public ParmConfgCurvaEntity() {}

    public Integer getIdtfdConfg() { return idtfdConfg; }
    public void setIdtfdConfg(Integer idtfdConfg) { this.idtfdConfg = idtfdConfg; }

    public String getConfgIdtfd() { return confgIdtfd; }
    public void setConfgIdtfd(String confgIdtfd) { this.confgIdtfd = confgIdtfd; }

    public BigDecimal getPrecoTx() { return precoTx; }
    public void setPrecoTx(BigDecimal precoTx) { this.precoTx = precoTx; }

    public String getPrvdrDados() { return prvdrDados; }
    public void setPrvdrDados(String prvdrDados) { this.prvdrDados = prvdrDados; }

    public String getTpoInstt() { return tpoInstt; }
    public void setTpoInstt(String tpoInstt) { this.tpoInstt = tpoInstt; }

    public static class ParmConfgCurvaId implements Serializable {
        private Integer idtfdConfg;
        private String confgIdtfd;

        public ParmConfgCurvaId() {}

        public ParmConfgCurvaId(Integer idtfdConfg, String confgIdtfd) {
            this.idtfdConfg = idtfdConfg;
            this.confgIdtfd = confgIdtfd;
        }

        public Integer getIdtfdConfg() { return idtfdConfg; }
        public void setIdtfdConfg(Integer idtfdConfg) { this.idtfdConfg = idtfdConfg; }

        public String getConfgIdtfd() { return confgIdtfd; }
        public void setConfgIdtfd(String confgIdtfd) { this.confgIdtfd = confgIdtfd; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParmConfgCurvaId that = (ParmConfgCurvaId) o;
            return Objects.equals(idtfdConfg, that.idtfdConfg) && Objects.equals(confgIdtfd, that.confgIdtfd);
        }

        @Override
        public int hashCode() { return Objects.hash(idtfdConfg, confgIdtfd); }
    }
}
