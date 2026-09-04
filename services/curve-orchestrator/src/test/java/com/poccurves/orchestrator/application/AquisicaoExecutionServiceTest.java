package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.application.FeederAcquisitionPort.ResultadoAquisicao;
import com.poccurves.orchestrator.domain.DefinicaoConsumidora;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.IntegracaoIndisponivelException;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AquisicaoExecutionServiceTest {

    @Test
    void acionarFeederEEncadearTrataNoData() {
        FeederAcquisitionPort feeder = mock(FeederAcquisitionPort.class);
        DefinicaoCurvaConsultaRepositoryPort consulta = mock(DefinicaoCurvaConsultaRepositoryPort.class);
        BuildRequestPort publisher = mock(BuildRequestPort.class);

        AquisicaoExecutionService service = new AquisicaoExecutionService(feeder, consulta, publisher, 1);

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.AGENDADO, "sistema", Faixa.ROTINA, null, null
        );
        execucao.iniciarExecucao();

        when(feeder.acionar(any(), any(), any(), any()))
                .thenReturn(new ResultadoAquisicao("NO_DATA", null, 0, "motivo", null));

        AquisicaoExecutionService.ExecutionResult result = service.acionarFeederEEncadear(
                execucao, "CONJUNTO", LocalDate.now(), Faixa.ROTINA, UUID.randomUUID()
        );

        assertThat(result.resultado().kind()).isEqualTo("NO_DATA");
        assertThat(result.progressoStatus()).isEqualTo("SEM_DADO");
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.EXECUTANDO);
    }

    @Test
    void acionarFeederEEncadearTrataPublishedComConsumidoraEIniciaConstrucao() {
        FeederAcquisitionPort feeder = mock(FeederAcquisitionPort.class);
        DefinicaoCurvaConsultaRepositoryPort consulta = mock(DefinicaoCurvaConsultaRepositoryPort.class);
        BuildRequestPort publisher = mock(BuildRequestPort.class);

        AquisicaoExecutionService service = new AquisicaoExecutionService(feeder, consulta, publisher, 1);

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.AGENDADO, "sistema", Faixa.ROTINA, null, null
        );
        execucao.iniciarExecucao();

        when(feeder.acionar(any(), any(), any(), any()))
                .thenReturn(new ResultadoAquisicao("PUBLISHED", "lote1", 3, null, null));
        DefinicaoConsumidora consumidora =
                new DefinicaoConsumidora(UUID.randomUUID(), "PRE_DI1", java.time.LocalTime.of(18, 0));
        when(consulta.buscarDefinicoesBootstrappedQueConsomem(any(), any())).thenReturn(List.of(consumidora));

        AquisicaoExecutionService.ExecutionResult result = service.acionarFeederEEncadear(
                execucao, "CONJUNTO", LocalDate.now(), Faixa.ROTINA, UUID.randomUUID()
        );

        assertThat(result.resultado().kind()).isEqualTo("PUBLISHED");
        assertThat(result.progressoStatus()).isEqualTo("EM_PROCESSAMENTO");
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.CONSTRUINDO);
        verify(publisher).publicar(eq("PRE_DI1"), any(), any(), any(), any(), any());
    }

    @Test
    void acionarFeederEEncadearTrataFailed() {
        FeederAcquisitionPort feeder = mock(FeederAcquisitionPort.class);
        DefinicaoCurvaConsultaRepositoryPort consulta = mock(DefinicaoCurvaConsultaRepositoryPort.class);
        BuildRequestPort publisher = mock(BuildRequestPort.class);

        AquisicaoExecutionService service = new AquisicaoExecutionService(feeder, consulta, publisher, 1);

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.AGENDADO, "sistema", Faixa.ROTINA, null, null
        );
        execucao.iniciarExecucao();

        when(feeder.acionar(any(), any(), any(), any()))
                .thenReturn(new ResultadoAquisicao("FAILED", null, 0, "motivo ruim", "diagnostico"));

        AquisicaoExecutionService.ExecutionResult result = service.acionarFeederEEncadear(
                execucao, "CONJUNTO", LocalDate.now(), Faixa.ROTINA, UUID.randomUUID()
        );

        assertThat(result.progressoStatus()).isEqualTo("FALHA");
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.FALHOU);
    }

    @Test
    void acionarFeederEEncadearTrataFalhaDeTransporte() {
        FeederAcquisitionPort feeder = mock(FeederAcquisitionPort.class);
        DefinicaoCurvaConsultaRepositoryPort consulta = mock(DefinicaoCurvaConsultaRepositoryPort.class);
        BuildRequestPort publisher = mock(BuildRequestPort.class);

        AquisicaoExecutionService service = new AquisicaoExecutionService(feeder, consulta, publisher, 1);

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.AGENDADO, "sistema", Faixa.ROTINA, null, null
        );
        execucao.iniciarExecucao();

        when(feeder.acionar(any(), any(), any(), any()))
                .thenThrow(new IntegracaoIndisponivelException("conexao recusada", null));

        AquisicaoExecutionService.ExecutionResult result = service.acionarFeederEEncadear(
                execucao, "CONJUNTO", LocalDate.now(), Faixa.ROTINA, UUID.randomUUID()
        );

        assertThat(result.erroTransporte()).isTrue();
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.FALHOU);
    }

    @Test
    void acionarFeederEEncadearRetentaComSucessoNaSegundaTentativa() {
        FeederAcquisitionPort feeder = mock(FeederAcquisitionPort.class);
        DefinicaoCurvaConsultaRepositoryPort consulta = mock(DefinicaoCurvaConsultaRepositoryPort.class);
        BuildRequestPort publisher = mock(BuildRequestPort.class);

        AquisicaoExecutionService service = new AquisicaoExecutionService(feeder, consulta, publisher, 2);

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.AGENDADO, "sistema", Faixa.ROTINA, null, null
        );
        execucao.iniciarExecucao();

        when(feeder.acionar(any(), any(), any(), any()))
                .thenThrow(new IntegracaoIndisponivelException("conexao recusada", null))
                .thenReturn(new ResultadoAquisicao("PUBLISHED", "lote1", 3, null, null));

        when(consulta.buscarDefinicoesBootstrappedQueConsomem(any(), any())).thenReturn(List.of());

        AquisicaoExecutionService.ExecutionResult result = service.acionarFeederEEncadear(
                execucao, "CONJUNTO", LocalDate.now(), Faixa.ROTINA, UUID.randomUUID()
        );

        assertThat(result.resultado().kind()).isEqualTo("PUBLISHED");
        assertThat(result.progressoStatus()).isEqualTo("EM_PROCESSAMENTO");
        assertThat(execucao.tentativas()).isEqualTo(1); // Incrementou na primeira falha
    }

    @Test
    void acionarFeederEEncadearEsgotaRetentativasEFalha() {
        FeederAcquisitionPort feeder = mock(FeederAcquisitionPort.class);
        DefinicaoCurvaConsultaRepositoryPort consulta = mock(DefinicaoCurvaConsultaRepositoryPort.class);
        BuildRequestPort publisher = mock(BuildRequestPort.class);

        AquisicaoExecutionService service = new AquisicaoExecutionService(feeder, consulta, publisher, 2);

        ExecucaoCurva execucao = ExecucaoCurva.iniciar(
                UUID.randomUUID(), null, null, "CONJUNTO", LocalDate.now(),
                MomentoCurva.INTRADIA, TipoDisparo.AGENDADO, "sistema", Faixa.ROTINA, null, null
        );
        execucao.iniciarExecucao();

        when(feeder.acionar(any(), any(), any(), any()))
                .thenThrow(new IntegracaoIndisponivelException("conexao recusada 1", null))
                .thenThrow(new IntegracaoIndisponivelException("conexao recusada 2", null));

        AquisicaoExecutionService.ExecutionResult result = service.acionarFeederEEncadear(
                execucao, "CONJUNTO", LocalDate.now(), Faixa.ROTINA, UUID.randomUUID()
        );

        assertThat(result.erroTransporte()).isTrue();
        assertThat(result.progressoStatus()).isEqualTo("FALHA");
        assertThat(execucao.estado()).isEqualTo(EstadoExecucao.FALHOU);
        assertThat(execucao.tentativas()).isEqualTo(2); // Incrementou na primeira e na segunda falha (esgotamento)
    }
}
