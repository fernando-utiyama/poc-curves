package com.poccurves.orchestrator.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecucaoCurvaTest {

    private ExecucaoCurva criarExecucaoPadrao() {
        return ExecucaoCurva.iniciar(
                UUID.randomUUID(),
                null,
                null,
                "PR_DI1",
                LocalDate.now(),
                MomentoCurva.FECHAMENTO,
                TipoDisparo.AGENDADO,
                null,
                Faixa.ROTINA,
                null,
                null
        );
    }

    @Test
    void deveIniciarComEstadoPendenteTentativasZeradasESemFinalizacao() {
        UUID correlacaoId = UUID.randomUUID();
        LocalDate dataReferencia = LocalDate.now();

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                correlacaoId,
                null,
                null,
                "PR_DI1",
                dataReferencia,
                MomentoCurva.FECHAMENTO,
                TipoDisparo.AGENDADO,
                "sistema",
                Faixa.ROTINA,
                null,
                null
        );

        assertThat(execucao.id()).isNotNull();
        assertThat(execucao.correlacaoId()).isEqualTo(correlacaoId);
        assertThat(execucao.execucaoPaiId()).isNull();
        assertThat(execucao.definicaoCurvaId()).isNull();
        assertThat(execucao.conjuntoDados()).isEqualTo("PR_DI1");
        assertThat(execucao.dataReferencia()).isEqualTo(dataReferencia);
        assertThat(execucao.momentoCurva()).isEqualTo(MomentoCurva.FECHAMENTO);
        assertThat(execucao.disparo()).isEqualTo(TipoDisparo.AGENDADO);
        assertThat(execucao.disparadoPor()).isEqualTo("sistema");
        assertThat(execucao.faixa()).isEqualTo(Faixa.ROTINA);
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.PENDENTE);
        assertThat(execucao.motivoSemDado()).isNull();
        assertThat(execucao.horarioLimite()).isNull();
        assertThat(execucao.margemSegundos()).isNull();
        assertThat(execucao.duracaoPorEtapaJson()).isNull();
        assertThat(execucao.tentativas()).isZero();
        assertThat(execucao.codigoErro()).isNull();
        assertThat(execucao.mensagemErro()).isNull();
        assertThat(execucao.iniciadoEm()).isNotNull();
        assertThat(execucao.finalizadoEm()).isNull();
    }

    @Test
    void deveExecutarCaminhoFelizAteConclusao() {
        ExecucaoCurva execucao = criarExecucaoPadrao();

        execucao.iniciarExecucao();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.EXECUTANDO);

        execucao.iniciarConstrucao();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.CONSTRUINDO);

        execucao.concluir();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.CONCLUIDA);
        assertThat(execucao.finalizadoEm()).isNotNull();
    }

    @Test
    void deveMarcarSemDadoDiretoDeExecutando() {
        ExecucaoCurva execucao = criarExecucaoPadrao();

        execucao.iniciarExecucao();
        String motivo = "Dados de mercado não disponíveis no feed B3";
        execucao.marcarSemDado(motivo);

        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.SEM_DADO);
        assertThat(execucao.motivoSemDado()).isEqualTo(motivo);
        assertThat(execucao.finalizadoEm()).isNotNull();
    }

    @Test
    void deveLancarExcecaoAoIniciarConstrucaoDiretoDePendente() {
        ExecucaoCurva execucao = criarExecucaoPadrao();

        assertThatThrownBy(execucao::iniciarConstrucao)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDENTE")
                .hasMessageContaining("CONSTRUINDO");
    }

    @Test
    void deveLancarExcecaoAoTentarTransicionarAposConclusao() {
        ExecucaoCurva execucao = criarExecucaoPadrao();
        execucao.iniciarExecucao();
        execucao.iniciarConstrucao();
        execucao.concluir();

        assertThatThrownBy(execucao::iniciarExecucao)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(execucao::iniciarConstrucao)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(execucao::sinalizarEmRisco)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(execucao::marcarAtrasada)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(execucao::concluir)
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> execucao.marcarSemDado("Sem dado tardio"))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> execucao.falhar("ERR_01", "Erro tardio"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveTransicionarPorEmRiscoAtrasadaEFalharComSucesso() {
        ExecucaoCurva execucao = criarExecucaoPadrao();

        execucao.iniciarExecucao();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.EXECUTANDO);

        execucao.sinalizarEmRisco();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.EM_RISCO);

        execucao.marcarAtrasada();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.ATRASADA);

        execucao.falhar("TIMEOUT_GRADE", "Tempo limite excedido para cálculo da grade");
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.FALHOU);
        assertThat(execucao.codigoErro()).isEqualTo("TIMEOUT_GRADE");
        assertThat(execucao.mensagemErro()).isEqualTo("Tempo limite excedido para cálculo da grade");
        assertThat(execucao.finalizadoEm()).isNotNull();
    }

    @Test
    void deveLancarExcecaoQuandoMotivoSemDadoForNuloOuVazio() {
        ExecucaoCurva execucao1 = criarExecucaoPadrao();
        execucao1.iniciarExecucao();
        assertThatThrownBy(() -> execucao1.marcarSemDado(null))
                .isInstanceOf(IllegalArgumentException.class);

        ExecucaoCurva execucao2 = criarExecucaoPadrao();
        execucao2.iniciarExecucao();
        assertThatThrownBy(() -> execucao2.marcarSemDado(""))
                .isInstanceOf(IllegalArgumentException.class);

        ExecucaoCurva execucao3 = criarExecucaoPadrao();
        execucao3.iniciarExecucao();
        assertThatThrownBy(() -> execucao3.marcarSemDado("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveIncrementarTentativasCorretamente() {
        ExecucaoCurva execucao = criarExecucaoPadrao();
        assertThat(execucao.tentativas()).isZero();

        execucao.incrementarTentativa();
        execucao.incrementarTentativa();

        assertThat(execucao.tentativas()).isEqualTo(2);
    }

    @Test
    void deveLancarExcecaoQuandoCamposObrigatoriosForemNulosNoIniciar() {
        assertThatThrownBy(() -> ExecucaoCurva.iniciar(null, null, null, "PR_DI1", LocalDate.now(), MomentoCurva.FECHAMENTO, TipoDisparo.AGENDADO, null, Faixa.ROTINA, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlacaoId");

        assertThatThrownBy(() -> ExecucaoCurva.iniciar(UUID.randomUUID(), null, null, "PR_DI1", LocalDate.now(), MomentoCurva.FECHAMENTO, null, null, Faixa.ROTINA, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disparo");

        assertThatThrownBy(() -> ExecucaoCurva.iniciar(UUID.randomUUID(), null, null, "PR_DI1", LocalDate.now(), MomentoCurva.FECHAMENTO, TipoDisparo.AGENDADO, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("faixa");
    }

    @Test
    void deveLancarExcecaoQuandoCamposErroForemNulosOuVaziosAoFalhar() {
        ExecucaoCurva execucao1 = criarExecucaoPadrao();
        execucao1.iniciarExecucao();
        assertThatThrownBy(() -> execucao1.falhar(null, "Mensagem de erro"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> execucao1.falhar("", "Mensagem de erro"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> execucao1.falhar("   ", "Mensagem de erro"))
                .isInstanceOf(IllegalArgumentException.class);

        ExecucaoCurva execucao2 = criarExecucaoPadrao();
        execucao2.iniciarExecucao();
        assertThatThrownBy(() -> execucao2.falhar("COD_ERRO", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> execucao2.falhar("COD_ERRO", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> execucao2.falhar("COD_ERRO", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
