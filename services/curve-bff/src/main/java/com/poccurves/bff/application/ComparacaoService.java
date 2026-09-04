package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

public class ComparacaoService {

    private final CurveApiPort curveApiClient;
    private final CurveEnginePort engineClient;

    public ComparacaoService(CurveApiPort curveApiClient, CurveEnginePort engineClient) {
        this.curveApiClient = curveApiClient;
        this.engineClient = engineClient;
    }

    public ComparacaoResponse compararCurvas(ComparacaoCurvasRequest request) {
        return curveApiClient.compararCurvas(request);
    }

    public ComparacaoResponse compararModelos(ComparacaoModelosRequest request) {
        return engineClient.compararModelos(request);
    }
}
