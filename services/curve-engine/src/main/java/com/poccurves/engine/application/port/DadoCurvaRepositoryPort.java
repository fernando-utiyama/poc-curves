package com.poccurves.engine.application.port;

import com.poccurves.engine.application.model.VerticeConstruido;

import java.time.LocalDate;
import java.util.List;

/**
 * Escrita dos vértices construídos pelo engine em {@code tDadoCurva} (V22) — os pontos que o
 * engine validou/transcreveu de {@code tBtrsCurvaPrimr}, com a data real do vértice resolvida.
 */
public interface DadoCurvaRepositoryPort {

    /**
     * Substitui (delete-then-insert, idempotente por reprocessamento) todos os vértices de
     * {@code tickerIndcd}/{@code dataReferencia}. Chamado ANTES de {@link CurvaDataRepositoryPort}
     * substituir — {@code tCurvaData} tem FK para {@code tDadoCurva} na mesma chave
     * (dBaseReft, cTickerIndcd, dVertcReft).
     */
    void substituirVertices(String tickerIndcd, LocalDate dataReferencia, List<VerticeConstruido> vertices);
}
