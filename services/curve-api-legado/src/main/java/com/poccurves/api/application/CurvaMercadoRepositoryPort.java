package com.poccurves.api.application;

import com.poccurves.api.domain.CurvaMercado;

import java.util.List;
import java.util.Optional;

/** Leitura do catálogo de curvas de mercado em {@code tCurvaMercd}. */
public interface CurvaMercadoRepositoryPort {

    /** Todas as curvas do catálogo, ordenadas por ticker. */
    List<CurvaMercado> listarTodas();

    Optional<CurvaMercado> buscarPorTicker(String ticker);
}
