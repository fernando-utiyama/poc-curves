package com.poccurves.orchestrator.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entidade de domínio que representa uma pendência de dead-letter e seu
 * ciclo de vida, espelhando db/migration/V6__pendencia_dlq.sql.
 */
public class PendenciaDlq {

    /**
     * Grafo de transição do estado. ABERTA → EM_REPROCESSAMENTO → RESOLVIDA é o caminho feliz
     * ("reprocessar republica a mensagem no tópico original preservando o id_evento; quando o
     * processamento dá certo, a pendência é fechada automaticamente" — proposal.md). OBSOLETA é
     * alcançável direto de ABERTA ou de EM_REPROCESSAMENTO ("se já existe lote mais recente para
     * a mesma chave, o reprocessamento é recusado e a pendência vira obsoleta" — proposal.md).
     * DESCARTADA é decisão manual do operador, também alcançável dos dois estados não-terminais.
     * Reprocessamento que falha volta para ABERTA (nova tentativa possível), não vira estado à parte.
     */
    private static final Map<EstadoPendenciaDlq, Set<EstadoPendenciaDlq>> TRANSICOES_PERMITIDAS = Map.of(
            EstadoPendenciaDlq.ABERTA, Set.of(EstadoPendenciaDlq.EM_REPROCESSAMENTO, EstadoPendenciaDlq.OBSOLETA, EstadoPendenciaDlq.DESCARTADA),
            EstadoPendenciaDlq.EM_REPROCESSAMENTO, Set.of(EstadoPendenciaDlq.RESOLVIDA, EstadoPendenciaDlq.ABERTA, EstadoPendenciaDlq.OBSOLETA, EstadoPendenciaDlq.DESCARTADA),
            EstadoPendenciaDlq.RESOLVIDA, Set.of(),
            EstadoPendenciaDlq.DESCARTADA, Set.of(),
            EstadoPendenciaDlq.OBSOLETA, Set.of()
    );

    private Long id;
    private final String idEvento;
    private final UUID correlacaoId;
    private final String motivo;
    private final String detalhe;
    private final String fonte;
    private final String conjuntoDados;
    private final LocalDate dataReferencia;
    private final String topicoOrigem;
    private final Integer particaoOrigem;
    private final Long offsetOrigem;
    private final String topicoDlq;
    private final Integer particaoDlq;
    private final Long offsetDlq;
    private final String grupoConsumo;
    private final String versaoAplicacao;
    private final Instant falhouEm;
    private int tentativas;
    private EstadoPendenciaDlq estado;
    private Instant desfechoEm;
    private String responsavel;
    private String justificativa;

    private PendenciaDlq(
            Long id,
            String idEvento,
            UUID correlacaoId,
            String motivo,
            String detalhe,
            String fonte,
            String conjuntoDados,
            LocalDate dataReferencia,
            String topicoOrigem,
            Integer particaoOrigem,
            Long offsetOrigem,
            String topicoDlq,
            Integer particaoDlq,
            Long offsetDlq,
            String grupoConsumo,
            String versaoAplicacao,
            Instant falhouEm,
            int tentativas,
            EstadoPendenciaDlq estado,
            Instant desfechoEm,
            String responsavel,
            String justificativa
    ) {
        this.id = id;
        this.idEvento = idEvento;
        this.correlacaoId = correlacaoId;
        this.motivo = motivo;
        this.detalhe = detalhe;
        this.fonte = fonte;
        this.conjuntoDados = conjuntoDados;
        this.dataReferencia = dataReferencia;
        this.topicoOrigem = topicoOrigem;
        this.particaoOrigem = particaoOrigem;
        this.offsetOrigem = offsetOrigem;
        this.topicoDlq = topicoDlq;
        this.particaoDlq = particaoDlq;
        this.offsetDlq = offsetDlq;
        this.grupoConsumo = grupoConsumo;
        this.versaoAplicacao = versaoAplicacao;
        this.falhouEm = falhouEm;
        this.tentativas = tentativas;
        this.estado = estado;
        this.desfechoEm = desfechoEm;
        this.responsavel = responsavel;
        this.justificativa = justificativa;
    }

    /**
     * Abre uma nova pendência de dead-letter, em estado ABERTA, com 1 tentativa contabilizada
     * (a própria falha que gerou a pendência já é a primeira tentativa).
     *
     * @throws IllegalArgumentException se idEvento, motivo, topicoOrigem ou topicoDlq forem nulos/em branco
     * @throws NullPointerException     se falhouEm for nulo
     */
    public static PendenciaDlq abrir(
            String idEvento,
            UUID correlacaoId,
            String motivo,
            String detalhe,
            String fonte,
            String conjuntoDados,
            LocalDate dataReferencia,
            String topicoOrigem,
            Integer particaoOrigem,
            Long offsetOrigem,
            String topicoDlq,
            Integer particaoDlq,
            Long offsetDlq,
            String grupoConsumo,
            String versaoAplicacao,
            Instant falhouEm
    ) {
        if (idEvento == null || idEvento.isBlank()) {
            throw new IllegalArgumentException("idEvento não pode ser nulo ou vazio");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("motivo não pode ser nulo ou vazio");
        }
        if (topicoOrigem == null || topicoOrigem.isBlank()) {
            throw new IllegalArgumentException("topicoOrigem não pode ser nulo ou vazio");
        }
        if (topicoDlq == null || topicoDlq.isBlank()) {
            throw new IllegalArgumentException("topicoDlq não pode ser nulo ou vazio");
        }
        java.util.Objects.requireNonNull(falhouEm, "falhouEm não pode ser nulo");

        return new PendenciaDlq(
                null, idEvento, correlacaoId, motivo, detalhe, fonte, conjuntoDados, dataReferencia,
                topicoOrigem, particaoOrigem, offsetOrigem, topicoDlq, particaoDlq, offsetDlq,
                grupoConsumo, versaoAplicacao, falhouEm, 1, EstadoPendenciaDlq.ABERTA, null, null, null
        );
    }

