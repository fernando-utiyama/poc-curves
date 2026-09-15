package com.poccurves.orchestrator.application.usecase;
import com.poccurves.orchestrator.application.model.ExecucaoCurva;
import com.poccurves.orchestrator.application.model.Faixa;
import com.poccurves.orchestrator.application.model.MomentoCurva;
import com.poccurves.orchestrator.application.model.ProgressoBackfill;
import com.poccurves.orchestrator.application.model.TipoDisparo;
import com.poccurves.orchestrator.application.port.BackfillRepositoryPort;
import com.poccurves.orchestrator.application.port.ExecucaoCurvaRepositoryPort;
import com.poccurves.orchestrator.application.service.BackfillDispatcher;


import java.time.LocalDate;
import java.util.UUID;

public class BackfillService {

    private final ExecucaoCurvaRepositoryPort execucaoCurvaRepository;
    private final BackfillRepositoryPort backfillRepository;
    private final BackfillDispatcher backfillDispatcher;
    private final int concorrenciaMaximaPadrao;

    public BackfillService(
            ExecucaoCurvaRepositoryPort execucaoCurvaRepository,
            BackfillRepositoryPort backfillRepository,
            BackfillDispatcher backfillDispatcher,
            int concorrenciaMaximaPadrao
    ) {
        this.execucaoCurvaRepository = execucaoCurvaRepository;
        this.backfillRepository = backfillRepository;
        this.backfillDispatcher = backfillDispatcher;
        this.concorrenciaMaximaPadrao = concorrenciaMaximaPadrao;
    }

    /**
     * Deliberadamente sem transação própria: o despacho assíncrono (chamado no fim deste
     * método) roda numa thread própria com sua própria conexão, e precisa enxergar a execução-mãe
     * e a linha de {@code backfill_execucao} já commitadas -- com uma transação envolvendo este
     * método, o commit só aconteceria quando ele retornasse (via proxy do Spring), depois de
     * {@link BackfillDispatcher#despachar} já ter sido chamado, criando uma corrida real entre o
     * commit e a primeira leitura da thread de despacho. Sem transação, cada INSERT via
     * {@code JdbcTemplate} já commita sozinho antes do despacho começar.
     */
    public ExecucaoCurva iniciar(
            String conjuntoDados,
            LocalDate dataInicial,
            LocalDate dataFinal,
            Integer concorrenciaMaximaOpcional,
            String disparadoPor
    ) {
        if (conjuntoDados == null || conjuntoDados.isBlank()) {
            throw new IllegalArgumentException("conjuntoDados não pode ser vazio");
        }
        if (dataInicial == null || dataFinal == null) {
            throw new IllegalArgumentException("dataInicial e dataFinal são obrigatórios");
        }
        if (dataInicial.isAfter(dataFinal)) {
            throw new IllegalArgumentException("dataInicial não pode ser posterior a dataFinal");
        }

        int concorrenciaMaxima = (concorrenciaMaximaOpcional != null && concorrenciaMaximaOpcional > 0)
                ? concorrenciaMaximaOpcional
                : concorrenciaMaximaPadrao;

        ExecucaoCurva mae = ExecucaoCurva.iniciar(
                UUID.randomUUID(),
                null,
                null,
                conjuntoDados,
                dataInicial,
                MomentoCurva.INTRADIA,
                TipoDisparo.BACKFILL,
                disparadoPor,
                Faixa.MASSA,
                null,
                null
        );

        execucaoCurvaRepository.inserir(mae);
        mae.iniciarExecucao();
        execucaoCurvaRepository.atualizar(mae);

        backfillRepository.inserirBackfillExecucao(mae.id(), dataFinal, concorrenciaMaxima);

        backfillDispatcher.despachar(
                mae.id(),
                conjuntoDados,
                dataInicial,
                dataFinal,
                concorrenciaMaxima,
                mae.correlacaoId()
        );

        return mae;
    }

    public void interromper(UUID execucaoMaeId) {
        backfillRepository.solicitarInterrupcao(execucaoMaeId);
    }

    public ProgressoBackfill progresso(UUID execucaoMaeId) {
        return backfillRepository.calcularProgresso(execucaoMaeId);
    }
}
