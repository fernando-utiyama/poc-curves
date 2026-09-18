package com.poccurves.api.application;

import com.poccurves.api.domain.CurvaMercado;
import com.poccurves.api.dto.ApiDtos.CatalogoCurvasResponse;
import com.poccurves.api.dto.ApiDtos.CurvaMercadoResponse;

import java.util.NoSuchElementException;

public class CurvaMercadoService {

    private final CurvaMercadoRepositoryPort repository;

    public CurvaMercadoService(CurvaMercadoRepositoryPort repository) {
        this.repository = repository;
    }

    public CatalogoCurvasResponse listarTodas() {
        return new CatalogoCurvasResponse(repository.listarTodas().stream()
                .map(this::paraResponse)
                .toList());
    }

    public CurvaMercadoResponse obterPorTicker(String ticker) {
        CurvaMercado curva = repository.buscarPorTicker(ticker)
                .orElseThrow(() -> new NoSuchElementException("Curva de mercado não encontrada para o ticker: " + ticker));
        return paraResponse(curva);
    }

    private CurvaMercadoResponse paraResponse(CurvaMercado curva) {
        return new CurvaMercadoResponse(
                curva.tickerIndcd(),
                curva.classfInstt(),
                curva.classAtivo(),
                curva.moedaNegoc(),
                curva.inicVigencia(),
                curva.usuarCalc());
    }
}
