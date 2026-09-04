package com.poccurves.orchestrator.config;

import com.poccurves.orchestrator.application.AgendamentoRepositoryPort;
import com.poccurves.orchestrator.application.AgendamentoSchedulerPort;
import com.poccurves.orchestrator.application.AgendamentoService;
import com.poccurves.orchestrator.application.AquisicaoExecutionService;
import com.poccurves.orchestrator.application.BackfillDispatcher;
import com.poccurves.orchestrator.application.BackfillRepositoryPort;
import com.poccurves.orchestrator.application.BackfillService;
import com.poccurves.orchestrator.application.BuildRequestPort;
import com.poccurves.orchestrator.application.CargaManualService;
import com.poccurves.orchestrator.application.CurveProcessorCargaManualPort;
import com.poccurves.orchestrator.application.DefinicaoCurvaConsultaRepositoryPort;
import com.poccurves.orchestrator.application.DisparoAgendadoExecutor;
import com.poccurves.orchestrator.application.DisparoManualService;
import com.poccurves.orchestrator.application.ExecucaoCurvaRepositoryPort;
import com.poccurves.orchestrator.application.ExecucoesService;
import com.poccurves.orchestrator.application.FeederAcquisitionPort;
import com.poccurves.orchestrator.application.MaterializarPendenciaDlqUseCase;
import com.poccurves.orchestrator.application.PendenciaDlqRepositoryPort;
import com.poccurves.orchestrator.application.ReconciliacaoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
public class UseCaseConfig {

    @Bean
    public AgendamentoService agendamentoService(
            AgendamentoRepositoryPort agendamentoRepository,
            AgendamentoSchedulerPort schedulerRegistry) {
        return new AgendamentoService(agendamentoRepository, schedulerRegistry);
    }

    @Bean
    public AquisicaoExecutionService aquisicaoExecutionService(
            FeederAcquisitionPort feederAcquisitionClient,
            DefinicaoCurvaConsultaRepositoryPort definicaoCurvaConsultaRepository,
            BuildRequestPort buildRequestPublisher,
            @Value("${resiliencia.retentativas-maximas:3}") int maxRetentativas) {
        return new AquisicaoExecutionService(feederAcquisitionClient, definicaoCurvaConsultaRepository, buildRequestPublisher, maxRetentativas);
    }

    @Bean
    public DisparoAgendadoExecutor disparoAgendadoExecutor(
            AgendamentoRepositoryPort agendamentoRepository,
            ExecucaoCurvaRepositoryPort execucaoCurvaRepository,
            AquisicaoExecutionService aquisicaoExecutionService) {
        return new DisparoAgendadoExecutor(agendamentoRepository, execucaoCurvaRepository, aquisicaoExecutionService);
    }

    @Bean
    public BackfillDispatcher backfillDispatcher(
            ExecutorService backfillExecutor,
            ExecucaoCurvaRepositoryPort execucaoCurvaRepository,
            BackfillRepositoryPort backfillRepository,
            AquisicaoExecutionService aquisicaoExecutionService) {
        return new BackfillDispatcher(backfillExecutor, execucaoCurvaRepository, backfillRepository, aquisicaoExecutionService);
    }

    @Bean
    public BackfillService backfillService(
            ExecucaoCurvaRepositoryPort execucaoCurvaRepository,
            BackfillRepositoryPort backfillRepository,
            BackfillDispatcher backfillDispatcher,
            @Value("${backfill.concorrencia-maxima-padrao:5}") int concorrenciaMaximaPadrao) {
        return new BackfillService(execucaoCurvaRepository, backfillRepository, backfillDispatcher, concorrenciaMaximaPadrao);
    }

    @Bean
    public CargaManualService cargaManualService(
            ExecucaoCurvaRepositoryPort execucaoRepository,
            DefinicaoCurvaConsultaRepositoryPort definicaoCurvaConsultaRepository,
            CurveProcessorCargaManualPort curveProcessorCargaManualClient) {
        return new CargaManualService(execucaoRepository, definicaoCurvaConsultaRepository, curveProcessorCargaManualClient);
    }

    @Bean
    public ExecucoesService execucoesService(ExecucaoCurvaRepositoryPort repository) {
        return new ExecucoesService(repository);
    }

    @Bean
    public DisparoManualService disparoManualService(
            ExecucaoCurvaRepositoryPort execucaoRepository,
            AquisicaoExecutionService aquisicaoExecutionService) {
        return new DisparoManualService(execucaoRepository, aquisicaoExecutionService);
    }

    @Bean
    public MaterializarPendenciaDlqUseCase materializarPendenciaDlqUseCase(PendenciaDlqRepositoryPort pendenciaDlqRepository) {
        return new MaterializarPendenciaDlqUseCase(pendenciaDlqRepository);
    }

    @Bean
    public ReconciliacaoService reconciliacaoService(ExecucaoCurvaRepositoryPort execucaoCurvaRepository) {
        return new ReconciliacaoService(execucaoCurvaRepository);
    }
}
