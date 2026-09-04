package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.application.AquisicaoExecutionService.ExecutionResult;
import com.poccurves.orchestrator.application.FunctionMarketdataPort.ResultadoAquisicao;
import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DisparoAgendadoExecutorTest {

    private AgendamentoRepositoryPort agendamentoRepository;
    private ExecucaoCurvaRepositoryPort execucaoCurvaRepository;
    private AquisicaoExecutionService aquisicaoExecutionService;
    private DisparoAgendadoExecutor executor;

    @BeforeEach
    void setUp() {
        agendamentoRepository = mock(AgendamentoRepositoryPort.class);
        execucaoCurvaRepository = mock(ExecucaoCurvaRepositoryPort.class);
        aquisicaoExecutionService = mock(AquisicaoExecutionService.class);
        executor = new DisparoAgendadoExecutor(agendamentoRepository, execucaoCurvaRepository, aquisicaoExecutionService);
    }

    @Test
    void diaSemPregaoNaoCriaExecucao() {
        Agendamento a = Agendamento.reconstituir(
                UUID.randomUUID(), null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, true, "teste", null, null
        );
        when(agendamentoRepository.buscarPorId(a.id())).thenReturn(Optional.of(a));

        try (var mockedCalendario = mockStatic(com.poccurves.orchestrator.domain.CalendarioPregao.class)) {
            mockedCalendario.when(() -> com.poccurves.orchestrator.domain.CalendarioPregao.ehDiaDePregao(any())).thenReturn(false);

            executor.executar(a.id());

            verify(execucaoCurvaRepository, never()).inserir(any());
        }
    }

    @Test
    void noDataDentroDaJanelaTentaDeNovoEDepoisSucesso() {
        Agendamento a = Agendamento.reconstituir(
                UUID.randomUUID(), null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 10, 1, true, "teste", null, null
        );
        when(agendamentoRepository.buscarPorId(a.id())).thenReturn(Optional.of(a));
        when(execucaoCurvaRepository.buscarExecucaoAtivaParaConjuntoDados(any(), any(), any())).thenReturn(Optional.empty());

        ResultadoAquisicao rNoData = new ResultadoAquisicao("NO_DATA", null, 0, "Nao tem", null);
        ResultadoAquisicao rPublished = new ResultadoAquisicao("PUBLISHED", "lote1", 1, null, null);

        when(aquisicaoExecutionService.acionarFeederEEncadear(any(), any(), any(), any(), any()))
                .thenReturn(new ExecutionResult(rNoData, "SEM_DADO", "msg", false))
                .thenReturn(new ExecutionResult(rPublished, "EM_PROCESSAMENTO", "msg", false));

        try (var mockedCalendario = mockStatic(com.poccurves.orchestrator.domain.CalendarioPregao.class)) {
            mockedCalendario.when(() -> com.poccurves.orchestrator.domain.CalendarioPregao.ehDiaDePregao(any())).thenReturn(true);

            executor.executar(a.id());

            verify(aquisicaoExecutionService, times(2)).acionarFeederEEncadear(any(), any(), any(), any(), any());
            verify(execucaoCurvaRepository, atLeastOnce()).atualizar(any(ExecucaoCurva.class));
        }
    }

    @Test
    void janelaEsgotadaMarcaSemDado() {
        Agendamento a = Agendamento.reconstituir(
                UUID.randomUUID(), null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 0, 1, true, "teste", null, null // janela 0 para forçar expiração no primeiro no_data
        );
        when(agendamentoRepository.buscarPorId(a.id())).thenReturn(Optional.of(a));
        when(execucaoCurvaRepository.buscarExecucaoAtivaParaConjuntoDados(any(), any(), any())).thenReturn(Optional.empty());

        ResultadoAquisicao rNoData = new ResultadoAquisicao("NO_DATA", null, 0, "Nao tem", null);
        when(aquisicaoExecutionService.acionarFeederEEncadear(any(), any(), any(), any(), any()))
                .thenReturn(new ExecutionResult(rNoData, "SEM_DADO", "msg", false));

        try (var mockedCalendario = mockStatic(com.poccurves.orchestrator.domain.CalendarioPregao.class)) {
            mockedCalendario.when(() -> com.poccurves.orchestrator.domain.CalendarioPregao.ehDiaDePregao(any())).thenReturn(true);

            executor.executar(a.id());

            ArgumentCaptor<ExecucaoCurva> captor = ArgumentCaptor.forClass(ExecucaoCurva.class);
            verify(execucaoCurvaRepository, atLeastOnce()).atualizar(captor.capture());

            ExecucaoCurva ultima = captor.getValue();
            assertThat(ultima.estado()).isEqualTo(EstadoExecucao.SEM_DADO);
        }
    }
}
