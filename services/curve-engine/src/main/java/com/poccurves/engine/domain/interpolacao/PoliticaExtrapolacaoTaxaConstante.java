package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;



import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Política de taxa constante: abaixo do primeiro vértice, usa a taxa do
 * primeiro vértice; acima do último, usa a taxa do último vértice. Nunca
 * calcula nada — apenas repete o valor de fronteira, de forma determinística.
 */
public final class PoliticaExtrapolacaoTaxaConstante implements PoliticaExtrapolacao {

    public static final String IDENTIFICADOR = "TAXA_CONSTANTE";

    @Override
    public String identificador() {
        return IDENTIFICADOR;
    }

    @Override
    public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis) {
        Objects.requireNonNull(verticesOrdenados, "verticesOrdenados não pode ser nulo");
        if (verticesOrdenados.isEmpty()) {
            throw new IllegalArgumentException("verticesOrdenados não pode ser vazio");
        }

        Vertice primeiro = verticesOrdenados.get(0);
        if (prazoDiasUteis < primeiro.prazoDiasUteis()) {
            return primeiro.taxa();
        }

        Vertice ultimo = verticesOrdenados.get(verticesOrdenados.size() - 1);
        return ultimo.taxa();
    }
}
