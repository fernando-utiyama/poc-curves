package br.com.poc.domain.pipeline.extrapolator;

import br.com.poc.domain.pipeline.CurveExtrapolator;

import java.math.BigDecimal;

public class StrictCurveExtrapolator implements CurveExtrapolator {

    public static final String POLICY_NAME = "STRICT";

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
        double min = prazos != null && prazos.length > 0 ? prazos[0] : 0;
        double max = prazos != null && prazos.length > 0 ? prazos[prazos.length - 1] : 0;

        throw new IllegalStateException(
            "CURV-OUT-OF-DOMAIN: Prazo " + prazoAlvo + " fora do domínio [" + min + ", " + max + "] sob política STRICT (" + direcao + ")."
        );
    }
}
