package com.poccurves.engine.application.port;

import com.poccurves.engine.application.model.VerticeBtrs;

import java.time.LocalDate;
import java.util.List;

/** Leitura dos vértices brutos gravados pelo curve-processor em {@code tBtrsCurvaPrimr}. */
public interface BtrsCurvaPrimrConsultaRepositoryPort {

    /** Vértices de {@code tickerIndcd} em {@code dataReferencia}, ordenados por diasUteis crescente. */
    List<VerticeBtrs> buscarVertices(String tickerIndcd, LocalDate dataReferencia);
}
