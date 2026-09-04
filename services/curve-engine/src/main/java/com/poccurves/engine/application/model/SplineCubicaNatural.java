package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Utilitário para cálculo de Spline Cúbica Natural com precisão arbitrária.
 * Opera exclusivamente sobre pares (x, y) de BigDecimal, sem conhecimento
 * de domínio (prazos, taxas).
 */
public final class SplineCubicaNatural {

    private static final BigDecimal DOIS = BigDecimal.valueOf(2);
    private static final BigDecimal SEIS = BigDecimal.valueOf(6);

    private SplineCubicaNatural() {
        // utilitário estático
    }

    /**
     * Avalia o Spline Cúbico Natural para um ponto t.
     *
     * @param xs          lista de valores X (estritamente crescente)
     * @param ys          lista de valores Y (mesmo tamanho que xs)
     * @param t           ponto de avaliação
     * @param mathContext contexto matemático para as operações de divisão/multiplicação
     * @return S(t), o valor interpolado
     */
    public static BigDecimal avaliar(List<BigDecimal> xs, List<BigDecimal> ys, BigDecimal t, MathContext mathContext) {
        Objects.requireNonNull(xs, "xs não pode ser nulo");
        Objects.requireNonNull(ys, "ys não pode ser nulo");
        Objects.requireNonNull(t, "t não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");

        if (xs.size() != ys.size()) {
            throw new IllegalArgumentException("tamanhos diferentes: xs=" + xs.size() + ", ys=" + ys.size());
        }
        if (xs.size() < 2) {
            throw new IllegalArgumentException("exige ao menos 2 pontos, recebido: " + xs.size());
        }

        for (int i = 0; i < xs.size() - 1; i++) {
            if (xs.get(i).compareTo(xs.get(i + 1)) >= 0) {
                throw new IllegalArgumentException("xs fora de ordem no índice " + i);
            }
        }

        BigDecimal xPrimeiro = xs.get(0);
        BigDecimal xUltimo = xs.get(xs.size() - 1);
        if (t.compareTo(xPrimeiro) < 0 || t.compareTo(xUltimo) > 0) {
            throw new IllegalArgumentException("t=" + t + " fora do intervalo [" + xPrimeiro + ", " + xUltimo + "]");
        }

        for (int i = 0; i < xs.size(); i++) {
            if (xs.get(i).compareTo(t) == 0) {
                return ys.get(i);
            }
        }

        int n = xs.size() - 1;
        BigDecimal[] h = new BigDecimal[n];
        for (int i = 0; i < n; i++) {
            h[i] = xs.get(i + 1).subtract(xs.get(i));
        }

        BigDecimal[] M = new BigDecimal[n + 1];
        for (int i = 0; i <= n; i++) {
            M[i] = BigDecimal.ZERO;
        }

        int m = n - 1;

        if (m == 1) {
            BigDecimal b1 = DOIS.multiply(h[0].add(h[1]), mathContext);
            BigDecimal diff1 = ys.get(2).subtract(ys.get(1)).divide(h[1], mathContext);
            BigDecimal diff0 = ys.get(1).subtract(ys.get(0)).divide(h[0], mathContext);
            BigDecimal d1 = SEIS.multiply(diff1.subtract(diff0), mathContext);
            M[1] = d1.divide(b1, mathContext);
        } else if (m > 1) {
            BigDecimal[] cp = new BigDecimal[m + 1];
            BigDecimal[] dp = new BigDecimal[m + 1];

            BigDecimal b1 = DOIS.multiply(h[0].add(h[1]), mathContext);
            BigDecimal c1 = h[1];
            BigDecimal diff1 = ys.get(2).subtract(ys.get(1)).divide(h[1], mathContext);
            BigDecimal diff0 = ys.get(1).subtract(ys.get(0)).divide(h[0], mathContext);
            BigDecimal d1 = SEIS.multiply(diff1.subtract(diff0), mathContext);

            cp[1] = c1.divide(b1, mathContext);
            dp[1] = d1.divide(b1, mathContext);

            for (int i = 2; i <= m; i++) {
                BigDecimal ai = h[i - 1];
                BigDecimal bi = DOIS.multiply(h[i - 1].add(h[i]), mathContext);
                BigDecimal ci = (i < m) ? h[i] : null;
                
                BigDecimal diffI1 = ys.get(i + 1).subtract(ys.get(i)).divide(h[i], mathContext);
                BigDecimal diffI0 = ys.get(i).subtract(ys.get(i - 1)).divide(h[i - 1], mathContext);
                BigDecimal di = SEIS.multiply(diffI1.subtract(diffI0), mathContext);

                BigDecimal denom = bi.subtract(ai.multiply(cp[i - 1], mathContext));
                if (i <= m - 1) {
                    cp[i] = ci.divide(denom, mathContext);
                }
                dp[i] = di.subtract(ai.multiply(dp[i - 1], mathContext)).divide(denom, mathContext);
            }

            M[m] = dp[m];
            for (int i = m - 1; i >= 1; i--) {
                M[i] = dp[i].subtract(cp[i].multiply(M[i + 1], mathContext));
            }
        }

        int indiceIntervalo = -1;
        for (int i = 0; i < xs.size() - 1; i++) {
            if (xs.get(i).compareTo(t) < 0 && t.compareTo(xs.get(i + 1)) < 0) {
                indiceIntervalo = i;
                break;
            }
        }

        if (indiceIntervalo == -1) {
            throw new IllegalStateException("não foi possível localizar intervalo para t=" + t);
        }

        int i = indiceIntervalo;
        BigDecimal hi = h[i];
        BigDecimal xi = xs.get(i);
        BigDecimal xProx = xs.get(i + 1);
        BigDecimal yi = ys.get(i);
        BigDecimal yProx = ys.get(i + 1);
        BigDecimal mi = M[i];
        BigDecimal mProx = M[i + 1];

        BigDecimal A = xProx.subtract(t).divide(hi, mathContext);
        BigDecimal B = t.subtract(xi).divide(hi, mathContext);

        BigDecimal termo1 = A.multiply(yi, mathContext);
        BigDecimal termo2 = B.multiply(yProx, mathContext);

        BigDecimal a3 = A.multiply(A, mathContext).multiply(A, mathContext);
        BigDecimal b3 = B.multiply(B, mathContext).multiply(B, mathContext);

        BigDecimal termM1 = a3.subtract(A).multiply(mi, mathContext);
        BigDecimal termM2 = b3.subtract(B).multiply(mProx, mathContext);
        
        BigDecimal h2 = hi.multiply(hi, mathContext);
        
        BigDecimal termo3 = termM1.add(termM2).multiply(h2, mathContext).divide(SEIS, mathContext);

        return termo1.add(termo2, mathContext).add(termo3, mathContext);
    }
}
