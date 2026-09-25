package br.com.poc.domain.pipeline;

import java.util.Objects;

public record PoliticaExtrapolacaoConfig(
    CurveExtrapolator pontaCurta,
    CurveExtrapolator pontaLonga
) {
    public PoliticaExtrapolacaoConfig {
        Objects.requireNonNull(pontaCurta, "Extrapolador da ponta curta não pode ser nulo");
        Objects.requireNonNull(pontaLonga, "Extrapolador da ponta longa não pode ser nulo");
    }

    public static PoliticaExtrapolacaoConfig simetrica(CurveExtrapolator extrapolator) {
        return new PoliticaExtrapolacaoConfig(extrapolator, extrapolator);
    }
}
