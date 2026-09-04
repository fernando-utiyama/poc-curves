package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterpoladorRegistryTest {

    @Test
    void shouldResolveRegisteredInterpolator() {
        InterpoladorLinear linear = new InterpoladorLinear();
        InterpoladorRegistry registry = new InterpoladorRegistry(List.of(linear));

        Interpolador resolved = registry.resolver("LINEAR");

        assertThat(resolved).isSameAs(linear);
        assertThat(resolved.identificador()).isEqualTo("LINEAR");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenInterpolatorNotFound() {
        InterpoladorRegistry registry = new InterpoladorRegistry(List.of(new InterpoladorLinear()));

        assertThatThrownBy(() -> registry.resolver("NAO_EXISTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAO_EXISTE");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDuplicateIdentifiersAreRegistered() {
        Interpolador fake1 = new Interpolador() {
            @Override
            public String identificador() {
                return "DUPLICADO";
            }

            @Override
            public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis, MathContext mathContext) {
                return BigDecimal.ZERO;
            }
        };

        Interpolador fake2 = new Interpolador() {
            @Override
            public String identificador() {
                return "DUPLICADO";
            }

            @Override
            public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis, MathContext mathContext) {
                return BigDecimal.ZERO;
            }
        };

        assertThatThrownBy(() -> new InterpoladorRegistry(List.of(fake1, fake2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUPLICADO");
    }

    @Test
    void shouldThrowNullPointerExceptionWhenInterpoladoresListIsNull() {
        assertThatThrownBy(() -> new InterpoladorRegistry(null))
                .isInstanceOf(NullPointerException.class);
    }
}
