package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterpoladorFlatForwardTest {

    private final InterpoladorFlatForward interpolador = new InterpoladorFlatForward();
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
    void shouldPreserveLinearityOfLogDiscountFactor() {
        List<Vertice> vertices = List.of(
                new Vertice(252, null, null, new BigDecimal("0.10"), null),
                new Vertice(504, null, null, new BigDecimal("0.12"), null)
        );

        BigDecimal taxa25 = interpolador.taxaEm(vertices, 252 + 63, mathContext);
        BigDecimal taxa50 = interpolador.taxaEm(vertices, 252 + 126, mathContext);
        BigDecimal taxa75 = interpolador.taxaEm(vertices, 252 + 189, mathContext);

        double df25 = calcularFatorDescontoDouble(taxa25.doubleValue(), 252 + 63);
        double df50 = calcularFatorDescontoDouble(taxa50.doubleValue(), 252 + 126);
        double df75 = calcularFatorDescontoDouble(taxa75.doubleValue(), 252 + 189);

        double lnDf25 = Math.log(df25);
        double lnDf50 = Math.log(df50);
        double lnDf75 = Math.log(df75);

        double inclinacao1 = (lnDf50 - lnDf25) / 63.0;
        double inclinacao2 = (lnDf75 - lnDf50) / 63.0;

        assertThat(inclinacao1).isCloseTo(inclinacao2, org.assertj.core.data.Offset.offset(1e-12));
    }

    private double calcularFatorDescontoDouble(double r, int d) {
        return Math.pow(1.0 + r, -d / 252.0);
    }
}
