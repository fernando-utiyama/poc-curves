package com.poccurves.processor.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio que representa um lote de ingestão de dado de mercado
 * em blocos, espelhando db/migration/V2__dado_mercado.sql (tabela lote_ingestao).
 * O lote é aberto no primeiro bloco e se consolida automaticamente (estado
 * COMPLETO) quando blocosRecebidos atinge totalBlocos — nunca antes, e nunca
 * gera pedido de construção enquanto estiver ABERTO ou INCOMPLETO.
 */
public class LoteIngestao {

    private Long id;
    private final UUID execucaoCurvaId;
    private final String fonte;
    private final String conjuntoDados;
    private final TipoPayload tipoPayload;
    private final LocalDate dataReferencia;
    private final String loteExternoId;
    private final UUID correlationId;
    private String idEvento;
    private final String hashPayload;
    private final int totalBlocos;
    private int blocosRecebidos;
    private int pontosRecebidos;
    private int pontosGravados;
    private int pontosDivergentes;
    private String divergencias;
    private EstadoLoteIngestao estado;
    private final Instant recebidoEm;

    private LoteIngestao(
            UUID execucaoCurvaId,
            String fonte,
            String conjuntoDados,
            TipoPayload tipoPayload,
            LocalDate dataReferencia,
            String loteExternoId,
            UUID correlationId,
            String idEvento,
            String hashPayload,
            int totalBlocos,
            int blocosRecebidos,
            int pontosRecebidos,
            int pontosGravados,
            int pontosDivergentes,
            String divergencias,
            EstadoLoteIngestao estado,
            Instant recebidoEm
    ) {
        this.execucaoCurvaId = execucaoCurvaId;
        this.fonte = fonte;
        this.conjuntoDados = conjuntoDados;
        this.tipoPayload = tipoPayload;
        this.dataReferencia = dataReferencia;
        this.loteExternoId = loteExternoId;
        this.correlationId = correlationId;
        this.idEvento = idEvento;
        this.hashPayload = hashPayload;
        this.totalBlocos = totalBlocos;
        this.blocosRecebidos = blocosRecebidos;
        this.pontosRecebidos = pontosRecebidos;
        this.pontosGravados = pontosGravados;
        this.pontosDivergentes = pontosDivergentes;
        this.divergencias = divergencias;
        this.estado = estado;
        this.recebidoEm = recebidoEm;
    }

    /**
     * Abre um novo lote de ingestão, em estado ABERTO, no recebimento do primeiro bloco.
     *
     * @throws IllegalArgumentException se fonte, conjuntoDados, loteExternoId, idEvento ou
     *                                   hashPayload forem nulos/em branco, ou se totalBlocos for menor que 1
     * @throws NullPointerException     se tipoPayload ou dataReferencia forem nulos
     */
    public static LoteIngestao abrir(
            UUID execucaoCurvaId,
            String fonte,
            String conjuntoDados,
            TipoPayload tipoPayload,
            LocalDate dataReferencia,
            String loteExternoId,
            UUID correlationId,
            String idEvento,
            String hashPayload,
            int totalBlocos
    ) {
        if (fonte == null || fonte.isBlank()) {
            throw new IllegalArgumentException("fonte não pode ser nula ou vazia");
        }
        if (conjuntoDados == null || conjuntoDados.isBlank()) {
            throw new IllegalArgumentException("conjuntoDados não pode ser nulo ou vazio");
        }
        Objects.requireNonNull(tipoPayload, "tipoPayload não pode ser nulo");
        Objects.requireNonNull(dataReferencia, "dataReferencia não pode ser nula");
        if (loteExternoId == null || loteExternoId.isBlank()) {
            throw new IllegalArgumentException("loteExternoId não pode ser nulo ou vazio");
        }
        if (idEvento == null || idEvento.isBlank()) {
            throw new IllegalArgumentException("idEvento não pode ser nulo ou vazio");
        }
        if (hashPayload == null || hashPayload.isBlank()) {
            throw new IllegalArgumentException("hashPayload não pode ser nulo ou vazio");
        }
        if (totalBlocos < 1) {
            throw new IllegalArgumentException("totalBlocos deve ser >= 1: " + totalBlocos);
        }

        return new LoteIngestao(
                execucaoCurvaId, fonte, conjuntoDados, tipoPayload, dataReferencia,
                loteExternoId, correlationId, idEvento, hashPayload, totalBlocos,
                0, 0, 0, 0, null, EstadoLoteIngestao.ABERTO, Instant.now()
        );
    }

