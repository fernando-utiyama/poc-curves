package br.com.poc.domain.pipeline.interpolator;

import br.com.poc.domain.pipeline.CurveInterpolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LinearCurveInterpolator implements CurveInterpolator {

    public static final String METHOD_NAME = "LINEAR";

    @Override
    public String getMethodName() { return METHOD_NAME; }

    @Override
    public BigDecimal interpolar(double prazoAlvo, double[] prazos, double[] valores) {
        if (prazos == null || valores == null || prazos.length != valores.length || prazos.length < 2) {
            throw new IllegalArgumentException("Mínimo de 2 pontos necessários para interpolação linear.");
        }

        // Ponto coincidente exato
        for (int i = 0; i < prazos.length; i++) {
            if (Double.compare(prazos[i], prazoAlvo) == 0) {
                return BigDecimal.valueOf(valores[i]).setScale(12, RoundingMode.HALF_UP);
            }
        }

        // Localiza o intervalo [idx-1, idx] onde prazos[idx-1] <= prazoAlvo <= prazos[idx]
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
