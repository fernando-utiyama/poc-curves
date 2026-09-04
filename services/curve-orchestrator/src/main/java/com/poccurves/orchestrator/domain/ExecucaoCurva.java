package com.poccurves.orchestrator.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entidade de domínio que representa o ciclo de vida e a máquina de estados
 * da execução de uma curva.
 */
public class ExecucaoCurva {

    private final UUID id;
    private final UUID correlacaoId;
    private final UUID execucaoPaiId;
    private final UUID definicaoCurvaId;
    private final String conjuntoDados;
    private final LocalDate dataReferencia;
    private final MomentoCurva momentoCurva;
    private final TipoDisparo disparo;
    private final String disparadoPor;
    private final Faixa faixa;
    private EstadoExecucao estado;
    private String motivoSemDado;
    private final Instant horarioLimite;
    private final Integer margemSegundos;
    private String duracaoPorEtapaJson;
    private int tentativas;
    private String codigoErro;
    private String mensagemErro;
    private final Instant iniciadoEm;
    private Instant finalizadoEm;

    /**
     * Grafo de transição do estado da execução. PENDENTE→EXECUTANDO→CONSTRUINDO→{CONCLUIDA,SEM_DADO,FALHOU}
     * é o caminho feliz especificado no backlog do orquestrador. EM_RISCO e ATRASADA existem no CHECK constraint
     * do banco e no proposal.md (alerta preditivo de prazo), mas o proposal.md deixa em aberto, como decisão
     * de negócio, o que a mesa faz quando o corte chega sem a curva — este grafo assume que EM_RISCO e ATRASADA
     * são sinalizações não-terminais sobre o mesmo fluxo (a execução pode seguir e concluir, falhar, ou ficar
     * sem dado a partir delas), e deve ser revisado quando essa decisão de negócio existir.
     */
    private static final Map<EstadoExecucao, Set<EstadoExecucao>> TRANSICOES_PERMITIDAS = Map.of(
            EstadoExecucao.PENDENTE, Set.of(EstadoExecucao.EXECUTANDO),
            EstadoExecucao.EXECUTANDO, Set.of(EstadoExecucao.CONSTRUINDO, EstadoExecucao.EM_RISCO, EstadoExecucao.SEM_DADO, EstadoExecucao.FALHOU),
            EstadoExecucao.CONSTRUINDO, Set.of(EstadoExecucao.EM_RISCO, EstadoExecucao.CONCLUIDA, EstadoExecucao.FALHOU),
            EstadoExecucao.EM_RISCO, Set.of(EstadoExecucao.CONSTRUINDO, EstadoExecucao.ATRASADA, EstadoExecucao.CONCLUIDA, EstadoExecucao.SEM_DADO, EstadoExecucao.FALHOU),
            EstadoExecucao.ATRASADA, Set.of(EstadoExecucao.CONCLUIDA, EstadoExecucao.SEM_DADO, EstadoExecucao.FALHOU),
            EstadoExecucao.CONCLUIDA, Set.of(),
            EstadoExecucao.SEM_DADO, Set.of(),
            EstadoExecucao.FALHOU, Set.of()
    );

    private ExecucaoCurva(
            UUID id,
            UUID correlacaoId,
            UUID execucaoPaiId,
            UUID definicaoCurvaId,
            String conjuntoDados,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            TipoDisparo disparo,
            String disparadoPor,
            Faixa faixa,
            EstadoExecucao estado,
            String motivoSemDado,
            Instant horarioLimite,
            Integer margemSegundos,
            String duracaoPorEtapaJson,
            int tentativas,
            String codigoErro,
            String mensagemErro,
            Instant iniciadoEm,
            Instant finalizadoEm
    ) {
        this.id = id;
        this.correlacaoId = correlacaoId;
        this.execucaoPaiId = execucaoPaiId;
        this.definicaoCurvaId = definicaoCurvaId;
        this.conjuntoDados = conjuntoDados;
        this.dataReferencia = dataReferencia;
        this.momentoCurva = momentoCurva;
        this.disparo = disparo;
        this.disparadoPor = disparadoPor;
        this.faixa = faixa;
        this.estado = estado;
        this.motivoSemDado = motivoSemDado;
        this.horarioLimite = horarioLimite;
        this.margemSegundos = margemSegundos;
        this.duracaoPorEtapaJson = duracaoPorEtapaJson;
        this.tentativas = tentativas;
        this.codigoErro = codigoErro;
        this.mensagemErro = mensagemErro;
        this.iniciadoEm = iniciadoEm;
        this.finalizadoEm = finalizadoEm;
    }

    public static ExecucaoCurva iniciar(
            UUID correlacaoId,
            UUID execucaoPaiId,
            UUID definicaoCurvaId,
            String conjuntoDados,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            TipoDisparo disparo,
            String disparadoPor,
            Faixa faixa,
            Instant horarioLimite,
            Integer margemSegundos
    ) {
        if (correlacaoId == null) {
            throw new IllegalArgumentException("correlacaoId não pode ser nulo");
        }
        if (disparo == null) {
            throw new IllegalArgumentException("disparo não pode ser nulo");
        }
        if (faixa == null) {
            throw new IllegalArgumentException("faixa não pode ser nulo");
        }

        return new ExecucaoCurva(
                UUID.randomUUID(),
                correlacaoId,
                execucaoPaiId,
                definicaoCurvaId,
                conjuntoDados,
                dataReferencia,
                momentoCurva,
                disparo,
                disparadoPor,
                faixa,
                EstadoExecucao.PENDENTE,
                null,
                horarioLimite,
                margemSegundos,
                null,
                0,
                null,
                null,
                Instant.now(),
                null
        );
    }

