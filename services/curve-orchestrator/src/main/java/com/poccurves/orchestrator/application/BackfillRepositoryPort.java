package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.domain.ProgressoBackfill;

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
