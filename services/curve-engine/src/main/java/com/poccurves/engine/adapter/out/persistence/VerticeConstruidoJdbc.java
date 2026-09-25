package com.poccurves.engine.adapter.out.persistence;
import com.poccurves.engine.application.model.VerticeConstruido;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Mapeamento JDBC compartilhado de {@link VerticeConstruido} para {@code Object[]} — {@code
 * tDadoCurva} e {@code tCurvaData} (V22) têm exatamente a mesma forma de coluna (dBaseReft,
 * cTickerIndcd, dVertcReft, vPrecoTx), usada por {@link DadoCurvaRepository} e
 * {@link CurvaDataRepository}.
 */
final class VerticeConstruidoJdbc {

    private VerticeConstruidoJdbc() {
    }

    static List<Object[]> paraBatchArgs(String tickerIndcd, LocalDate dataReferencia, List<VerticeConstruido> pontos) {
        return pontos.stream()
                .map(p -> new Object[]{
                        Date.valueOf(dataReferencia),
                        tickerIndcd,
                        Date.valueOf(p.dataVertice()),
                        p.valor()
                })
                .toList();
    }
}
