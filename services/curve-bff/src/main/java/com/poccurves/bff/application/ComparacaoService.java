package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

public class ComparacaoService {

    private final CurveApiPort curveApiClient;

    public ComparacaoService(CurveApiPort curveApiClient) {
        this.curveApiClient = curveApiClient;
    }

    public ComparacaoResponse compararCurvas(ComparacaoCurvasRequest request) {
        return curveApiClient.compararCurvas(request);
    }
}
