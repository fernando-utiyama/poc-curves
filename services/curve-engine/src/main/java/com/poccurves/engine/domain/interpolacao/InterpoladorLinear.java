package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;



import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Interpolação linear de taxa em função do prazo: entre dois vértices
 * adjacentes (t1, r1) e (t2, r2), r(t) = r1 + (r2 - r1) * (t - t1) / (t2 - t1).
 * Aritmética exata em BigDecimal — sem double.
 */
public final class InterpoladorLinear implements Interpolador {

    public static final String IDENTIFICADOR = "LINEAR";

    @Override
    public String identificador() {
        return IDENTIFICADOR;
    }

    @Override
    public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis, MathContext mathContext) {
        Objects.requireNonNull(verticesOrdenados, "verticesOrdenados não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");
        if (verticesOrdenados.size() < 2) {
            throw new IllegalArgumentException("interpolação linear exige ao menos 2 vértices");
        }

        Vertice primeiro = verticesOrdenados.get(0);
        Vertice ultimo = verticesOrdenados.get(verticesOrdenados.size() - 1);
        if (prazoDiasUteis < primeiro.prazoDiasUteis() || prazoDiasUteis > ultimo.prazoDiasUteis()) {
            throw new IllegalArgumentException(
                    "prazo " + prazoDiasUteis + " fora do intervalo dos vértices ["
                            + primeiro.prazoDiasUteis() + ", " + ultimo.prazoDiasUteis() + "]");
        }

        for (Vertice v : verticesOrdenados) {
            if (v.prazoDiasUteis() == prazoDiasUteis) {
                return v.taxa();
            }
        }

        Vertice anterior = null;
        Vertice posterior = null;
        for (int i = 0; i < verticesOrdenados.size() - 1; i++) {
            Vertice atual = verticesOrdenados.get(i);
            Vertice proximo = verticesOrdenados.get(i + 1);
            if (atual.prazoDiasUteis() < prazoDiasUteis && prazoDiasUteis < proximo.prazoDiasUteis()) {
                anterior = atual;
                posterior = proximo;
                break;
            }
        }
        if (anterior == null) {
            throw new IllegalArgumentException(
                    "não foi possível localizar par de vértices adjacentes para o prazo " + prazoDiasUteis);
        }

        BigDecimal t1 = BigDecimal.valueOf(anterior.prazoDiasUteis());
        BigDecimal t2 = BigDecimal.valueOf(posterior.prazoDiasUteis());
        BigDecimal t = BigDecimal.valueOf(prazoDiasUteis);
        BigDecimal r1 = anterior.taxa();
        BigDecimal r2 = posterior.taxa();

        BigDecimal fracao = t.subtract(t1).divide(t2.subtract(t1), mathContext);
        return r1.add(r2.subtract(r1).multiply(fracao, mathContext), mathContext);
    }
}
