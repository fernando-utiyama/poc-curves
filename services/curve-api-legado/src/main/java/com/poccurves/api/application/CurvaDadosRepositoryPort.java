package com.poccurves.api.application;

import com.poccurves.api.domain.PontoCurva;

import java.time.LocalDate;
import java.util.List;

/** Leitura dos vértices/curva construídos pelo curve-engine — esta API nunca escreve aqui. */
public interface CurvaDadosRepositoryPort {

    /** Vértices brutos construídos pelo engine, em {@code tDadoCurva}. */
    List<PontoCurva> buscarVertices(String ticker, LocalDate dataReferencia);

    /** Curva construída/interpolada, em {@code tCurvaData}. */
    List<PontoCurva> buscarCurvaConstruida(String ticker, LocalDate dataReferencia);
}
