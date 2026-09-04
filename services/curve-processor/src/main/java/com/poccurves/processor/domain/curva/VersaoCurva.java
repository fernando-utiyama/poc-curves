package com.poccurves.processor.domain.curva;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio que representa uma versão de curva publicada pelo
 * curve-processor (origem IMPORTADA ou CARREGADA — nunca CALCULADA, que é
 * exclusiva do curve-engine), espelhando db/migration/V4__versao_curva.sql
 * (tabela versao_curva). Segue a mesma convenção de id gerado client-side
 * usada por DefinicaoCurva (curve-api) e ModeloCurva (curve-engine) — a
 * coluna tem DEFAULT NEWSEQUENTIALID() mas a aplicação sempre fornece o seu
 * próprio UUID no INSERT.
 * <p>
 * Ciclo de vida: EM_VALIDACAO (recém-criada, ainda não passou pela bateria
 * de validação) -&gt; PUBLICADA (aprovada e publicada) ou REPROVADA (falhou
 * validação, nunca publicada). Uma versão PUBLICADA pode depois ser
 * SUBSTITUIDA quando uma versão mais nova da mesma curva/data/momento é
 * publicada — nunca o contrário.
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

    /**
     * Cria uma nova versão em EM_VALIDACAO — ainda não publicada.
     * {@code numeroVersao} é responsabilidade de quem chama calcular
     * (tipicamente: 1 + o maior número de versão já existente para a mesma
     * definicaoCurvaId + dataReferencia + momentoCurva).
     *
     * @throws IllegalArgumentException se numeroVersao for menor que 1, ou se origemVersao for CALCULADA
     *                                   (exclusiva do curve-engine, nunca produzida pelo curve-processor)
     * @throws NullPointerException     se qualquer outro argumento for nulo
     */
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
        if (numeroVersao < 1) {
            throw new IllegalArgumentException("numeroVersao deve ser >= 1: " + numeroVersao);
        }
        Objects.requireNonNull(origemVersao, "origemVersao não pode ser nula");
        if (origemVersao == OrigemVersao.CALCULADA) {
            throw new IllegalArgumentException("curve-processor nunca produz versão CALCULADA — isso é exclusivo do curve-engine");
        }
        Objects.requireNonNull(execucaoCurvaId, "execucaoCurvaId não pode ser nulo");

        return new VersaoCurva(
                UUID.randomUUID(), definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia, momentoCurva,
                numeroVersao, origemVersao, EstadoVersaoCurva.EM_VALIDACAO, execucaoCurvaId, null
        );
    }

    /** Reconstrói uma VersaoCurva a partir de uma linha já persistida. */
    public static VersaoCurva reidratar(
            UUID id, UUID definicaoCurvaId, UUID versaoDefinicaoCurvaId, LocalDate dataReferencia,
            MomentoCurva momentoCurva, int numeroVersao, OrigemVersao origemVersao,
            EstadoVersaoCurva estado, UUID execucaoCurvaId, Instant publicadoEm
    ) {
        return new VersaoCurva(id, definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia, momentoCurva,
                numeroVersao, origemVersao, estado, execucaoCurvaId, publicadoEm);
    }

    /**
     * Publica a versão: EM_VALIDACAO -&gt; PUBLICADA, marca o instante de publicação.
     *
     * @throws IllegalStateException se o estado atual não for EM_VALIDACAO
     */
    public void publicar() {
        if (this.estado != EstadoVersaoCurva.EM_VALIDACAO) {
            throw new IllegalStateException("não é possível publicar a partir do estado " + this.estado + "; esperado EM_VALIDACAO");
        }
        this.estado = EstadoVersaoCurva.PUBLICADA;
        this.publicadoEm = Instant.now();
    }

    /**
     * Reprova a versão: EM_VALIDACAO -&gt; REPROVADA. Uma versão reprovada nunca é publicada
     * e a versão anteriormente PUBLICADA (se houver) permanece vigente.
     *
     * @throws IllegalStateException se o estado atual não for EM_VALIDACAO
     */
    public void reprovar() {
        if (this.estado != EstadoVersaoCurva.EM_VALIDACAO) {
            throw new IllegalStateException("não é possível reprovar a partir do estado " + this.estado + "; esperado EM_VALIDACAO");
        }
        this.estado = EstadoVersaoCurva.REPROVADA;
    }

    /**
     * Marca esta versão (até então PUBLICADA) como substituída por uma versão mais nova.
     *
     * @throws IllegalStateException se o estado atual não for PUBLICADA
     */
    public void substituir() {
        if (this.estado != EstadoVersaoCurva.PUBLICADA) {
            throw new IllegalStateException("não é possível substituir a partir do estado " + this.estado + "; esperado PUBLICADA");
        }
        this.estado = EstadoVersaoCurva.SUBSTITUIDA;
    }

    public UUID id() { return id; }
    public UUID definicaoCurvaId() { return definicaoCurvaId; }
    public UUID versaoDefinicaoCurvaId() { return versaoDefinicaoCurvaId; }
    public LocalDate dataReferencia() { return dataReferencia; }
    public MomentoCurva momentoCurva() { return momentoCurva; }
    public int numeroVersao() { return numeroVersao; }
    public OrigemVersao origemVersao() { return origemVersao; }
    public EstadoVersaoCurva estado() { return estado; }
    public UUID execucaoCurvaId() { return execucaoCurvaId; }
    public Instant publicadoEm() { return publicadoEm; }
}
