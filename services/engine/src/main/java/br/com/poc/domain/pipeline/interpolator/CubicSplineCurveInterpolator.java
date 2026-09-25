package br.com.poc.domain.pipeline.interpolator;

import br.com.poc.domain.pipeline.CurveInterpolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CubicSplineCurveInterpolator implements CurveInterpolator {

    public static final String METHOD_NAME = "SPLINE";

    @Override
    public String getMethodName() { return METHOD_NAME; }

    @Override
    public BigDecimal interpolar(double prazoAlvo, double[] prazos, double[] valores) {
        if (prazos == null || valores == null || prazos.length != valores.length || prazos.length < 2) {
            throw new IllegalArgumentException("Mínimo de 2 pontos necessários para interpolação.");
        }

        // Ponto coincidente exato
        for (int i = 0; i < prazos.length; i++) {
            if (Double.compare(prazos[i], prazoAlvo) == 0) {
                return BigDecimal.valueOf(valores[i]).setScale(12, RoundingMode.HALF_UP);
            }
        }

        // Spline cúbica requer pelo menos 3 pontos; com 2 pontos faz fallback linear nativo
        if (prazos.length >= 3) {
            try {
                double val = interpolateNaturalCubicSpline(prazoAlvo, prazos, valores);
                return BigDecimal.valueOf(val).setScale(12, RoundingMode.HALF_UP);
            } catch (Exception ignored) {
                // Fallback para linear em caso de singularidade numérica
            }
        }

        return fallbackLinear(prazoAlvo, prazos, valores);
    }

    private double interpolateNaturalCubicSpline(double targetX, double[] x, double[] y) {
        int n = x.length;
        double[] h = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            h[i] = x[i + 1] - x[i];
        }

        double[] alpha = new double[n - 1];
        for (int i = 1; i < n - 1; i++) {
            alpha[i] = (3.0 / h[i]) * (y[i + 1] - y[i]) - (3.0 / h[i - 1]) * (y[i] - y[i - 1]);
        }

        double[] l = new double[n];
        double[] mu = new double[n];
        double[] z = new double[n];
        l[0] = 1.0;
        mu[0] = 0.0;
        z[0] = 0.0;

        for (int i = 1; i < n - 1; i++) {
            l[i] = 2.0 * (x[i + 1] - x[i - 1]) - h[i - 1] * mu[i - 1];
            mu[i] = h[i] / l[i];
            z[i] = (alpha[i] - h[i - 1] * z[i - 1]) / l[i];
        }

        l[n - 1] = 1.0;
        z[n - 1] = 0.0;
        double[] c = new double[n];
        double[] b = new double[n - 1];
        double[] d = new double[n - 1];
        c[n - 1] = 0.0;

        for (int j = n - 2; j >= 0; j--) {
            c[j] = z[j] - mu[j] * c[j + 1];
            b[j] = (y[j + 1] - y[j]) / h[j] - h[j] * (c[j + 1] + 2.0 * c[j]) / 3.0;
            d[j] = (c[j + 1] - c[j]) / (3.0 * h[j]);
        }

        int i = 0;
        while (i < n - 2 && x[i + 1] < targetX) {
            i++;
        }

        double dx = targetX - x[i];
        return y[i] + b[i] * dx + c[i] * dx * dx + d[i] * dx * dx * dx;
    }

    private BigDecimal fallbackLinear(double prazoAlvo, double[] prazos, double[] valores) {
        int idx = 1;
        while (idx < prazos.length && prazos[idx] < prazoAlvo) {
            idx++;
        }
        if (idx >= prazos.length) {
            idx = prazos.length - 1;
        }
        double x1 = prazos[idx - 1];
        double y1 = valores[idx - 1];
        double x2 = prazos[idx];
        double y2 = valores[idx];
        double slope = (y2 - y1) / (x2 - x1);
        double val = y1 + slope * (prazoAlvo - x1);
        return BigDecimal.valueOf(val).setScale(12, RoundingMode.HALF_UP);
    }
}
