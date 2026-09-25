package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Um vértice bruto de {@code tBtrsCurvaPrimr} (V22, openspec/changes/b3-additional-curves) — o
 * insumo de entrada das 5 curvas TS B3 (PRE/DCL/PTX/INP/DPL), já gravado pelo curve-processor
 * (dias corridos/úteis + taxa, sem fator de desconto — o curve-processor apenas transcreve o
 * arquivo TaxaSwap.txt, não calcula nada).
 */
public record VerticeBtrs(
        int diasCorridos,
        int diasUteis,
        BigDecimal taxa
) {
    public VerticeBtrs {
        if (diasCorridos < 0) {
            throw new IllegalArgumentException("diasCorridos não pode ser negativo: " + diasCorridos);
        }
        if (diasUteis < 0) {
            throw new IllegalArgumentException("diasUteis não pode ser negativo: " + diasUteis);
        }
        Objects.requireNonNull(taxa, "taxa não pode ser nula");
    }
}
