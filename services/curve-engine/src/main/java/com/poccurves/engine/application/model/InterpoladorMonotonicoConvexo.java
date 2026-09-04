package com.poccurves.engine.application.model;


import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Implementação real: interpolação cúbica de Hermite monotônica (Fritsch-Carlson,
 * condição suficiente simplificada, sem a etapa de reescala por raiz quadrada do
 * artigo original) sobre a taxa — garante ausência de overshoot/monotonicidade local.
 * É uma simplificação documentada do método 'monotone convex' de Hagan-West (2006),
 * que opera sobre taxas forward com garantias mais fortes sobre a convexidade do
 * fator de desconto; implementar Hagan-West completo é tarefa maior, fora do
 * escopo desta rodada.
 */
public final class InterpoladorMonotonicoConvexo implements Interpolador {

    public static final String IDENTIFICADOR = "MONOTONICO_CONVEXO";

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

        int n = verticesOrdenados.size() - 1;
        BigDecimal[] t = new BigDecimal[n + 1];
        BigDecimal[] r = new BigDecimal[n + 1];
        for (int i = 0; i <= n; i++) {
            t[i] = BigDecimal.valueOf(verticesOrdenados.get(i).prazoDiasUteis());
            r[i] = verticesOrdenados.get(i).taxa();
        }

        BigDecimal[] h = new BigDecimal[n];
        BigDecimal[] delta = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            h[i] = t[i + 1].subtract(t[i], mathContext);
            delta[i] = r[i + 1].subtract(r[i], mathContext).divide(h[i], mathContext);
        }

        BigDecimal[] m = new BigDecimal[n + 1];
        if (n == 1) {
            m[0] = delta[0];
            m[1] = delta[0];
        } else {
            m[0] = delta[0];
            m[n] = delta[n - 1];
            for (int i = 1; i < n; i++) {
                m[i] = delta[i - 1].add(delta[i], mathContext).divide(BigDecimal.valueOf(2), mathContext);
            }

            if (delta[0].signum() == 0) {
                m[0] = BigDecimal.ZERO;
            } else {
                BigDecimal limite0 = BigDecimal.valueOf(3).multiply(delta[0].abs(), mathContext);
                if (m[0].abs().compareTo(limite0) > 0) {
                    m[0] = delta[0].signum() < 0 ? limite0.negate(mathContext) : limite0;
                }
            }

            if (delta[n - 1].signum() == 0) {
                m[n] = BigDecimal.ZERO;
            } else {
                BigDecimal limiten = BigDecimal.valueOf(3).multiply(delta[n - 1].abs(), mathContext);
                if (m[n].abs().compareTo(limiten) > 0) {
                    m[n] = delta[n - 1].signum() < 0 ? limiten.negate(mathContext) : limiten;
                }
            }

            for (int i = 1; i < n; i++) {
                if (delta[i - 1].multiply(delta[i], mathContext).signum() <= 0) {
                    m[i] = BigDecimal.ZERO;
                } else {
                    BigDecimal limite = BigDecimal.valueOf(3).multiply(delta[i - 1].abs().min(delta[i].abs()), mathContext);
                    if (m[i].abs().compareTo(limite) > 0) {
                        m[i] = delta[i].signum() < 0 ? limite.negate(mathContext) : limite;
                    }
                }
            }
        }

        BigDecimal prazo = BigDecimal.valueOf(prazoDiasUteis);
        int segmento = -1;
        for (int i = 0; i < n; i++) {
            if (t[i].compareTo(prazo) < 0 && t[i + 1].compareTo(prazo) > 0) {
                segmento = i;
                break;
            }
        }

        BigDecimal h_i = h[segmento];
        BigDecimal s = prazo.subtract(t[segmento], mathContext).divide(h_i, mathContext);
        BigDecimal s2 = s.multiply(s, mathContext);
        BigDecimal s3 = s2.multiply(s, mathContext);

        BigDecimal dois = BigDecimal.valueOf(2);
        BigDecimal tres = BigDecimal.valueOf(3);

        BigDecimal basisH00 = dois.multiply(s3, mathContext).subtract(tres.multiply(s2, mathContext), mathContext).add(BigDecimal.ONE, mathContext);
        BigDecimal basisH10 = s3.subtract(dois.multiply(s2, mathContext), mathContext).add(s, mathContext);
        BigDecimal basisH01 = dois.negate().multiply(s3, mathContext).add(tres.multiply(s2, mathContext), mathContext);
        BigDecimal basisH11 = s3.subtract(s2, mathContext);

        BigDecimal termo1 = r[segmento].multiply(basisH00, mathContext);
        BigDecimal termo2 = h_i.multiply(m[segmento], mathContext).multiply(basisH10, mathContext);
        BigDecimal termo3 = r[segmento + 1].multiply(basisH01, mathContext);
        BigDecimal termo4 = h_i.multiply(m[segmento + 1], mathContext).multiply(basisH11, mathContext);

        return termo1.add(termo2, mathContext).add(termo3, mathContext).add(termo4, mathContext);
    }
}
