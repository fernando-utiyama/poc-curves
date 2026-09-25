package br.com.poc.domain.strategy;

import br.com.poc.domain.model.MetodoInterpolacao;
import br.com.poc.domain.pipeline.CurveInterpolator;
import br.com.poc.domain.pipeline.CurveInterpolatorRegistry;
import br.com.poc.domain.pipeline.InsumoNormalizer;
import br.com.poc.domain.pipeline.normalizer.DefaultInsumoNormalizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CurveBuilderRegistry {

    private final Map<String, CurveBuilderStrategy> strategies = new ConcurrentHashMap<>();
    private final CurveInterpolatorRegistry interpolatorRegistry;
    private final InsumoNormalizer defaultNormalizer;

    public CurveBuilderRegistry() { this(new CurveInterpolatorRegistry(), new DefaultInsumoNormalizer()); }

    public CurveBuilderRegistry(
        CurveInterpolatorRegistry interpolatorRegistry,
        InsumoNormalizer defaultNormalizer
    ) {
        this.interpolatorRegistry = interpolatorRegistry;
        this.defaultNormalizer = defaultNormalizer;

        // Auto-registra dinamicamente todos os interpoladores conhecidos pelo CurveInterpolatorRegistry
        for (CurveInterpolator interpolator : interpolatorRegistry.getAll().values()) {
            register(interpolator);
        }
    }

    public void register(CurveBuilderStrategy strategy) {
        if (strategy == null || strategy.getStrategyName() == null) {
            throw new IllegalArgumentException("Estratégia ou nome não podem ser nulos");
        }
        strategies.put(normalizeKey(strategy.getStrategyName()), strategy);
    }

    public void register(String alias, CurveBuilderStrategy strategy) {
        if (alias == null || strategy == null) {
            throw new IllegalArgumentException("Alias ou estratégia não podem ser nulos");
        }
        strategies.put(normalizeKey(alias), strategy);
    }

    public void register(CurveInterpolator interpolator) {
        if (interpolator == null || interpolator.getMethodName() == null) {
            throw new IllegalArgumentException("CurveInterpolator não pode ser nulo");
        }

        String strategyName = interpolator.getClass().getSimpleName();
        ComposableCurveBuilder builder = new ComposableCurveBuilder(
            strategyName,
            defaultNormalizer
        );

        register(builder);
        register(interpolator.getMethodName(), builder);
    }

    public Optional<CurveBuilderStrategy> find(String strategyName) {
        if (strategyName == null || strategyName.isBlank()) {
            return Optional.empty();
        }

        String key = normalizeKey(strategyName);
        CurveBuilderStrategy found = strategies.get(key);
        if (found != null) {
            return Optional.of(found);
        }

        // Resolução dinâmica sob demanda: se for um método de interpolação conhecido (ex: SPLINE, LAGRANGE, NEWTON)
        try {
            MetodoInterpolacao metodo = MetodoInterpolacao.valueOf(strategyName.trim().toUpperCase());
            CurveInterpolator interpolator = interpolatorRegistry.resolve(metodo);
            if (interpolator != null) {
                register(interpolator);
                return Optional.ofNullable(strategies.get(key));
            }
        } catch (IllegalArgumentException ignored) {
            // Não é um nome de enum direto
        }

        return Optional.empty();
    }

    public boolean contains(String strategyName) { return find(strategyName).isPresent(); }

    private String normalizeKey(String key) { return key.trim().toLowerCase().replace("_", "").replace("-", ""); }
}
