package com.poccurves.api.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio que representa o cadastro básico de uma curva,
 * espelhando db/migration/V1__definicao_curva.sql (tabela definicao_curva).
 * Código, moeda e modo de origem são imutáveis por design — nenhum método
 * desta classe os altera (é a implementação de "recusa de alteração de
 * código" do backlog: a recusa é a ausência de qualquer mutador).
 */
public class DefinicaoCurva {

    private final UUID id;
    private final String codigo;
    private String nome;
    private final String moeda;
    private final ModoOrigem modoOrigem;
    private final LocalTime horarioLimitePublicacao;
    private EstadoDefinicaoCurva estado;
    private final Instant criadoEm;
    private final String criadoPor;

    private DefinicaoCurva(
            UUID id,
            String codigo,
            String nome,
            String moeda,
            ModoOrigem modoOrigem,
            LocalTime horarioLimitePublicacao,
            EstadoDefinicaoCurva estado,
            Instant criadoEm,
            String criadoPor
    ) {
        this.id = id;
        this.codigo = codigo;
        this.nome = nome;
        this.moeda = moeda;
        this.modoOrigem = modoOrigem;
        this.horarioLimitePublicacao = horarioLimitePublicacao;
        this.estado = estado;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
    }

    /**
     * Cria uma nova definição de curva em estado RASCUNHO.
     *
     * @throws IllegalArgumentException se codigo, nome ou criadoPor forem nulos/em branco,
     *                                   ou se moeda não tiver exatamente 3 caracteres
     * @throws NullPointerException     se moeda, modoOrigem ou horarioLimitePublicacao forem nulos
     */
    public static DefinicaoCurva criar(
            String codigo,
            String nome,
            String moeda,
            ModoOrigem modoOrigem,
            LocalTime horarioLimitePublicacao,
            String criadoPor
    ) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("codigo não pode ser nulo ou vazio");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome não pode ser nulo ou vazio");
        }
        Objects.requireNonNull(moeda, "moeda não pode ser nula");
        if (moeda.length() != 3) {
            throw new IllegalArgumentException("moeda deve ter exatamente 3 caracteres: " + moeda);
        }
        Objects.requireNonNull(modoOrigem, "modoOrigem não pode ser nulo");
        Objects.requireNonNull(horarioLimitePublicacao, "horarioLimitePublicacao não pode ser nulo");
        if (criadoPor == null || criadoPor.isBlank()) {
            throw new IllegalArgumentException("criadoPor não pode ser nulo ou vazio");
        }

        return new DefinicaoCurva(
                UUID.randomUUID(),
                codigo,
                nome,
                moeda,
                modoOrigem,
                horarioLimitePublicacao,
                EstadoDefinicaoCurva.RASCUNHO,
                Instant.now(),
                criadoPor
        );
    }

    /**
     * Reconstitui uma definição de curva a partir dos dados persistidos no banco.
     */
    public static DefinicaoCurva reconstituir(
            UUID id,
            String codigo,
            String nome,
            String moeda,
            ModoOrigem modoOrigem,
            LocalTime horarioLimitePublicacao,
            EstadoDefinicaoCurva estado,
            Instant criadoEm,
            String criadoPor
    ) {
        return new DefinicaoCurva(
                id, codigo, nome, moeda, modoOrigem, horarioLimitePublicacao, estado, criadoEm, criadoPor
        );
    }


    /**
     * Renomeia a definição. Nome não é imutável (só código, moeda e modo de origem são).
     *
     * @throws IllegalArgumentException se novoNome for nulo ou em branco
     */
    public void renomear(String novoNome) {
        if (novoNome == null || novoNome.isBlank()) {
            throw new IllegalArgumentException("novoNome não pode ser nulo ou vazio");
        }
        this.nome = novoNome;
    }

    /**
     * Ativa a definição: RASCUNHO -> ATIVA.
     *
     * @throws IllegalStateException se o estado atual não for RASCUNHO
     */
    public void ativar() {
        if (this.estado != EstadoDefinicaoCurva.RASCUNHO) {
            throw new IllegalStateException(
                    "não é possível ativar a partir do estado " + this.estado + "; esperado RASCUNHO");
        }
        this.estado = EstadoDefinicaoCurva.ATIVA;
    }

    /**
     * Aposenta a definição: ATIVA -> APOSENTADA.
     *
     * @throws IllegalStateException se o estado atual não for ATIVA
     */
    public void aposentar() {
        if (this.estado != EstadoDefinicaoCurva.ATIVA) {
            throw new IllegalStateException(
                    "não é possível aposentar a partir do estado " + this.estado + "; esperado ATIVA");
        }
        this.estado = EstadoDefinicaoCurva.APOSENTADA;
    }

    public UUID id() {
        return id;
    }

    public String codigo() {
        return codigo;
    }

    public String nome() {
        return nome;
    }

    public String moeda() {
        return moeda;
    }

    public ModoOrigem modoOrigem() {
        return modoOrigem;
    }

    public LocalTime horarioLimitePublicacao() {
        return horarioLimitePublicacao;
    }

    public EstadoDefinicaoCurva estado() {
        return estado;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public String criadoPor() {
        return criadoPor;
    }
}
