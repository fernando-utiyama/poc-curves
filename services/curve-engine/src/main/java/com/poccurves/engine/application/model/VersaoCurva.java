package com.poccurves.engine.application.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio rica que representa uma versão de curva.
 * Espelha a tabela versao_curva (db/migration/V4__versao_curva.sql).
 * <p>
 * O modelo usado não é rastreado aqui — {@code versao_curva} não tem coluna
 * {@code modelo_curva_id} (só {@code versao_definicao_curva} e {@code procedencia_curva}
 * têm, adicionada em V5). Quem quer saber o modelo apontado hoje consulta
 * {@code versao_definicao_curva}; quem quer saber o modelo que produziu esta versão
 * específica consulta {@link ProcedenciaCurva#modeloCurvaId()}.
 */
public class VersaoCurva {

    private final UUID id;
    private final UUID definicaoCurvaId;
    private final UUID versaoDefinicaoCurvaId;
    private final LocalDate dataReferencia;
    private final MomentoCurva momentoCurva;
    private final int numeroVersao;
    private final OrigemVersao origemVersao;
    private EstadoVersaoCurva estado;
    private final UUID execucaoCurvaId;
    private Instant publicadoEm;

    private VersaoCurva(
            UUID id,
            UUID definicaoCurvaId,
            UUID versaoDefinicaoCurvaId,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            int numeroVersao,
            OrigemVersao origemVersao,
            EstadoVersaoCurva estado,
            UUID execucaoCurvaId,
            Instant publicadoEm
    ) {
        this.id = id;
        this.definicaoCurvaId = definicaoCurvaId;
        this.versaoDefinicaoCurvaId = versaoDefinicaoCurvaId;
        this.dataReferencia = dataReferencia;
        this.momentoCurva = momentoCurva;
        this.numeroVersao = numeroVersao;
        this.origemVersao = origemVersao;
        this.estado = estado;
        this.execucaoCurvaId = execucaoCurvaId;
        this.publicadoEm = publicadoEm;
    }

    public static VersaoCurva criar(
            UUID definicaoCurvaId,
            UUID versaoDefinicaoCurvaId,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            int numeroVersao,
            OrigemVersao origemVersao,
            UUID execucaoCurvaId
    ) {
        Objects.requireNonNull(definicaoCurvaId, "definicaoCurvaId não pode ser nulo");
        Objects.requireNonNull(versaoDefinicaoCurvaId, "versaoDefinicaoCurvaId não pode ser nulo");
        Objects.requireNonNull(dataReferencia, "dataReferencia não pode ser nula");
        Objects.requireNonNull(momentoCurva, "momentoCurva não pode ser nulo");
        Objects.requireNonNull(origemVersao, "origemVersao não pode ser nulo");
        Objects.requireNonNull(execucaoCurvaId, "execucaoCurvaId não pode ser nulo");

        if (numeroVersao <= 0) {
            throw new IllegalArgumentException("numeroVersao deve ser maior que zero");
        }

        return new VersaoCurva(
                UUID.randomUUID(),
                definicaoCurvaId,
                versaoDefinicaoCurvaId,
                dataReferencia,
                momentoCurva,
                numeroVersao,
                origemVersao,
                EstadoVersaoCurva.EM_VALIDACAO,
                execucaoCurvaId,
                null
        );
    }

    public void publicar() {
        if (this.estado != EstadoVersaoCurva.EM_VALIDACAO) {
            throw new IllegalStateException("Para publicar, a versão deve estar EM_VALIDACAO");
        }
        this.estado = EstadoVersaoCurva.PUBLICADA;
        this.publicadoEm = Instant.now();
    }

    public void reprovar() {
        if (this.estado != EstadoVersaoCurva.EM_VALIDACAO) {
            throw new IllegalStateException("Para reprovar, a versão deve estar EM_VALIDACAO");
        }
        this.estado = EstadoVersaoCurva.REPROVADA;
    }

    public void substituir() {
        if (this.estado != EstadoVersaoCurva.PUBLICADA) {
            throw new IllegalStateException("Para substituir, a versão deve estar PUBLICADA");
        }
        this.estado = EstadoVersaoCurva.SUBSTITUIDA;
    }

    public static VersaoCurva reconstituir(
            UUID id,
            UUID definicaoCurvaId,
            UUID versaoDefinicaoCurvaId,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            int numeroVersao,
            OrigemVersao origemVersao,
            EstadoVersaoCurva estado,
            UUID execucaoCurvaId,
            Instant publicadoEm
    ) {
        return new VersaoCurva(
                id,
                definicaoCurvaId,
                versaoDefinicaoCurvaId,
                dataReferencia,
                momentoCurva,
                numeroVersao,
                origemVersao,
                estado,
                execucaoCurvaId,
                publicadoEm
        );
    }

    public UUID id() {
        return id;
    }

    public UUID definicaoCurvaId() {
        return definicaoCurvaId;
    }

    public UUID versaoDefinicaoCurvaId() {
        return versaoDefinicaoCurvaId;
    }

    public LocalDate dataReferencia() {
        return dataReferencia;
    }

    public MomentoCurva momentoCurva() {
        return momentoCurva;
    }

    public int numeroVersao() {
        return numeroVersao;
    }

    public OrigemVersao origemVersao() {
        return origemVersao;
    }

    public EstadoVersaoCurva estado() {
        return estado;
    }

    public UUID execucaoCurvaId() {
        return execucaoCurvaId;
    }

    public Instant publicadoEm() {
        return publicadoEm;
    }
}
