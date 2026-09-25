package br.com.poc.domain.pipeline.interpolator;

import br.com.poc.domain.pipeline.CurveInterpolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LogLinearCurveInterpolator implements CurveInterpolator {

    public static final String METHOD_NAME = "LOG_LINEAR";

    @Override
    public String getMethodName() { return METHOD_NAME; }

    @Override
    public BigDecimal interpolar(double prazoAlvo, double[] prazos, double[] valores) {
        if (prazos == null || valores == null || prazos.length != valores.length || prazos.length < 2) {
            throw new IllegalArgumentException("Mínimo de 2 pontos necessários para interpolação Log-Linear.");
        }

        // Ponto coincidente exato
        for (int i = 0; i < prazos.length; i++) {
            if (Double.compare(prazos[i], prazoAlvo) == 0) {
                return BigDecimal.valueOf(valores[i]).setScale(12, RoundingMode.HALF_UP);
            }
        }

        int idx = 1;
        while (idx < prazos.length && prazos[idx] < prazoAlvo) {
            idx++;
        }

        if (idx >= prazos.length) {
            idx = prazos.length - 1;
        }

        double t1 = prazos[idx - 1];
        double v1 = Math.max(valores[idx - 1], 1e-9);
        double t2 = prazos[idx];
        double v2 = Math.max(valores[idx], 1e-9);

        double lnV1 = Math.log(v1);
        double lnV2 = Math.log(v2);

        double weight = (prazoAlvo - t1) / (t2 - t1);
        double lnValvo = lnV1 + weight * (lnV2 - lnV1);
        double vAlvo = Math.exp(lnValvo);

        return BigDecimal.valueOf(vAlvo).setScale(12, RoundingMode.HALF_UP);
    }
}
