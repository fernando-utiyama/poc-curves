package com.poccurves.engine.domain.construcao;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade de domínio que representa um modelo de construção de curva,
 * espelhando db/migration/V5__modelo_curva.sql (tabela modelo_curva).
 * BUILTIN é fixo em Java dentro do motor — nasce sem código-fonte, checksum,
 * importador nem data de importação. GROOVY é importado — esses quatro
 * campos vêm preenchidos desde a criação.
 */
public class ModeloCurva {

    private final UUID id;
    private final String codigo;
    private final String nome;
    private final TipoModelo tipo;
    private EstadoModelo estado;
    private final String codigoFonte;
    private final String checksum;
    private final String importadoPor;
    private final Instant importadoEm;

    private ModeloCurva(
            String codigo,
            String nome,
            TipoModelo tipo,
            String codigoFonte,
            String checksum,
            String importadoPor,
            Instant importadoEm
    ) {
        this(UUID.randomUUID(), codigo, nome, tipo, EstadoModelo.ATIVO, codigoFonte, checksum, importadoPor, importadoEm);
    }

    private ModeloCurva(
            UUID id,
            String codigo,
            String nome,
            TipoModelo tipo,
            EstadoModelo estado,
            String codigoFonte,
            String checksum,
            String importadoPor,
            Instant importadoEm
    ) {
        this.id = id;
        this.codigo = codigo;
        this.nome = nome;
        this.tipo = tipo;
        this.estado = estado;
        this.codigoFonte = codigoFonte;
        this.checksum = checksum;
        this.importadoPor = importadoPor;
        this.importadoEm = importadoEm;
    }

    /**
     * Reidrata a entidade a partir de dados da persistência (DB),
     * preservando o ID original e o estado exato.
     */
    public static ModeloCurva reconstituir(
            UUID id,
            String codigo,
            String nome,
            TipoModelo tipo,
            EstadoModelo estado,
            String codigoFonte,
            String checksum,
            String importadoPor,
            Instant importadoEm
    ) {
        return new ModeloCurva(id, codigo, nome, tipo, estado, codigoFonte, checksum, importadoPor, importadoEm);
    }

    /**
     * Registra um modelo embutido (BUILTIN) — compilado junto com o motor, sem importação.
     *
     * @throws IllegalArgumentException se codigo ou nome forem nulos/em branco
     */
    public static ModeloCurva builtin(String codigo, String nome) {
        validarCodigoNome(codigo, nome);
        return new ModeloCurva(codigo, nome, TipoModelo.BUILTIN, null, null, null, null);
    }

    /**
     * Importa um modelo Groovy (GROOVY), registrando autoria e checksum do script importado.
     *
     * @throws IllegalArgumentException se codigo, nome, codigoFonte, checksum ou importadoPor
     *                                   forem nulos/em branco
     */
    public static ModeloCurva importarGroovy(String codigo, String nome, String codigoFonte, String checksum, String importadoPor) {
        validarCodigoNome(codigo, nome);
        if (codigoFonte == null || codigoFonte.isBlank()) {
            throw new IllegalArgumentException("codigoFonte não pode ser nulo ou vazio");
        }
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalArgumentException("checksum não pode ser nulo ou vazio");
        }
        if (importadoPor == null || importadoPor.isBlank()) {
            throw new IllegalArgumentException("importadoPor não pode ser nulo ou vazio");
        }
        return new ModeloCurva(codigo, nome, TipoModelo.GROOVY, codigoFonte, checksum, importadoPor, Instant.now());
    }

    private static void validarCodigoNome(String codigo, String nome) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("codigo não pode ser nulo ou vazio");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome não pode ser nulo ou vazio");
        }
    }

    /**
     * ATIVO -> DESABILITADO.
     *
     * @throws IllegalStateException se o modelo já estiver DESABILITADO
     */
    public void desabilitar() {
        if (this.estado != EstadoModelo.ATIVO) {
            throw new IllegalStateException("modelo já está " + this.estado + "; esperado ATIVO");
        }
        this.estado = EstadoModelo.DESABILITADO;
    }

    /**
     * DESABILITADO -> ATIVO.
     *
     * @throws IllegalStateException se o modelo já estiver ATIVO
     */
    public void ativar() {
        if (this.estado != EstadoModelo.DESABILITADO) {
            throw new IllegalStateException("modelo já está " + this.estado + "; esperado DESABILITADO");
        }
        this.estado = EstadoModelo.ATIVO;
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

    public TipoModelo tipo() {
        return tipo;
    }

    public EstadoModelo estado() {
        return estado;
    }

    public String codigoFonte() {
        return codigoFonte;
    }

    public String checksum() {
        return checksum;
    }

    public String importadoPor() {
        return importadoPor;
    }

    public Instant importadoEm() {
        return importadoEm;
    }
}
