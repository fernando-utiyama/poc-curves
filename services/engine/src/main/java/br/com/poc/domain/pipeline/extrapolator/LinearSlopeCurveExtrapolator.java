package br.com.poc.domain.pipeline.extrapolator;

import br.com.poc.domain.pipeline.CurveExtrapolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LinearSlopeCurveExtrapolator implements CurveExtrapolator {

    public static final String POLICY_NAME = "LINEAR";

    @Override
    public String getPolicyName() { return POLICY_NAME; }

    @Override
    public BigDecimal extrapolar(
        double prazoAlvo,
        double[] prazos,
        double[] valores,
        Direcao direcao,
        int baseAnual
    ) {
        if (prazos == null || valores == null || prazos.length < 2 || valores.length < 2) {
            throw new IllegalArgumentException("Mínimo de 2 pontos necessários para extrapolação linear.");
        }

        double taxaExtrap;
        if (direcao == Direcao.INFERIOR) {
            double x1 = prazos[0];
            double y1 = valores[0];
            double x2 = prazos[1];
            double y2 = valores[1];
            double slope = (y2 - y1) / (x2 - x1);
            taxaExtrap = y1 + slope * (prazoAlvo - x1);
        } else {
            int n = prazos.length;
            double x1 = prazos[n - 2];
            double y1 = valores[n - 2];
            double x2 = prazos[n - 1];
            double y2 = valores[n - 1];
            double slope = (y2 - y1) / (x2 - x1);
            taxaExtrap = y2 + slope * (prazoAlvo - x2);
        }

        return BigDecimal.valueOf(taxaExtrap).setScale(12, RoundingMode.HALF_UP);
    }
}
