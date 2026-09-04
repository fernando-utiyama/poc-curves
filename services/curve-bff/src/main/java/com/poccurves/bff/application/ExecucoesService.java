package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.LocalDate;

public class ExecucoesService {

    private final CurveOrchestratorPort orchestratorClient;

    public ExecucoesService(CurveOrchestratorPort orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    public ExecucoesResponse getExecucoes(String codigoCurva, LocalDate dataReferencia, String estado, int pagina, int tamanho) {
        return orchestratorClient.getExecucoes(codigoCurva, dataReferencia, estado, pagina, tamanho);
    }
}
