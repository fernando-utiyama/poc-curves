package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "tConfgCurva", schema = "dbo")
public class ConfgCurvaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cldtfdConfg", nullable = false)
    private Integer idtfdConfg;

    @Column(name = "cTickerIndcd", length = 50)
    private String tickerIndcd;

    @Column(name = "cMotorCalc", length = 1024)
    private String motorCalc;

    @Column(name = "cRotnaCalc", length = 1024)
    private String rotnaCalc;

    @Column(name = "cModDado", length = 1024)
    private String modDado;

    @Column(name = "cLingSist", length = 50)
    private String lingSist;

    @Column(name = "cVrsaoReg")
    private Integer vrsaoReg;

    @Column(name = "dInicVgcia")
    private LocalDate inicVgcia;

    @Column(name = "dValidAte")
    private LocalDate validAte;

    public ConfgCurvaEntity() {}

    public Integer getIdtfdConfg() { return idtfdConfg; }
    public void setIdtfdConfg(Integer idtfdConfg) { this.idtfdConfg = idtfdConfg; }

    public String getTickerIndcd() { return tickerIndcd; }
    public void setTickerIndcd(String tickerIndcd) { this.tickerIndcd = tickerIndcd; }

    public String getMotorCalc() { return motorCalc; }
    public void setMotorCalc(String motorCalc) { this.motorCalc = motorCalc; }

    public String getRotnaCalc() { return rotnaCalc; }
    public void setRotnaCalc(String rotnaCalc) { this.rotnaCalc = rotnaCalc; }

    public String getModDado() { return modDado; }
    public void setModDado(String modDado) { this.modDado = modDado; }

    public String getLingSist() { return lingSist; }
    public void setLingSist(String lingSist) { this.lingSist = lingSist; }

    public Integer getVrsaoReg() { return vrsaoReg; }
    public void setVrsaoReg(Integer vrsaoReg) { this.vrsaoReg = vrsaoReg; }

    public LocalDate getInicVgcia() { return inicVgcia; }
    public void setInicVgcia(LocalDate inicVgcia) { this.inicVgcia = inicVgcia; }

    public LocalDate getValidAte() { return validAte; }
    public void setValidAte(LocalDate validAte) { this.validAte = validAte; }
}
