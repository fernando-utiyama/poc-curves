package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.interpolacao.SplineCubicaNatural;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplineCubicaNaturalTest {

    private final MathContext mc = MathContext.DECIMAL128;

    @Test
    void shouldDegenerateToLineWithTwoPoints() {
        List<BigDecimal> xs = List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(20));
        List<BigDecimal> ys = List.of(BigDecimal.valueOf(100), BigDecimal.valueOf(200));

        BigDecimal resultado = SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(15), mc);

        assertThat(resultado).isCloseTo(BigDecimal.valueOf(150), org.assertj.core.data.Offset.offset(BigDecimal.valueOf(1e-20)));
    }

    @Test
    void shouldReproduceLineExactlyWithThreeOrMoreCollinearPoints() {
        List<BigDecimal> xs = List.of(BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.valueOf(20), BigDecimal.valueOf(30));
        List<BigDecimal> ys = List.of(BigDecimal.ZERO, BigDecimal.valueOf(10), BigDecimal.valueOf(20), BigDecimal.valueOf(30));

        BigDecimal resultado15 = SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(15), mc);
        BigDecimal resultado25 = SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(25), mc);

        assertThat(resultado15).isCloseTo(BigDecimal.valueOf(15), org.assertj.core.data.Offset.offset(BigDecimal.valueOf(1e-20)));
        assertThat(resultado25).isCloseTo(BigDecimal.valueOf(25), org.assertj.core.data.Offset.offset(BigDecimal.valueOf(1e-20)));
    }

    @Test
    void shouldReturnExactYWhenTMatchesX() {
        List<BigDecimal> xs = List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(20), BigDecimal.valueOf(30));
        List<BigDecimal> ys = List.of(BigDecimal.valueOf(100), BigDecimal.valueOf(400), BigDecimal.valueOf(900));

        BigDecimal resultado = SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(20), mc);

        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(400));
    }

    @Test
    void shouldThrowExceptionWhenTIsOutsideInterval() {
        List<BigDecimal> xs = List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(20));
        List<BigDecimal> ys = List.of(BigDecimal.valueOf(100), BigDecimal.valueOf(200));

        assertThatThrownBy(() -> SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(5), mc))
                .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(25), mc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenSizesDiffer() {
        List<BigDecimal> xs = List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(20));
        List<BigDecimal> ys = List.of(BigDecimal.valueOf(100));

        assertThatThrownBy(() -> SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(15), mc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenFewerThanTwoPoints() {
        List<BigDecimal> xs = List.of(BigDecimal.valueOf(10));
        List<BigDecimal> ys = List.of(BigDecimal.valueOf(100));

        assertThatThrownBy(() -> SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(10), mc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionWhenXsNotStrictlyIncreasing() {
        List<BigDecimal> xs = List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(20));
        List<BigDecimal> ys = List.of(BigDecimal.valueOf(100), BigDecimal.valueOf(200), BigDecimal.valueOf(300));

        assertThatThrownBy(() -> SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(15), mc))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