    /**
     * Reconstrói uma execução a partir de uma linha já persistida em execucao_curva
     * (leitura via {@code ExecucaoCurvaRepository}) — sem validação, pois os dados
     * já passaram pelas invariantes no momento em que foram gravados.
     */
    public static ExecucaoCurva reidratar(
            UUID id,
            UUID correlacaoId,
            UUID execucaoPaiId,
            UUID definicaoCurvaId,
            String conjuntoDados,
            LocalDate dataReferencia,
            MomentoCurva momentoCurva,
            TipoDisparo disparo,
            String disparadoPor,
            Faixa faixa,
            EstadoExecucao estado,
            String motivoSemDado,
            Instant horarioLimite,
            Integer margemSegundos,
            String duracaoPorEtapaJson,
            int tentativas,
            String codigoErro,
            String mensagemErro,
            Instant iniciadoEm,
            Instant finalizadoEm
    ) {
        return new ExecucaoCurva(
                id,
                correlacaoId,
                execucaoPaiId,
                definicaoCurvaId,
                conjuntoDados,
                dataReferencia,
                momentoCurva,
                disparo,
                disparadoPor,
                faixa,
                estado,
                motivoSemDado,
                horarioLimite,
                margemSegundos,
                duracaoPorEtapaJson,
                tentativas,
                codigoErro,
                mensagemErro,
                iniciadoEm,
                finalizadoEm
        );
    }

    private void transicionar(EstadoExecucao novoEstado) {
        Set<EstadoExecucao> permitidos = TRANSICOES_PERMITIDAS.get(this.estado);
        if (permitidos == null || !permitidos.contains(novoEstado)) {
            throw new IllegalStateException("Transição de estado inválida de " + this.estado + " para " + novoEstado);
        }
        this.estado = novoEstado;
    }

    public void iniciarExecucao() {
        transicionar(EstadoExecucao.EXECUTANDO);
    }

    public void iniciarConstrucao() {
        transicionar(EstadoExecucao.CONSTRUINDO);
    }

    public void sinalizarEmRisco() {
        transicionar(EstadoExecucao.EM_RISCO);
    }

    public void marcarAtrasada() {
        transicionar(EstadoExecucao.ATRASADA);
    }

    public void concluir() {
        transicionar(EstadoExecucao.CONCLUIDA);
        this.finalizadoEm = Instant.now();
    }

    public void marcarSemDado(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("motivo não pode ser nulo ou vazio");
        }
        transicionar(EstadoExecucao.SEM_DADO);
        this.motivoSemDado = motivo;
        this.finalizadoEm = Instant.now();
    }

    public void falhar(String codigoErro, String mensagemErro) {
        if (codigoErro == null || codigoErro.isBlank()) {
            throw new IllegalArgumentException("codigoErro não pode ser nulo ou vazio");
        }
        if (mensagemErro == null || mensagemErro.isBlank()) {
            throw new IllegalArgumentException("mensagemErro não pode ser nulo ou vazio");
        }
        transicionar(EstadoExecucao.FALHOU);
        this.codigoErro = codigoErro;
        this.mensagemErro = mensagemErro;
        this.finalizadoEm = Instant.now();
    }

    public void incrementarTentativa() {
        this.tentativas++;
    }

    public void registrarDuracaoPorEtapa(String duracaoPorEtapaJson) {
        this.duracaoPorEtapaJson = duracaoPorEtapaJson;
    }

    public UUID id() {
        return id;
    }

    public UUID correlacaoId() {
        return correlacaoId;
    }

    public UUID execucaoPaiId() {
        return execucaoPaiId;
    }

    public UUID definicaoCurvaId() {
        return definicaoCurvaId;
    }

    public String conjuntoDados() {
        return conjuntoDados;
    }

    public LocalDate dataReferencia() {
        return dataReferencia;
    }

    public MomentoCurva momentoCurva() {
        return momentoCurva;
    }

    public TipoDisparo disparo() {
        return disparo;
    }

    public String disparadoPor() {
        return disparadoPor;
    }

    public Faixa faixa() {
        return faixa;
    }

    public EstadoExecucao estado() {
        return estado;
    }

    public String motivoSemDado() {
        return motivoSemDado;
    }

    public Instant horarioLimite() {
        return horarioLimite;
    }

    public Integer margemSegundos() {
        return margemSegundos;
    }

    public String duracaoPorEtapaJson() {
        return duracaoPorEtapaJson;
    }

    public int tentativas() {
        return tentativas;
    }

    public String codigoErro() {
        return codigoErro;
    }

    public String mensagemErro() {
        return mensagemErro;
    }

    public Instant iniciadoEm() {
        return iniciadoEm;
    }

    public Instant finalizadoEm() {
        return finalizadoEm;
    }
}
