package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.interpolacao.InterpoladorSplineCubicoNatural;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterpoladorSplineCubicoNaturalTest {

    private final InterpoladorSplineCubicoNatural interpolador = new InterpoladorSplineCubicoNatural();
    private final MathContext mc = MathContext.DECIMAL128;

    @Test
    void shouldReturnExactRateWhenPrazoMatchesVertice() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null),
                new Vertice(60, null, null, new BigDecimal("0.12"), null)
        );

        assertThat(interpolador.taxaEm(vertices, 30, mc))
                .isEqualByComparingTo(new BigDecimal("0.10"));
        assertThat(interpolador.taxaEm(vertices, 60, mc))
                .isEqualByComparingTo(new BigDecimal("0.12"));
    }

    @Test
    void shouldThrowExceptionWhenPrazoIsOutsideRange() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null),
                new Vertice(60, null, null, new BigDecimal("0.12"), null)
        );

        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 29, mc))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 61, mc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenLessThanTwoVerticesProvided() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null)
        );

        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 30, mc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldInterpolateSmoothlyBetweenVertices() {
        List<Vertice> vertices = List.of(
                new Vertice(30, null, null, new BigDecimal("0.08"), null),
                new Vertice(60, null, null, new BigDecimal("0.10"), null),
                new Vertice(90, null, null, new BigDecimal("0.12"), null),
                new Vertice(120, null, null, new BigDecimal("0.14"), null)
        );

        BigDecimal taxa1 = interpolador.taxaEm(vertices, 45, mc);
        BigDecimal taxa2 = interpolador.taxaEm(vertices, 75, mc);
        BigDecimal taxa3 = interpolador.taxaEm(vertices, 105, mc);

        assertThat(taxa1).isBetween(new BigDecimal("0.08"), new BigDecimal("0.10"));
        assertThat(taxa2).isBetween(new BigDecimal("0.10"), new BigDecimal("0.12"));
        assertThat(taxa3).isBetween(new BigDecimal("0.12"), new BigDecimal("0.14"));
    }

    @Test
    void shouldReturnCorrectIdentifier() {
        assertThat(interpolador.identificador()).isEqualTo("SPLINE_CUBICO_NATURAL");
    }
}