    /** Atribui o id gerado pelo banco na persistência. Só pode ser chamado uma vez. */
    public void atribuirId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("id já atribuído: " + this.id);
        }
        this.id = id;
    }

    /**
     * Reconstrói um LoteIngestao a partir de uma linha já persistida — usado
     * pelo repositório ao recarregar um lote existente para registrar mais
     * um bloco. Diferente de {@link #abrir}, não valida invariantes de
     * criação (a linha já passou por eles quando foi inserida) nem começa em
     * ABERTO/zerado — reproduz exatamente o estado gravado.
     */
    public static LoteIngestao reidratar(
            long id,
            UUID execucaoCurvaId,
            String fonte,
            String conjuntoDados,
            TipoPayload tipoPayload,
            LocalDate dataReferencia,
            String loteExternoId,
            UUID correlationId,
            String idEvento,
            String hashPayload,
            int totalBlocos,
            int blocosRecebidos,
            int pontosRecebidos,
            int pontosGravados,
            int pontosDivergentes,
            String divergencias,
            EstadoLoteIngestao estado,
            Instant recebidoEm
    ) {
        LoteIngestao lote = new LoteIngestao(
                execucaoCurvaId, fonte, conjuntoDados, tipoPayload, dataReferencia,
                loteExternoId, correlationId, idEvento, hashPayload, totalBlocos,
                blocosRecebidos, pontosRecebidos, pontosGravados, pontosDivergentes, divergencias, estado, recebidoEm
        );
        lote.id = id;
        return lote;
    }

    /**
     * Soma a quantidade de divergências detectadas num bloco ao total
     * acumulado do lote — usado para preencher {@code divergences} no
     * evento marketdata.normalized.v1, emitido só depois que o lote
     * consolida.
     *
     * @throws IllegalArgumentException se quantidade for negativa
     */
    public void somarDivergencias(int quantidade) {
        if (quantidade < 0) {
            throw new IllegalArgumentException("quantidade não pode ser negativa: " + quantidade);
        }
        this.pontosDivergentes += quantidade;
    }

    /**
     * Registra a chegada de um bloco: soma pontos recebidos e gravados, atualiza o idEvento para
     * o do bloco mais recente, e consolida automaticamente para COMPLETO quando blocosRecebidos
     * atinge totalBlocos.
     *
     * @throws IllegalStateException    se o lote não estiver ABERTO, ou se todos os blocos já tiverem sido recebidos
     * @throws IllegalArgumentException se idEvento for nulo/em branco, ou se algum contador for negativo
     */
    public void registrarBloco(String idEvento, int pontosRecebidosNoBloco, int pontosGravadosNoBloco) {
        if (this.estado != EstadoLoteIngestao.ABERTO) {
            throw new IllegalStateException(
                    "não é possível registrar bloco em lote no estado " + this.estado + "; esperado ABERTO");
        }
        if (idEvento == null || idEvento.isBlank()) {
            throw new IllegalArgumentException("idEvento não pode ser nulo ou vazio");
        }
        if (pontosRecebidosNoBloco < 0 || pontosGravadosNoBloco < 0) {
            throw new IllegalArgumentException("pontosRecebidosNoBloco e pontosGravadosNoBloco não podem ser negativos");
        }
        if (this.blocosRecebidos >= this.totalBlocos) {
            throw new IllegalStateException("todos os " + this.totalBlocos + " blocos já foram recebidos");
        }

        this.idEvento = idEvento;
        this.blocosRecebidos++;
        this.pontosRecebidos += pontosRecebidosNoBloco;
        this.pontosGravados += pontosGravadosNoBloco;

        if (this.blocosRecebidos == this.totalBlocos) {
            this.estado = EstadoLoteIngestao.COMPLETO;
        }
    }

    /**
     * Marca o lote como INCOMPLETO após o tempo limite, nomeando as sequências faltantes.
     *
     * @throws IllegalStateException    se o lote não estiver ABERTO
     * @throws IllegalArgumentException se sequenciasFaltantes for nulo ou em branco
     */
    public void marcarIncompleto(String sequenciasFaltantes) {
        if (this.estado != EstadoLoteIngestao.ABERTO) {
            throw new IllegalStateException(
                    "não é possível marcar incompleto um lote no estado " + this.estado + "; esperado ABERTO");
        }
        if (sequenciasFaltantes == null || sequenciasFaltantes.isBlank()) {
            throw new IllegalArgumentException("sequenciasFaltantes não pode ser nulo ou vazio");
        }

        this.estado = EstadoLoteIngestao.INCOMPLETO;
        this.divergencias = "sequências faltantes: " + sequenciasFaltantes;
    }

    public Long id() {
        return id;
    }

    public UUID execucaoCurvaId() {
        return execucaoCurvaId;
    }

    public String fonte() {
        return fonte;
    }

    public String conjuntoDados() {
        return conjuntoDados;
    }

    public TipoPayload tipoPayload() {
        return tipoPayload;
    }

    public LocalDate dataReferencia() {
        return dataReferencia;
    }

    public String loteExternoId() {
        return loteExternoId;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public String idEvento() {
        return idEvento;
    }

    public String hashPayload() {
        return hashPayload;
    }

    public int totalBlocos() {
        return totalBlocos;
    }

    public int blocosRecebidos() {
        return blocosRecebidos;
    }

    public int pontosRecebidos() {
        return pontosRecebidos;
    }

    public int pontosGravados() {
        return pontosGravados;
    }

    public int pontosDivergentes() {
        return pontosDivergentes;
    }

    public String divergencias() {
        return divergencias;
    }

    public EstadoLoteIngestao estado() {
        return estado;
    }

    public Instant recebidoEm() {
        return recebidoEm;
    }
}
