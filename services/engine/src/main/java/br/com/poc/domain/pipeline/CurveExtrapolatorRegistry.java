package br.com.poc.domain.pipeline;

import br.com.poc.domain.model.PoliticaExtrapolacao;
import br.com.poc.domain.pipeline.extrapolator.ExponentialForwardCurveExtrapolator;
import br.com.poc.domain.pipeline.extrapolator.FlatCurveExtrapolator;
import br.com.poc.domain.pipeline.extrapolator.LinearSlopeCurveExtrapolator;
import br.com.poc.domain.pipeline.extrapolator.StrictCurveExtrapolator;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry de extrapoladores do domínio puro (sem acoplamento a Spring/frameworks).
 */
public class CurveExtrapolatorRegistry {

    private final Map<PoliticaExtrapolacao, CurveExtrapolator> extrapolators = new EnumMap<>(PoliticaExtrapolacao.class);
    private final CurveExtrapolator defaultExtrapolator;

    public CurveExtrapolatorRegistry() {
        CurveExtrapolator linear = new LinearSlopeCurveExtrapolator();
        this.defaultExtrapolator = linear;
        register(PoliticaExtrapolacao.LINEAR, linear);
        register(PoliticaExtrapolacao.FLAT, new FlatCurveExtrapolator());
        register(PoliticaExtrapolacao.EXPONENCIAL, new ExponentialForwardCurveExtrapolator());
        register(PoliticaExtrapolacao.STRICT, new StrictCurveExtrapolator());
    }

    public void register(PoliticaExtrapolacao politica, CurveExtrapolator extrapolator) {
        Objects.requireNonNull(politica, "PoliticaExtrapolacao não pode ser nulo");
        Objects.requireNonNull(extrapolator, "CurveExtrapolator não pode ser nulo");
        extrapolators.put(politica, extrapolator);
    }

    public CurveExtrapolator resolve(PoliticaExtrapolacao politica) {
        if (politica == null) {
            return defaultExtrapolator;
        }
        return Optional.ofNullable(extrapolators.get(politica)).orElse(defaultExtrapolator);
    }
}
