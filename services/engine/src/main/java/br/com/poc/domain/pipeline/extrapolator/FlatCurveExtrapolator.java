package br.com.poc.domain.pipeline.extrapolator;

import br.com.poc.domain.pipeline.CurveExtrapolator;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FlatCurveExtrapolator implements CurveExtrapolator {

    public static final String POLICY_NAME = "FLAT";

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
        if (valores == null || valores.length == 0) {
            throw new IllegalArgumentException("Valores vazios para extrapolação.");
        }

        double val = (direcao == Direcao.INFERIOR) ? valores[0] : valores[valores.length - 1];
        return BigDecimal.valueOf(val).setScale(12, RoundingMode.HALF_UP);
    }
}
