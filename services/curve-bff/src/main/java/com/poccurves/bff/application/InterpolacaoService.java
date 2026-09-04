package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

public class InterpolacaoService {

    private final CurveEnginePort engineClient;

    public InterpolacaoService(CurveEnginePort engineClient) {
        this.engineClient = engineClient;
    }

    public InterpolacaoResponse interpolar(String codigo, InterpolacaoRequest request) {
        return engineClient.interpolar(codigo, request);
    }
}
