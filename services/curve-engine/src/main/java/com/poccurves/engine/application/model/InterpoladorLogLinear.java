package com.poccurves.engine.application.model;




import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Interpolação log-linear diretamente sobre a taxa (LOG_LINEAR).
 * <p>
 * Decisão de design:
 * Interpolação geométrica direta da taxa (e não do fator de desconto) em função do prazo.
 * A taxa no prazo t é calculada por:
 * r(t) = r1 * (r2/r1)^((t-t1)/(t2-t1))
 */
public final class InterpoladorLogLinear implements Interpolador {

    public static final String IDENTIFICADOR = "LOG_LINEAR";

    @Override
    public String identificador() {
        return IDENTIFICADOR;
    }

    @Override
    public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis, MathContext mathContext) {
        Objects.requireNonNull(verticesOrdenados, "verticesOrdenados não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");
        if (verticesOrdenados.size() < 2) {
            throw new IllegalArgumentException("interpolação exige ao menos 2 vértices");
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

        if (r1.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("taxa <= 0 no vértice " + anterior.prazoDiasUteis());
        }
        if (r2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("taxa <= 0 no vértice " + posterior.prazoDiasUteis());
        }

        BigDecimal razaoR = r2.divide(r1, mathContext);
        BigDecimal expoente = t.subtract(t1).divide(t2.subtract(t1), mathContext);
        return r1.multiply(RoundingPolicy.powerRaw(razaoR, expoente, mathContext), mathContext);
    }
}
