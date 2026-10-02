package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entidade JPA que representa uma entrada de log relacionada a uma `Tarefa`.
 *
 * Armazena data, código e texto do log, além do vínculo com a tarefa correspondente.
 */
@Getter
@Setter
@Entity
@Table(name = "tLogTrefa")
public class LogTarefaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cIdtfdEntrd")
    private Integer idEntrada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cIdtfdTrefa", nullable = false)
    private TarefaEntity tarefa;

    @Column(name = "dAtaCriac", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "cSitExcuc")
    private Integer codigo;

    @Column(name = "rLogTrefa", nullable = false, columnDefinition = "VARCHAR(MAX)")
    private String descricaoLog;
}
