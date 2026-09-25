package br.com.poc.domain.pipeline.interpolator;

import br.com.poc.domain.pipeline.CurveInterpolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FlatForwardCurveInterpolator implements CurveInterpolator {

    public static final String METHOD_NAME = "FLAT_FORWARD";
    private final int baseAnual;

    public FlatForwardCurveInterpolator() { this(252); }

    public FlatForwardCurveInterpolator(int baseAnual) { this.baseAnual = baseAnual > 0 ? baseAnual : 252; }

    @Override
    public String getMethodName() { return METHOD_NAME; }

    @Override
    public BigDecimal interpolar(double prazoAlvo, double[] prazos, double[] taxas) {
        if (prazos == null || taxas == null || prazos.length != taxas.length || prazos.length < 2) {
            throw new IllegalArgumentException("Mínimo de 2 pontos necessários para interpolação Flat Forward.");
        }

        // Ponto coincidente exato
        for (int i = 0; i < prazos.length; i++) {
            if (Double.compare(prazos[i], prazoAlvo) == 0) {
                return BigDecimal.valueOf(taxas[i]).setScale(12, RoundingMode.HALF_UP);
            }
        }

        // Localiza o intervalo [i-1, i] onde prazos[i-1] <= prazoAlvo <= prazos[i]
        int idx = 1;
        while (idx < prazos.length && prazos[idx] < prazoAlvo) {
            idx++;
        }

        if (idx >= prazos.length) {
            idx = prazos.length - 1;
        }

        double t1 = prazos[idx - 1];
        double r1 = taxas[idx - 1];
        double t2 = prazos[idx];
        double r2 = taxas[idx];

        // Fatores acumulados nos nós t1 e t2 sob convenção exponencial
        double f1 = Math.pow(1.0 + r1, t1 / (double) baseAnual);
        double f2 = Math.pow(1.0 + r2, t2 / (double) baseAnual);

        // Fator diário forward constante no subperíodo (t2 - t1)
        double deltaT = t2 - t1;
        double fatorForwardDiario = Math.pow(f2 / f1, 1.0 / deltaT);

        // Fator acumulado projetado até prazoAlvo
        double deltaPrazo = prazoAlvo - t1;
        double fAlvo = f1 * Math.pow(fatorForwardDiario, deltaPrazo);

        // Taxa anualizada equivalente: fAlvo^(base / prazoAlvo) - 1
        double taxaAlvo = Math.pow(fAlvo, (double) baseAnual / prazoAlvo) - 1.0;
        return BigDecimal.valueOf(taxaAlvo).setScale(12, RoundingMode.HALF_UP);
    }
}
