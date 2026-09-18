package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.util.NoSuchElementException;

public class CatalogoService {

    private final CurveApiPort curveApiClient;

    public CatalogoService(CurveApiPort curveApiClient) {
        this.curveApiClient = curveApiClient;
    }

    public CatalogoResponse listarCurvas() {
        return curveApiClient.getCatalogo();
    }

    public CurvaMercadoDTO obterCurva(String ticker) {
        return curveApiClient.getCurva(ticker)
                .orElseThrow(() -> new NoSuchElementException("Curva '" + ticker + "' não encontrada."));
    }
}
