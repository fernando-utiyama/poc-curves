package com.poccurves.orchestrator.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendenciaDlqTest {

    private PendenciaDlq abrirPendenciaPadrao() {
        return PendenciaDlq.abrir(
                "evt-1",
                UUID.randomUUID(),
                "PARSE_FAILED",
                null,
                "B3",
                "PR_DI1",
                LocalDate.now(),
                "marketdata.rotina.v1",
                2,
                100L,
                "marketdata.rotina.v1.curve-processor-rotina.dlq",
                2,
                5L,
                "curve-processor-rotina",
                "0.1.0",
                Instant.now()
        );
    }

    @Test
    void deveAbrirPendenciaComEstadoAbertaTentativaUnicaECamposCorretos() {
        UUID correlacaoId = UUID.randomUUID();
        LocalDate dataReferencia = LocalDate.of(2026, 8, 21);
        Instant falhouEm = Instant.now();

        PendenciaDlq pendencia = PendenciaDlq.abrir(
                "evt-123",
                correlacaoId,
                "SCHEMA_VALIDATION_ERROR",
                "Campo taxa inválido",
                "B3",
                "PR_DI1",
                dataReferencia,
                "marketdata.rotina.v1",
                1,
                456L,
                "marketdata.rotina.v1.curve-processor-rotina.dlq",
                3,
                789L,
                "curve-processor-rotina",
                "1.0.0",
                falhouEm
        );

        assertThat(pendencia.id()).isNull();
        assertThat(pendencia.idEvento()).isEqualTo("evt-123");
        assertThat(pendencia.correlacaoId()).isEqualTo(correlacaoId);
        assertThat(pendencia.motivo()).isEqualTo("SCHEMA_VALIDATION_ERROR");
        assertThat(pendencia.detalhe()).isEqualTo("Campo taxa inválido");
        assertThat(pendencia.fonte()).isEqualTo("B3");
        assertThat(pendencia.conjuntoDados()).isEqualTo("PR_DI1");
        assertThat(pendencia.dataReferencia()).isEqualTo(dataReferencia);
        assertThat(pendencia.topicoOrigem()).isEqualTo("marketdata.rotina.v1");
        assertThat(pendencia.particaoOrigem()).isEqualTo(1);
        assertThat(pendencia.offsetOrigem()).isEqualTo(456L);
        assertThat(pendencia.topicoDlq()).isEqualTo("marketdata.rotina.v1.curve-processor-rotina.dlq");
        assertThat(pendencia.particaoDlq()).isEqualTo(3);
        assertThat(pendencia.offsetDlq()).isEqualTo(789L);
        assertThat(pendencia.grupoConsumo()).isEqualTo("curve-processor-rotina");
        assertThat(pendencia.versaoAplicacao()).isEqualTo("1.0.0");
        assertThat(pendencia.falhouEm()).isEqualTo(falhouEm);
        assertThat(pendencia.tentativas()).isEqualTo(1);
        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.ABERTA);
        assertThat(pendencia.desfechoEm()).isNull();
        assertThat(pendencia.responsavel()).isNull();
        assertThat(pendencia.justificativa()).isNull();
    }

    @Test
    void deveLancarExcecaoQuandoCamposObrigatoriosForemNulosOuEmBrancoAoAbrir() {
        UUID correlacaoId = UUID.randomUUID();
        LocalDate dataRef = LocalDate.now();
        Instant falhouEm = Instant.now();

        // idEvento
        assertThatThrownBy(() -> PendenciaDlq.abrir(null, correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idEvento");
        assertThatThrownBy(() -> PendenciaDlq.abrir("", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idEvento");
        assertThatThrownBy(() -> PendenciaDlq.abrir("   ", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idEvento");

        // motivo
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, null, null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "   ", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        // topicoOrigem
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, null, 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topicoOrigem");
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topicoOrigem");
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "   ", 0, 0L, "topico.dlq", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topicoOrigem");

        // topicoDlq
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, null, 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topicoDlq");
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topicoDlq");
        assertThatThrownBy(() -> PendenciaDlq.abrir("evt-1", correlacaoId, "MOTIVO", null, "B3", "PR_DI1", dataRef, "topico.in", 0, 0L, "   ", 0, 0L, "grupo", "1.0", falhouEm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topicoDlq");
    }

    @Test
    void deveLancarNullPointerExceptionQuandoFalhouEmForNulo() {
        assertThatThrownBy(() -> PendenciaDlq.abrir(
                "evt-1",
                UUID.randomUUID(),
                "PARSE_FAILED",
                null,
                "B3",
                "PR_DI1",
                LocalDate.now(),
                "marketdata.rotina.v1",
                2,
                100L,
                "marketdata.rotina.v1.curve-processor-rotina.dlq",
                2,
                5L,
                "curve-processor-rotina",
                "0.1.0",
                null
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("falhouEm");
    }

    @Test
    void deveExecutarCaminhoFelizDeReprocessamentoAteResolucao() {
        PendenciaDlq pendencia = abrirPendenciaPadrao();
        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.ABERTA);

        pendencia.iniciarReprocessamento();
        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.EM_REPROCESSAMENTO);

        pendencia.resolver();
        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.RESOLVIDA);
        assertThat(pendencia.desfechoEm()).isNotNull();
    }

    @Test
    void deveMarcarObsoletaDiretoDeAberta() {
        PendenciaDlq pendencia = abrirPendenciaPadrao();

        pendencia.marcarObsoleta();

        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.OBSOLETA);
        assertThat(pendencia.desfechoEm()).isNotNull();
    }

    @Test
    void deveVoltarParaAbertaIncrementandoTentativasAposFalhaNoReprocessamento() {
        PendenciaDlq pendencia = abrirPendenciaPadrao();
        assertThat(pendencia.tentativas()).isEqualTo(1);

        pendencia.iniciarReprocessamento();
        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.EM_REPROCESSAMENTO);

        pendencia.voltarParaAberta();
        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.ABERTA);
        assertThat(pendencia.tentativas()).isEqualTo(2);
    }

    @Test
    void deveDescartarAPartirDeEmReprocessamentoComResponsavelEJustificativa() {
        PendenciaDlq pendencia = abrirPendenciaPadrao();
        pendencia.iniciarReprocessamento();

        pendencia.descartar("operador.x", "duplicidade confirmada");

        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.DESCARTADA);
        assertThat(pendencia.responsavel()).isEqualTo("operador.x");
        assertThat(pendencia.justificativa()).isEqualTo("duplicidade confirmada");
        assertThat(pendencia.desfechoEm()).isNotNull();
    }

    @Test
    void deveLancarExcecaoQuandoResponsavelOuJustificativaForemNulosOuEmBrancoAoDescartar() {
        PendenciaDlq pendencia1 = abrirPendenciaPadrao();
        assertThatThrownBy(() -> pendencia1.descartar(null, "motivo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responsavel");

        PendenciaDlq pendencia2 = abrirPendenciaPadrao();
        assertThatThrownBy(() -> pendencia2.descartar("", "motivo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responsavel");

        PendenciaDlq pendencia3 = abrirPendenciaPadrao();
        assertThatThrownBy(() -> pendencia3.descartar("   ", "motivo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responsavel");

        PendenciaDlq pendencia4 = abrirPendenciaPadrao();
        assertThatThrownBy(() -> pendencia4.descartar("resp", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");

        PendenciaDlq pendencia5 = abrirPendenciaPadrao();
        assertThatThrownBy(() -> pendencia5.descartar("resp", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");

        PendenciaDlq pendencia6 = abrirPendenciaPadrao();
        assertThatThrownBy(() -> pendencia6.descartar("resp", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");
    }

    @Test
    void deveLancarExcecaoAoChamarResolverDiretoDeAberta() {
        PendenciaDlq pendencia = abrirPendenciaPadrao();

        assertThatThrownBy(pendencia::resolver)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ABERTA")
                .hasMessageContaining("RESOLVIDA");
    }

    @Test
    void deveLancarExcecaoAoTentarTransicionarAposResolucao() {
        PendenciaDlq pendencia = abrirPendenciaPadrao();
        pendencia.iniciarReprocessamento();
        pendencia.resolver();

        assertThatThrownBy(pendencia::iniciarReprocessamento)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESOLVIDA");

        assertThatThrownBy(pendencia::resolver)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(pendencia::voltarParaAberta)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> pendencia.descartar("op", "motivo"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(pendencia::marcarObsoleta)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveAtribuirIdUmaUnicaVezELancarExcecaoSeChamadoNovamente() {
        PendenciaDlq pendencia = abrirPendenciaPadrao();
        assertThat(pendencia.id()).isNull();

        pendencia.atribuirId(42L);
        assertThat(pendencia.id()).isEqualTo(42L);

        assertThatThrownBy(() -> pendencia.atribuirId(99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("42");
    }
}
