package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoteIngestaoTest {

    private final LocalDate dataReferencia = LocalDate.now();

    private LoteIngestao abrirLotePadrao(int totalBlocos) {
        return LoteIngestao.abrir(
                null,
                "B3",
                "PR_DI1",
                TipoPayload.INDIVIDUAL_QUOTES,
                dataReferencia,
                "lote-ext-1",
                null,
                "evt-inicial",
                "abc123",
                totalBlocos
        );
    }

    @Test
    void deveAbrirLotePadraoComValoresIniciaisCorretos() {
        LoteIngestao lote = abrirLotePadrao(3);

        assertThat(lote.id()).isNull();
        assertThat(lote.execucaoCurvaId()).isNull();
        assertThat(lote.fonte()).isEqualTo("B3");
        assertThat(lote.conjuntoDados()).isEqualTo("PR_DI1");
        assertThat(lote.tipoPayload()).isEqualTo(TipoPayload.INDIVIDUAL_QUOTES);
        assertThat(lote.dataReferencia()).isEqualTo(dataReferencia);
        assertThat(lote.loteExternoId()).isEqualTo("lote-ext-1");
        assertThat(lote.idEvento()).isEqualTo("evt-inicial");
        assertThat(lote.hashPayload()).isEqualTo("abc123");
        assertThat(lote.totalBlocos()).isEqualTo(3);
        assertThat(lote.blocosRecebidos()).isEqualTo(0);
        assertThat(lote.pontosRecebidos()).isEqualTo(0);
        assertThat(lote.pontosGravados()).isEqualTo(0);
        assertThat(lote.divergencias()).isNull();
        assertThat(lote.estado()).isEqualTo(EstadoLoteIngestao.ABERTO);
        assertThat(lote.recebidoEm()).isNotNull();
    }

    @Test
    void deveLancarIllegalArgumentExceptionQuandoTotalBlocosMenorQueUm() {
        assertThatThrownBy(() -> LoteIngestao.abrir(
                null, "B3", "PR_DI1", TipoPayload.INDIVIDUAL_QUOTES,
                dataReferencia, "lote-ext-1", null, "evt-inicial", "abc123", 0
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarIllegalArgumentExceptionQuandoCamposObrigatoriosEstiveremEmBranco() {
        assertThatThrownBy(() -> LoteIngestao.abrir(
                null, "   ", "PR_DI1", TipoPayload.INDIVIDUAL_QUOTES,
                dataReferencia, "lote-ext-1", null, "evt-inicial", "abc123", 3
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LoteIngestao.abrir(
                null, "B3", "PR_DI1", TipoPayload.INDIVIDUAL_QUOTES,
                dataReferencia, "   ", null, "evt-inicial", "abc123", 3
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LoteIngestao.abrir(
                null, "B3", "PR_DI1", TipoPayload.INDIVIDUAL_QUOTES,
                dataReferencia, "lote-ext-1", null, "   ", "abc123", 3
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LoteIngestao.abrir(
                null, "B3", "PR_DI1", TipoPayload.INDIVIDUAL_QUOTES,
                dataReferencia, "lote-ext-1", null, "evt-inicial", "   ", 3
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarNullPointerExceptionQuandoTipoPayloadForNulo() {
        assertThatThrownBy(() -> LoteIngestao.abrir(
                null, "B3", "PR_DI1", null,
                dataReferencia, "lote-ext-1", null, "evt-inicial", "abc123", 3
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveRegistrarBlocosEConsolidarParaCompletoAoAtingirTotal() {
        LoteIngestao lote = abrirLotePadrao(2);

        lote.registrarBloco("evt-1", 10, 10);
        assertThat(lote.estado()).isEqualTo(EstadoLoteIngestao.ABERTO);
        assertThat(lote.blocosRecebidos()).isEqualTo(1);
        assertThat(lote.pontosRecebidos()).isEqualTo(10);
        assertThat(lote.pontosGravados()).isEqualTo(10);
        assertThat(lote.idEvento()).isEqualTo("evt-1");

        lote.registrarBloco("evt-2", 5, 5);
        assertThat(lote.estado()).isEqualTo(EstadoLoteIngestao.COMPLETO);
        assertThat(lote.blocosRecebidos()).isEqualTo(2);
        assertThat(lote.pontosRecebidos()).isEqualTo(15);
        assertThat(lote.pontosGravados()).isEqualTo(15);
        assertThat(lote.idEvento()).isEqualTo("evt-2");
    }

    @Test
    void deveLancarIllegalStateExceptionAoRegistrarBlocoEmLoteJaCompleto() {
        LoteIngestao lote = abrirLotePadrao(2);
        lote.registrarBloco("evt-1", 10, 10);
        lote.registrarBloco("evt-2", 5, 5);

        assertThatThrownBy(() -> lote.registrarBloco("evt-3", 1, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveLancarIllegalArgumentExceptionAoRegistrarBlocoComDadosInvalidos() {
        LoteIngestao lote = abrirLotePadrao(2);

        assertThatThrownBy(() -> lote.registrarBloco(null, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> lote.registrarBloco("evt-1", -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveMarcarComoIncompletoEmLoteAberto() {
        LoteIngestao lote = abrirLotePadrao(3);

        lote.marcarIncompleto("3, 4");

        assertThat(lote.estado()).isEqualTo(EstadoLoteIngestao.INCOMPLETO);
        assertThat(lote.divergencias()).contains("3, 4");
    }

    @Test
    void deveLancarIllegalStateExceptionAoMarcarIncompletoEmLoteCompleto() {
        LoteIngestao lote = abrirLotePadrao(2);
        lote.registrarBloco("evt-1", 10, 10);
        lote.registrarBloco("evt-2", 5, 5);

        assertThatThrownBy(() -> lote.marcarIncompleto("5"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveLancarIllegalArgumentExceptionAoMarcarIncompletoComSequenciasNulasOuVazias() {
        LoteIngestao loteNulo = abrirLotePadrao(3);
        assertThatThrownBy(() -> loteNulo.marcarIncompleto(null))
                .isInstanceOf(IllegalArgumentException.class);

        LoteIngestao loteVazio = abrirLotePadrao(3);
        assertThatThrownBy(() -> loteVazio.marcarIncompleto(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveAtribuirIdUmaUnicaVezELancarIllegalStateExceptionSeRepetido() {
        LoteIngestao lote = abrirLotePadrao(3);

        lote.atribuirId(99);
        assertThat(lote.id()).isEqualTo(99L);

        assertThatThrownBy(() -> lote.atribuirId(100))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveReidratarReproduzindoExatamenteOEstadoGravado() {
        java.time.Instant recebidoEmOriginal = java.time.Instant.parse("2026-08-21T18:00:00Z");

        LoteIngestao lote = LoteIngestao.reidratar(
                42L,
                null,
                "B3",
                "PR_DI1",
                TipoPayload.INDIVIDUAL_QUOTES,
                dataReferencia,
                "lote-ext-1",
                null,
                "evt-2",
                "abc123",
                3,
                2,
                20,
                18,
                1,
                "sequências faltantes: 3",
                EstadoLoteIngestao.INCOMPLETO,
                recebidoEmOriginal
        );

        assertThat(lote.id()).isEqualTo(42L);
        assertThat(lote.blocosRecebidos()).isEqualTo(2);
        assertThat(lote.pontosRecebidos()).isEqualTo(20);
        assertThat(lote.pontosGravados()).isEqualTo(18);
        assertThat(lote.pontosDivergentes()).isEqualTo(1);
        assertThat(lote.divergencias()).isEqualTo("sequências faltantes: 3");
        assertThat(lote.estado()).isEqualTo(EstadoLoteIngestao.INCOMPLETO);
        assertThat(lote.recebidoEm()).isEqualTo(recebidoEmOriginal);
    }

    @Test
    void reidratarPermiteContinuarRegistrandoBlocosQuandoAbertoENaoEstaCompleto() {
        LoteIngestao lote = LoteIngestao.reidratar(
                7L, null, "B3", "PR_DI1", TipoPayload.INDIVIDUAL_QUOTES, dataReferencia,
                "lote-ext-2", null, "evt-1", "hash1", 2, 1, 10, 10, 0, null,
                EstadoLoteIngestao.ABERTO, java.time.Instant.now()
        );

        lote.registrarBloco("evt-2", 5, 5);

        assertThat(lote.estado()).isEqualTo(EstadoLoteIngestao.COMPLETO);
        assertThat(lote.pontosRecebidos()).isEqualTo(15);
    }

    @Test
    void somaDivergenciasAcumulandoEntreBlocos() {
        LoteIngestao lote = abrirLotePadrao(2);

        lote.somarDivergencias(2);
        lote.somarDivergencias(1);

        assertThat(lote.pontosDivergentes()).isEqualTo(3);
    }

    @Test
    void novoLoteComecaComZeroDivergencias() {
        assertThat(abrirLotePadrao(1).pontosDivergentes()).isZero();
    }

    @Test
    void lancaIllegalArgumentExceptionAoSomarDivergenciasNegativas() {
        LoteIngestao lote = abrirLotePadrao(1);

        assertThatThrownBy(() -> lote.somarDivergencias(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