    /**
     * Reconstrói uma pendência a partir de uma linha já persistida em pendencia_dlq
     * (leitura via {@code PendenciaDlqRepository}) — sem validação, pois os dados já
     * passaram pelas invariantes de {@link #abrir} no momento em que foram gravados.
     */
    public static PendenciaDlq reidratar(
            Long id,
            String idEvento,
            UUID correlacaoId,
            String motivo,
            String detalhe,
            String fonte,
            String conjuntoDados,
            LocalDate dataReferencia,
            String topicoOrigem,
            Integer particaoOrigem,
            Long offsetOrigem,
            String topicoDlq,
            Integer particaoDlq,
            Long offsetDlq,
            String grupoConsumo,
            String versaoAplicacao,
            Instant falhouEm,
            int tentativas,
            EstadoPendenciaDlq estado,
            Instant desfechoEm,
            String responsavel,
            String justificativa
    ) {
        return new PendenciaDlq(
                id, idEvento, correlacaoId, motivo, detalhe, fonte, conjuntoDados, dataReferencia,
                topicoOrigem, particaoOrigem, offsetOrigem, topicoDlq, particaoDlq, offsetDlq,
                grupoConsumo, versaoAplicacao, falhouEm, tentativas, estado, desfechoEm, responsavel, justificativa
        );
    }

    private void transicionar(EstadoPendenciaDlq novoEstado) {
        Set<EstadoPendenciaDlq> permitidos = TRANSICOES_PERMITIDAS.get(this.estado);
        if (permitidos == null || !permitidos.contains(novoEstado)) {
            throw new IllegalStateException("Transição de estado inválida de " + this.estado + " para " + novoEstado);
        }
        this.estado = novoEstado;
    }

    /** Atribui o id gerado pelo banco na persistência. Só pode ser chamado uma vez. */
    public void atribuirId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("id já atribuído: " + this.id);
        }
        this.id = id;
    }

    /** ABERTA -> EM_REPROCESSAMENTO: reprocessamento disparado (republica no tópico original). */
    public void iniciarReprocessamento() {
        transicionar(EstadoPendenciaDlq.EM_REPROCESSAMENTO);
    }

    /** EM_REPROCESSAMENTO -> RESOLVIDA: reprocessamento deu certo, pendência fecha sozinha. */
    public void resolver() {
        transicionar(EstadoPendenciaDlq.RESOLVIDA);
        this.desfechoEm = Instant.now();
    }

    /** EM_REPROCESSAMENTO -> ABERTA: reprocessamento falhou de novo, disponível para nova tentativa. */
    public void voltarParaAberta() {
        transicionar(EstadoPendenciaDlq.ABERTA);
        this.tentativas++;
    }

    /**
     * ABERTA ou EM_REPROCESSAMENTO -> DESCARTADA: decisão manual do operador.
     *
     * @throws IllegalArgumentException se responsavel ou justificativa forem nulos/em branco
     */
    public void descartar(String responsavel, String justificativa) {
        if (responsavel == null || responsavel.isBlank()) {
            throw new IllegalArgumentException("responsavel não pode ser nulo ou vazio");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("justificativa não pode ser nula ou vazia");
        }
        transicionar(EstadoPendenciaDlq.DESCARTADA);
        this.responsavel = responsavel;
        this.justificativa = justificativa;
        this.desfechoEm = Instant.now();
    }

    /** ABERTA ou EM_REPROCESSAMENTO -> OBSOLETA: já existe lote mais recente para a mesma chave. */
    public void marcarObsoleta() {
        transicionar(EstadoPendenciaDlq.OBSOLETA);
        this.desfechoEm = Instant.now();
    }

    public Long id() {
        return id;
    }

    public String idEvento() {
        return idEvento;
    }

    public UUID correlacaoId() {
        return correlacaoId;
    }

    public String motivo() {
        return motivo;
    }

    public String detalhe() {
        return detalhe;
    }

    public String fonte() {
        return fonte;
    }

    public String conjuntoDados() {
        return conjuntoDados;
    }

    public LocalDate dataReferencia() {
        return dataReferencia;
    }

    public String topicoOrigem() {
        return topicoOrigem;
    }

    public Integer particaoOrigem() {
        return particaoOrigem;
    }

    public Long offsetOrigem() {
        return offsetOrigem;
    }

    public String topicoDlq() {
        return topicoDlq;
    }

    public Integer particaoDlq() {
        return particaoDlq;
    }

    public Long offsetDlq() {
        return offsetDlq;
    }

    public String grupoConsumo() {
        return grupoConsumo;
    }

    public String versaoAplicacao() {
        return versaoAplicacao;
    }

    public Instant falhouEm() {
        return falhouEm;
    }

    public int tentativas() {
        return tentativas;
    }

    public EstadoPendenciaDlq estado() {
        return estado;
    }

    public Instant desfechoEm() {
        return desfechoEm;
    }

    public String responsavel() {
        return responsavel;
    }

    public String justificativa() {
        return justificativa;
    }
}
