package com.poccurves.orchestrator.application.usecase;
import com.poccurves.orchestrator.application.model.EstadoExecucao;
import com.poccurves.orchestrator.application.model.ExecucaoCurva;
import com.poccurves.orchestrator.application.model.Faixa;
import com.poccurves.orchestrator.application.model.MomentoCurva;
import com.poccurves.orchestrator.application.model.TipoDisparo;
import com.poccurves.orchestrator.application.port.ExecucaoCurvaRepositoryPort;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliacaoServiceTest {

    private static final Duration LIMITE_TESTE = Duration.ofMinutes(60);

    private ExecucaoCurva criar(TipoDisparo disparo) {
        return ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, disparo, "teste", Faixa.ROTINA, null, null
        );
    }

    @Test
    void naoFazNadaQuandoNaoHaExecucaoPresa() {
        ExecucaoCurvaRepositoryPort repository = mock(ExecucaoCurvaRepositoryPort.class);
        when(repository.buscarExecucoesEmAndamento(any(Instant.class))).thenReturn(List.of());

        int quantidade = new ReconciliacaoService(repository).reconciliarExecucoesPresas(LIMITE_TESTE);

        assertThat(quantidade).isZero();
        verify(repository, never()).atualizar(any());
    }

    @Test
    void marcaExecucaoPendenteComoFalhaPassandoPorExecutandoPrimeiro() {
        ExecucaoCurvaRepositoryPort repository = mock(ExecucaoCurvaRepositoryPort.class);
        ExecucaoCurva pendente = criar(TipoDisparo.MANUAL);
        assertThat(pendente.estado()).isEqualTo(EstadoExecucao.PENDENTE);
        when(repository.buscarExecucoesEmAndamento(any(Instant.class))).thenReturn(List.of(pendente));

        int quantidade = new ReconciliacaoService(repository).reconciliarExecucoesPresas(LIMITE_TESTE);

        assertThat(quantidade).isEqualTo(1);
        assertThat(pendente.estado()).isEqualTo(EstadoExecucao.FALHOU);
        verify(repository).atualizar(pendente);
    }

    @Test
    void marcaExecucaoEmExecutandoComoFalhaDiretamente() {
        ExecucaoCurvaRepositoryPort repository = mock(ExecucaoCurvaRepositoryPort.class);
        ExecucaoCurva executando = criar(TipoDisparo.AGENDADO);
        executando.iniciarExecucao();
        when(repository.buscarExecucoesEmAndamento(any(Instant.class))).thenReturn(List.of(executando));

        new ReconciliacaoService(repository).reconciliarExecucoesPresas(LIMITE_TESTE);

        assertThat(executando.estado()).isEqualTo(EstadoExecucao.FALHOU);
        assertThat(executando.codigoErro()).isEqualTo("SERVICO_REINICIADO");
        verify(repository).atualizar(executando);
    }

    @Test
    void calculaOInstanteLimiteAPartirDoLimiteDeAntiguidade() {
        ExecucaoCurvaRepositoryPort repository = mock(ExecucaoCurvaRepositoryPort.class);
        when(repository.buscarExecucoesEmAndamento(any(Instant.class))).thenReturn(List.of());

        Instant antes = Instant.now();
        new ReconciliacaoService(repository).reconciliarExecucoesPresas(LIMITE_TESTE);
        Instant depois = Instant.now();

        org.mockito.ArgumentCaptor<Instant> captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).buscarExecucoesEmAndamento(captor.capture());
        Instant limiteUsado = captor.getValue();

        assertThat(limiteUsado).isAfterOrEqualTo(antes.minus(LIMITE_TESTE).minusSeconds(1));
        assertThat(limiteUsado).isBeforeOrEqualTo(depois.minus(LIMITE_TESTE).plusSeconds(1));
    }
}
