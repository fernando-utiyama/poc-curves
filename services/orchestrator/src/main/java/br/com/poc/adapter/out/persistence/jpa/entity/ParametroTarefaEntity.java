package br.com.poc.adapter.out.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Entidade JPA que representa parâmetros associados a uma `Tarefa`.
 *
 * Armazena nome, valor e tipo do parâmetro além do vínculo para a entidade da tarefa.
 */
@Getter
@Setter
@Entity
@Table(name = "tParmTrefa")
public class ParametroTarefaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cIdtfdParm")
    private Integer idParametro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cIdtfdTrefa", nullable = false)
    private TarefaEntity tarefa;

    @Column(name = "cCategParmConfg", nullable = false, length = 255)
    private String categoriaConfiguracao;

    @Column(name = "rParmTrefa", nullable = false, columnDefinition = "VARCHAR(MAX)")
    private String valorParametro;

    @Column(name = "iParmTrefa", nullable = false, length = 255)
    private String identificadorParametro;
}
