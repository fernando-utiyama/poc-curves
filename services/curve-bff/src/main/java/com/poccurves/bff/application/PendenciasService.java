package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.util.UUID;

public class PendenciasService {

    private final CurveOrchestratorPort orchestratorClient;

    public PendenciasService(CurveOrchestratorPort orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    public PendenciasSumarioResponse getSumario() {
        return orchestratorClient.getPendenciasSumario();
    }

    public PendenciasDetalheResponse getDetalhe(UUID grupoId, int pagina, int tamanho) {
        return orchestratorClient.getPendenciasDetalhe(grupoId, pagina, tamanho);
    }

    public AcaoPendenciaResponse reprocessar(UUID grupoId, UUID pendenciaId) {
        return orchestratorClient.reprocessarPendencias(grupoId, pendenciaId);
    }

    public AcaoPendenciaResponse descartar(UUID grupoId, UUID pendenciaId, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("Justificativa é obrigatória para o descarte de pendências.");
        }
        return orchestratorClient.descartarPendencias(grupoId, pendenciaId, justificativa);
    }
}
