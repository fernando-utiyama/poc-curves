package br.com.poc.domain.pipeline;

import br.com.poc.domain.model.MetodoInterpolacao;
import br.com.poc.domain.pipeline.interpolator.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry de interpoladores do domínio puro (sem acoplamento a Spring/frameworks).
 */
public class CurveInterpolatorRegistry {

    private final Map<MetodoInterpolacao, CurveInterpolator> interpolators = new EnumMap<>(MetodoInterpolacao.class);
    private final CurveInterpolator defaultInterpolator;

    public CurveInterpolatorRegistry() {
        CurveInterpolator linear = new LinearCurveInterpolator();
        this.defaultInterpolator = linear;
        register(MetodoInterpolacao.LINEAR, linear);
        register(MetodoInterpolacao.EXPONENCIAL, new FlatForwardCurveInterpolator());
        register(MetodoInterpolacao.SPLINE, new CubicSplineCurveInterpolator());
        register(MetodoInterpolacao.LAGRANGE, new LagrangeCurveInterpolator());
        register(MetodoInterpolacao.NEWTON, new NewtonDividedDifferenceCurveInterpolator());
    }

    public void register(MetodoInterpolacao metodo, CurveInterpolator interpolator) {
        Objects.requireNonNull(metodo, "MetodoInterpolacao não pode ser nulo");
        Objects.requireNonNull(interpolator, "CurveInterpolator não pode ser nulo");
        interpolators.put(metodo, interpolator);
    }

    public CurveInterpolator resolve(MetodoInterpolacao metodo) {
        if (metodo == null) {
            return defaultInterpolator;
        }
        return Optional.ofNullable(interpolators.get(metodo)).orElse(defaultInterpolator);
    }

    public Map<MetodoInterpolacao, CurveInterpolator> getAll() { return Map.copyOf(interpolators); }
}
