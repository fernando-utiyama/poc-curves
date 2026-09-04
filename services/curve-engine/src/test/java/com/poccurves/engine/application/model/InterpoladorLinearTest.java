package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterpoladorLinearTest {

    private final InterpoladorLinear interpolador = new InterpoladorLinear();

    @Test
    void shouldInterpolateLinearRateAtMidpoint() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null),
                new Vertice(60, null, null, new BigDecimal("0.12"), null)
        );

        BigDecimal taxa = interpolador.taxaEm(vertices, 45, MathContext.DECIMAL64);

        assertThat(taxa).isEqualByComparingTo(new BigDecimal("0.11"));
    }

    @Test
    void shouldReturnExactRateWhenPrazoMatchesVertice() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null),
                new Vertice(60, null, null, new BigDecimal("0.12"), null)
        );

        assertThat(interpolador.taxaEm(vertices, 30, MathContext.DECIMAL64))
                .isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(interpolador.taxaEm(vertices, 60, MathContext.DECIMAL64))
                .isEqualByComparingTo(new BigDecimal("0.12"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoIsBelowFirstVertice() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null),
                new Vertice(60, null, null, new BigDecimal("0.12"), null)
        );

        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 29, MathContext.DECIMAL64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoIsAboveLastVertice() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null),
                new Vertice(60, null, null, new BigDecimal("0.12"), null)
        );

        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 61, MathContext.DECIMAL64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenSingleVerticeProvided() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null)
        );

        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 30, MathContext.DECIMAL64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldInterpolateCorrectlyWithThreeVertices() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null),
                new Vertice(60, null, null, new BigDecimal("0.12"), null),
                new Vertice(90, null, null, new BigDecimal("0.15"), null)
        );

        BigDecimal taxa = interpolador.taxaEm(vertices, 75, MathContext.DECIMAL64);

        assertThat(taxa).isEqualByComparingTo(new BigDecimal("0.135"));
    }

    @Test
    void shouldReturnLinearIdentifier() {
        assertThat(interpolador.identificador()).isEqualTo("LINEAR");
    }
}
