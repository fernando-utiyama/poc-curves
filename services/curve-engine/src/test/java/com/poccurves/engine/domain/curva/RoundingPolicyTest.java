package com.poccurves.engine.domain.curva;
import com.poccurves.engine.domain.curva.RoundingPolicy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundingPolicyTest {

    @Test
    void shouldTruncatePositiveValueDiscardingExtraDigits() {
        BigDecimal result = RoundingPolicy.truncating(2).apply(new BigDecimal("1.239"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.23"));
    }

    @Test
    void shouldTruncateNegativeValueTowardsZeroNotFloor() {
        BigDecimal result = RoundingPolicy.truncating(2).apply(new BigDecimal("-1.239"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("-1.23"));
    }

    @Test
    void shouldRoundNormallyWhenNonTruncatingModeIsUsed() {
        BigDecimal result = RoundingPolicy.of(2, RoundingMode.HALF_UP).apply(new BigDecimal("1.235"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.24"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenScaleIsNegative() {
        assertThatThrownBy(() -> RoundingPolicy.of(-1, RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenModeIsNull() {
        assertThatThrownBy(() -> RoundingPolicy.of(2, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCalculateFractionalPowerForSquareRootOfFour() {
        BigDecimal result = RoundingPolicy.truncating(6).power(new BigDecimal("4"), new BigDecimal("0.5"), MathContext.DECIMAL64);
        assertThat(result).isEqualByComparingTo(new BigDecimal("2.000000"));
    }

    @Test
    void shouldCalculateFractionalPowerForSquareRootOfNine() {
        BigDecimal result = RoundingPolicy.truncating(6).power(new BigDecimal("9"), new BigDecimal("0.5"), MathContext.DECIMAL64);
        assertThat(result).isEqualByComparingTo(new BigDecimal("3.000000"));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenBaseIsNullInPower() {
        assertThatThrownBy(() -> RoundingPolicy.truncating(2).power(null, new BigDecimal("0.5"), MathContext.DECIMAL64))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCalculatePowerRawWithoutTruncation() {
        BigDecimal result = RoundingPolicy.powerRaw(new BigDecimal("1.10"), new BigDecimal("2"), MathContext.DECIMAL128);
        BigDecimal expected = new BigDecimal("1.21");
        BigDecimal diff = result.subtract(expected).abs();
        assertThat(diff).isLessThan(new BigDecimal("1E-14"));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenBaseIsNullInPowerRaw() {
        assertThatThrownBy(() -> RoundingPolicy.powerRaw(null, new BigDecimal("0.5"), MathContext.DECIMAL128))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCalculateLnRawOfE() {
        // e ≈ 2.718281828459045
        BigDecimal result = RoundingPolicy.lnRaw(new BigDecimal("2.718281828459045"), MathContext.DECIMAL128);
        assertThat(result.subtract(BigDecimal.ONE).abs()).isLessThan(new BigDecimal("1E-10"));
    }

    @Test
    void shouldCalculateLnRawOfOneAsZero() {
        BigDecimal result = RoundingPolicy.lnRaw(BigDecimal.ONE, MathContext.DECIMAL128);
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenLnRawValueIsZeroOrNegative() {
        assertThatThrownBy(() -> RoundingPolicy.lnRaw(BigDecimal.ZERO, MathContext.DECIMAL128))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoundingPolicy.lnRaw(new BigDecimal("-1"), MathContext.DECIMAL128))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCalculateExpRawOfZeroAsOne() {
        BigDecimal result = RoundingPolicy.expRaw(BigDecimal.ZERO, MathContext.DECIMAL128);
        assertThat(result).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void lnRawAndExpRawShouldBeInverses() {
        BigDecimal original = new BigDecimal("0.105");
        BigDecimal ln = RoundingPolicy.lnRaw(original, MathContext.DECIMAL128);
        BigDecimal roundTrip = RoundingPolicy.expRaw(ln, MathContext.DECIMAL128);
        assertThat(roundTrip.subtract(original).abs()).isLessThan(new BigDecimal("1E-12"));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenValueIsNullInLnRaw() {
        assertThatThrownBy(() -> RoundingPolicy.lnRaw(null, MathContext.DECIMAL128))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenExpoenteIsNullInExpRaw() {
        assertThatThrownBy(() -> RoundingPolicy.expRaw(null, MathContext.DECIMAL128))
                .isInstanceOf(NullPointerException.class);
    }
}
