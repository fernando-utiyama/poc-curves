package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CurveOrchestratorPort {

    DisparoManualResponse disparoManual(DisparoManualRequest request);

    BackfillResponse backfill(BackfillRequest request);

    ExecucoesResponse getExecucoes(String codigoCurva, LocalDate dataReferencia, String estado, int pagina, int tamanho);

    Optional<ExecucaoResumoDTO> getUltimaExecucaoCurva(String codigoCurva, LocalDate dataReferencia, String momento);

    PendenciasSumarioResponse getPendenciasSumario();

    PendenciasDetalheResponse getPendenciasDetalhe(UUID grupoId, int pagina, int tamanho);

    AcaoPendenciaResponse reprocessarPendencias(UUID grupoId, UUID pendenciaId);

    AcaoPendenciaResponse descartarPendencias(UUID grupoId, UUID pendenciaId, String justificativa);

    CargaManualResponse cargaManualCurva(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            String justificativa,
            byte[] fileBytes,
            String fileName
    );
}
