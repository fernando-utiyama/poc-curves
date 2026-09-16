package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Um vértice de saída da construção das curvas TS B3 — mesma forma persistida em {@code
 * tDadoCurva} e {@code tCurvaData} (V22): a data real do vértice (dBaseReft + diasCorridos,
 * resolvida a partir de {@link VerticeBtrs#diasCorridos()} — dias corridos não depende de
 * calendário de pregão, diferente de dias úteis) e a taxa/preço/pontos de índice (o valor é
 * tratado como decimal opaco, igual ao pipeline de ingestão — ver B3TaxaSwapParser).
 */
public record VerticeConstruido(
        LocalDate dataVertice,
        BigDecimal valor
) {
    public VerticeConstruido {
        Objects.requireNonNull(dataVertice, "dataVertice não pode ser nula");
        Objects.requireNonNull(valor, "valor não pode ser nulo");
    }
}
