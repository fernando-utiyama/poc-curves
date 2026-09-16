package com.poccurves.orchestrator.config;
import com.poccurves.orchestrator.application.port.AgendamentoRepositoryPort;
import com.poccurves.orchestrator.application.port.AgendamentoSchedulerPort;
import com.poccurves.orchestrator.application.port.BackfillRepositoryPort;
import com.poccurves.orchestrator.application.port.BuildRequestPort;
import com.poccurves.orchestrator.application.port.ConstrucaoCurvaB3Port;
import com.poccurves.orchestrator.application.port.CurveProcessorCargaManualPort;
import com.poccurves.orchestrator.application.port.DefinicaoCurvaConsultaRepositoryPort;
import com.poccurves.orchestrator.application.port.ExecucaoCurvaRepositoryPort;
import com.poccurves.orchestrator.application.port.FunctionMarketdataPort;
import com.poccurves.orchestrator.application.port.PendenciaDlqRepositoryPort;
import com.poccurves.orchestrator.application.service.AquisicaoExecutionService;
import com.poccurves.orchestrator.application.service.BackfillDispatcher;
import com.poccurves.orchestrator.application.usecase.AgendamentoService;
import com.poccurves.orchestrator.application.usecase.BackfillService;
import com.poccurves.orchestrator.application.usecase.CargaManualService;
import com.poccurves.orchestrator.application.usecase.DisparoAgendadoExecutor;
import com.poccurves.orchestrator.application.usecase.DisparoManualService;
import com.poccurves.orchestrator.application.usecase.ExecucoesService;
import com.poccurves.orchestrator.application.usecase.MaterializarPendenciaDlqUseCase;
import com.poccurves.orchestrator.application.usecase.ReconciliacaoAgendamentosService;
import com.poccurves.orchestrator.application.usecase.ReconciliacaoService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
public class UseCaseConfig {

    @Bean
    public AgendamentoService agendamentoService(AgendamentoRepositoryPort agendamentoRepository) {
        return new AgendamentoService(agendamentoRepository);
    }

    @Bean
    public ReconciliacaoAgendamentosService reconciliacaoAgendamentosService(
            AgendamentoRepositoryPort agendamentoRepository,
            AgendamentoSchedulerPort schedulerRegistry) {
        return new ReconciliacaoAgendamentosService(agendamentoRepository, schedulerRegistry);
    }

    @Bean
    public AquisicaoExecutionService aquisicaoExecutionService(
            FunctionMarketdataPort functionMarketdataClient,
            DefinicaoCurvaConsultaRepositoryPort definicaoCurvaConsultaRepository,
            BuildRequestPort buildRequestPublisher,
            ConstrucaoCurvaB3Port construcaoCurvaB3Publisher,
            @Value("${resiliencia.retentativas-maximas:3}") int maxRetentativas) {
        return new AquisicaoExecutionService(
                functionMarketdataClient, definicaoCurvaConsultaRepository, buildRequestPublisher,
                construcaoCurvaB3Publisher, maxRetentativas);
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
