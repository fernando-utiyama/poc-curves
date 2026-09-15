package com.poccurves.orchestrator.application.port;
import com.poccurves.orchestrator.application.model.ProgressoBackfill;


import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BackfillRepositoryPort {
    void inserirBackfillExecucao(UUID execucaoCurvaId, LocalDate dataReferenciaFinal, int concorrenciaMaxima);
    void solicitarInterrupcao(UUID execucaoCurvaId);
    boolean interrupcaoSolicitada(UUID execucaoCurvaId);
    Optional<LocalDate> buscarDataReferenciaFinal(UUID execucaoCurvaId);
    ProgressoBackfill calcularProgresso(UUID execucaoMaeId);
}
