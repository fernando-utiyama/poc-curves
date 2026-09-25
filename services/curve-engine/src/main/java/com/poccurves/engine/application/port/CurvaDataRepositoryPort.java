package com.poccurves.engine.application.port;

import com.poccurves.engine.application.model.VerticeConstruido;

import java.time.LocalDate;
import java.util.List;

/**
 * Escrita da curva construída e interpolada pelo engine em {@code tCurvaData} (V22) — mesma
 * chave (dBaseReft, cTickerIndcd, dVertcReft) de {@code tDadoCurva}, com FK para lá
 * ({@code FK_tDadoCurva_tCurvaData}): toda linha aqui precisa ter uma correspondente em
 * {@code tDadoCurva} primeiro.
 * <p>
 * Por causa dessa FK, um reprocessamento idempotente PRECISA excluir os pontos daqui antes de
 * {@link DadoCurvaRepositoryPort#substituirVertices} apagar os vértices pais — apagar
 * {@code tDadoCurva} primeiro, com linhas de {@code tCurvaData} ainda apontando pra chave antiga,
 * viola a FK. Por isso exclusão e inserção são métodos separados aqui (ao contrário de
 * {@code substituirVertices}, que é auto-contido): quem orquestra decide a ordem entre as duas
 * tabelas ({@link com.poccurves.engine.application.service.ConstrucaoCurvaB3Service}).
 */
public interface CurvaDataRepositoryPort {

    /** Exclui todos os pontos de {@code tickerIndcd}/{@code dataReferencia} — chamar ANTES de {@link DadoCurvaRepositoryPort#substituirVertices}. */
    void excluirPontos(String tickerIndcd, LocalDate dataReferencia);

    /** Insere os pontos informados — chamar DEPOIS de {@link DadoCurvaRepositoryPort#substituirVertices}. */
    void inserirPontos(String tickerIndcd, LocalDate dataReferencia, List<VerticeConstruido> pontos);
}
