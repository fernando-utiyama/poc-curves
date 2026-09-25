package br.com.poc.domain.pipeline.interpolator;

import br.com.poc.domain.pipeline.CurveInterpolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LagrangeCurveInterpolator implements CurveInterpolator {

    public static final String METHOD_NAME = "LAGRANGE";

    @Override
    public String getMethodName() { return METHOD_NAME; }

    @Override
    public BigDecimal interpolar(double prazoAlvo, double[] prazos, double[] valores) {
        if (prazos == null || valores == null || prazos.length != valores.length || prazos.length < 2) {
            throw new IllegalArgumentException("Mínimo de 2 pontos necessários para interpolação de Lagrange.");
        }

        // Ponto coincidente exato
        for (int i = 0; i < prazos.length; i++) {
            if (Double.compare(prazos[i], prazoAlvo) == 0) {
                return BigDecimal.valueOf(valores[i]).setScale(12, RoundingMode.HALF_UP);
            }
        }

        try {
            double resultado = 0.0;
            int n = prazos.length;
            for (int i = 0; i < n; i++) {
                double termo = valores[i];
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        double denominador = prazos[i] - prazos[j];
                        if (Double.compare(denominador, 0.0) == 0) {
                            throw new ArithmeticException("Pontos coincidentes na interpolação de Lagrange.");
                        }
                        termo *= (prazoAlvo - prazos[j]) / denominador;
                    }
                }
                resultado += termo;
            }
            return BigDecimal.valueOf(resultado).setScale(12, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return fallbackLinear(prazoAlvo, prazos, valores);
        }
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
