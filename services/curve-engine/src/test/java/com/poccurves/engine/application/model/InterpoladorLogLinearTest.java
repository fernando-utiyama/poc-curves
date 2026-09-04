package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterpoladorLogLinearTest {

    private final InterpoladorLogLinear interpolador = new InterpoladorLogLinear();
    private final MathContext mathContext = MathContext.DECIMAL128;

    @Test
    void shouldReturnVerticeTaxaWhenPrazoIsExact() {
        List<Vertice> vertices = List.of(
                new Vertice(21, null, null, new BigDecimal("0.10"), null),
                new Vertice(42, null, null, new BigDecimal("0.12"), null)
        );
        BigDecimal taxa = interpolador.taxaEm(vertices, 21, mathContext);
        assertThat(taxa).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    void shouldThrowExceptionWhenLessThanTwoVertices() {
        List<Vertice> vertices = List.of(
                new Vertice(21, null, null, new BigDecimal("0.10"), null)
        );
        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 30, mathContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 vértices");
    }

    @Test
    void shouldThrowExceptionWhenPrazoIsOutsideRange() {
        List<Vertice> vertices = List.of(
                new Vertice(21, null, null, new BigDecimal("0.10"), null),
                new Vertice(42, null, null, new BigDecimal("0.12"), null)
        );
        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 50, mathContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do intervalo");
    }

    @Test
    void shouldThrowExceptionWhenTaxaIsZeroOrNegative() {
        List<Vertice> vertices = List.of(
                new Vertice(21, null, null, new BigDecimal("0.00"), null),
                new Vertice(42, null, null, new BigDecimal("0.12"), null)
        );
        assertThatThrownBy(() -> interpolador.taxaEm(vertices, 30, mathContext))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taxa <= 0");
    }

    @Test
    void shouldPreserveLinearityOfLogRate() {
        List<Vertice> vertices = List.of(
                new Vertice(252, null, null, new BigDecimal("0.10"), null),
                new Vertice(504, null, null, new BigDecimal("0.12"), null)
        );

        BigDecimal taxa25 = interpolador.taxaEm(vertices, 252 + 63, mathContext);
        BigDecimal taxa50 = interpolador.taxaEm(vertices, 252 + 126, mathContext);
        BigDecimal taxa75 = interpolador.taxaEm(vertices, 252 + 189, mathContext);

        double lnR25 = Math.log(taxa25.doubleValue());
        double lnR50 = Math.log(taxa50.doubleValue());
        double lnR75 = Math.log(taxa75.doubleValue());

        double inclinacao1 = (lnR50 - lnR25) / 63.0;
        double inclinacao2 = (lnR75 - lnR50) / 63.0;

        assertThat(inclinacao1).isCloseTo(inclinacao2, org.assertj.core.data.Offset.offset(1e-12));
    }
}
