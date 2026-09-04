package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;



import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Política estrita: rejeita qualquer prazo fora do intervalo dos vértices,
 * nomeando o prazo pedido e o intervalo disponível — nunca extrapola.
 */
public final class PoliticaExtrapolacaoEstrita implements PoliticaExtrapolacao {

    public static final String IDENTIFICADOR = "ESTRITA";

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
        Vertice ultimo = verticesOrdenados.get(verticesOrdenados.size() - 1);
        throw new IllegalArgumentException(
                "prazo " + prazoDiasUteis + " fora do intervalo dos vértices ["
                        + primeiro.prazoDiasUteis() + ", " + ultimo.prazoDiasUteis() + "]; política ESTRITA não extrapola");
    }
}
