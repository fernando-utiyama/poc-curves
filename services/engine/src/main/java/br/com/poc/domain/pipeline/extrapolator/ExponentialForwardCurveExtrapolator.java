package br.com.poc.domain.pipeline.extrapolator;

import br.com.poc.domain.pipeline.CurveExtrapolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ExponentialForwardCurveExtrapolator implements CurveExtrapolator {

    public static final String POLICY_NAME = "FLAT_FORWARD";

    @Override
    public String getPolicyName() { return POLICY_NAME; }

    @Override
    public BigDecimal extrapolar(
        double prazoAlvo,
        double[] prazos,
        double[] taxas,
        Direcao direcao,
        int baseAnual
    ) {
        if (prazos == null || taxas == null || prazos.length < 2 || taxas.length < 2) {
            throw new IllegalArgumentException("Mínimo de 2 pontos necessários para extrapolação Flat Forward.");
        }

        int base = baseAnual > 0 ? baseAnual : 252;

        if (direcao == Direcao.INFERIOR) {
            // Na ponta curta, mantém flat a taxa do primeiro vértice
            return BigDecimal.valueOf(taxas[0]).setScale(12, RoundingMode.HALF_UP);
        }

        // Ponta Longa: calcula o fator diário forward do último trecho [n-2, n-1] e capitaliza até prazoAlvo
        int n = prazos.length;
        double t1 = prazos[n - 2];
        double r1 = taxas[n - 2];
        double t2 = prazos[n - 1];
        double r2 = taxas[n - 1];

        double f1 = Math.pow(1.0 + r1, t1 / (double) base);
        double f2 = Math.pow(1.0 + r2, t2 / (double) base);

        double deltaT = t2 - t1;
        double fatorForwardDiario = Math.pow(f2 / f1, 1.0 / deltaT);

        double deltaExtrap = prazoAlvo - t2;
        double fAlvo = f2 * Math.pow(fatorForwardDiario, deltaExtrap);

        double taxaExtrap = Math.pow(fAlvo, (double) base / prazoAlvo) - 1.0;
        return BigDecimal.valueOf(taxaExtrap).setScale(12, RoundingMode.HALF_UP);
    }
}
