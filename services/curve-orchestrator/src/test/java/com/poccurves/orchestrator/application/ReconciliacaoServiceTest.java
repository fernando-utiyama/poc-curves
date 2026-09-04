package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliacaoServiceTest {

    private ExecucaoCurva criar(TipoDisparo disparo) {
        return ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, disparo, "teste", Faixa.ROTINA, null, null
        );
    }

    @Test
    void naoFazNadaQuandoNaoHaExecucaoPresa() {
        ExecucaoCurvaRepositoryPort repository = mock(ExecucaoCurvaRepositoryPort.class);
        when(repository.buscarExecucoesEmAndamento()).thenReturn(List.of());

        int quantidade = new ReconciliacaoService(repository).reconciliarExecucoesPresas();

        assertThat(quantidade).isZero();
        verify(repository, never()).atualizar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void marcaExecucaoPendenteComoFalhaPassandoPorExecutandoPrimeiro() {
        ExecucaoCurvaRepositoryPort repository = mock(ExecucaoCurvaRepositoryPort.class);
        ExecucaoCurva pendente = criar(TipoDisparo.MANUAL);
        assertThat(pendente.estado()).isEqualTo(EstadoExecucao.PENDENTE);
        when(repository.buscarExecucoesEmAndamento()).thenReturn(List.of(pendente));

        int quantidade = new ReconciliacaoService(repository).reconciliarExecucoesPresas();

        assertThat(quantidade).isEqualTo(1);
        assertThat(pendente.estado()).isEqualTo(EstadoExecucao.FALHOU);
        verify(repository).atualizar(pendente);
    }

    @Test
    void marcaExecucaoEmExecutandoComoFalhaDiretamente() {
        ExecucaoCurvaRepositoryPort repository = mock(ExecucaoCurvaRepositoryPort.class);
        ExecucaoCurva executando = criar(TipoDisparo.AGENDADO);
        executando.iniciarExecucao();
        when(repository.buscarExecucoesEmAndamento()).thenReturn(List.of(executando));

        new ReconciliacaoService(repository).reconciliarExecucoesPresas();

        assertThat(executando.estado()).isEqualTo(EstadoExecucao.FALHOU);
        assertThat(executando.codigoErro()).isEqualTo("SERVICO_REINICIADO");
        verify(repository).atualizar(executando);
    }
}
