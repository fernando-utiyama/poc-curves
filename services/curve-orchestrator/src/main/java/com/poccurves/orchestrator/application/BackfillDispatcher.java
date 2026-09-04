package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.CalendarioPregao;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import com.poccurves.orchestrator.domain.TipoDisparo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * Despacha as execuções filhas de um backfill.
 * <p>
 * O contorno da máquina de estados (avançar para CONSTRUINDO e depois CONCLUIDA na mãe)
 * ao final do despacho segue o mesmo precedente estabelecido em {@code CargaManualService}.
 * O estado CONCLUIDA na execução-mãe significa "terminou de despachar", e não
 * necessariamente que toda filha teve sucesso. O resultado real fica na consulta de progresso.
 */
public class BackfillDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BackfillDispatcher.class);

    private final ExecutorService backfillExecutor;
    private final ExecucaoCurvaRepositoryPort execucaoCurvaRepository;
    private final BackfillRepositoryPort backfillRepository;
    private final AquisicaoExecutionService aquisicaoExecutionService;

    public BackfillDispatcher(
            ExecutorService backfillExecutor,
            ExecucaoCurvaRepositoryPort execucaoCurvaRepository,
            BackfillRepositoryPort backfillRepository,
            AquisicaoExecutionService aquisicaoExecutionService
    ) {
        this.backfillExecutor = backfillExecutor;
        this.execucaoCurvaRepository = execucaoCurvaRepository;
        this.backfillRepository = backfillRepository;
        this.aquisicaoExecutionService = aquisicaoExecutionService;
    }

    public void despachar(
            UUID execucaoMaeId,
            String conjuntoDados,
            LocalDate dataInicial,
            LocalDate dataFinal,
            int concorrenciaMaxima,
            UUID correlationIdLote
    ) {
        backfillExecutor.submit(() -> {
            Semaphore semaphore = new Semaphore(concorrenciaMaxima);

            try {
                for (LocalDate data = dataInicial; !data.isAfter(dataFinal); data = data.plusDays(1)) {
                    if (!CalendarioPregao.ehDiaDePregao(data)) {
                        continue;
                    }

                    if (backfillRepository.interrupcaoSolicitada(execucaoMaeId)) {
                        log.info("Interrupção solicitada para backfill mãe {}. Parando despacho.", execucaoMaeId);
                        break;
                    }

                    semaphore.acquire();

                    final LocalDate dataFilha = data;
                    backfillExecutor.submit(() -> {
                        try {
                            processarFilha(execucaoMaeId, conjuntoDados, dataFilha, correlationIdLote);
                        } finally {
                            semaphore.release();
                        }
                    });
                }

                // Aguarda todas as filhas submetidas terminarem
                semaphore.acquire(concorrenciaMaxima);

                finalizarMae(execucaoMaeId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread de despacho do backfill {} foi interrompida", execucaoMaeId, e);
            }
        });
    }

    private void processarFilha(UUID execucaoMaeId, String conjuntoDados, LocalDate data, UUID correlationIdLote) {
        ExecucaoCurva filha = ExecucaoCurva.iniciar(
                UUID.randomUUID(),
                execucaoMaeId,
                null,
                conjuntoDados,
                data,
                MomentoCurva.INTRADIA,
                TipoDisparo.BACKFILL,
                "sistema:backfill",
                Faixa.MASSA,
                null,
                null
        );
        execucaoCurvaRepository.inserir(filha);
        filha.iniciarExecucao();
        execucaoCurvaRepository.atualizar(filha);

        AquisicaoExecutionService.ExecutionResult result = aquisicaoExecutionService.acionarFeederEEncadear(
                filha, conjuntoDados, data, Faixa.MASSA, correlationIdLote
        );

        if (result.resultado() != null && "NO_DATA".equals(result.resultado().kind())) {
            filha.marcarSemDado(result.resultado().motivo());
            execucaoCurvaRepository.atualizar(filha);
        } else if (result.erroTransporte() || "FALHA".equals(result.progressoStatus())) {
            // aquisicaoExecutionService.acionarFeederEEncadear já chama filha.falhar() internamente.
            // Apenas precisamos salvar a alteração
            execucaoCurvaRepository.atualizar(filha);
        } else if ("EM_PROCESSAMENTO".equals(result.progressoStatus())) {
             // O aquisicaoExecutionService também muda estados como iniciarConstrucao se publicou com sucesso.
             execucaoCurvaRepository.atualizar(filha);
        }
    }

    private void finalizarMae(UUID execucaoMaeId) {
        Optional<ExecucaoCurva> maeOpt = execucaoCurvaRepository.buscarPorId(execucaoMaeId);
        if (maeOpt.isPresent()) {
            ExecucaoCurva mae = maeOpt.get();
            mae.iniciarConstrucao();
            mae.concluir();
            execucaoCurvaRepository.atualizar(mae);
        }
    }
}
