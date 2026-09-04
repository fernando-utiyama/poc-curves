package com.poccurves.api.domain;

import java.math.BigDecimal;

/**
 * Um ponto de comparação entre duas curvas na mesma data, no mesmo prazo.
 * taxaA ou taxaB é nulo quando o prazo existe em apenas uma das curvas —
 * nunca preenchido por interpolação; a comparação é vértice a vértice, tal
 * como cada curva foi publicada.
 */
public record PontoComparacao(int prazoDiasUteis, BigDecimal taxaA, BigDecimal taxaB) {

    /** true quando o prazo existe nas duas curvas comparadas. */
    public boolean presenteEmAmbas() {
        return taxaA != null && taxaB != null;
    }
}
