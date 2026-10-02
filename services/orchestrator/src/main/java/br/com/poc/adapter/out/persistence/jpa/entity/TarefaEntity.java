package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Entidade JPA que representa a tabela `Tarefa` no banco de dados.
 *
 * Contém os campos persistidos e relacionamentos com parâmetros e logs.
 */
@Getter
@Setter
@Entity
@Table(name = "tTrefaAgnda")
public class TarefaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cIdtfdTrefa")
    private Integer id;

    @Column(name = "cAcaoOperSist", nullable = false, length = 1024)
    private String acaoOperSistema;

    @Column(name = "cRegraAgnda", length = 15)
    private String regraAgendamento;

    @Column(name = "cRegraIntvl", length = 20)
    private String regraIntervalo;

    @Column(name = "cSit", length = 30)
    private String situacao;

    @Column(name = "iParmTrefa", nullable = false, length = 255)
    private String identificadorParametro;

    @Column(name = "rTrefa", nullable = false, length = 1024)
    private String descricao;

    @OneToMany(mappedBy = "tarefa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ParametroTarefaEntity> parametros = new LinkedHashSet<>();

    @OneToMany(mappedBy = "tarefa", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LogTarefaEntity> logs = new ArrayList<>();
}
