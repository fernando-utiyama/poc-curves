package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerticeCurvaTest {

    @Test
    void recusaPrazoNegativo() {
        assertThatThrownBy(() -> new VerticeCurva(-1, null, null, new BigDecimal("0.14"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaTaxaNula() {
        assertThatThrownBy(() -> new VerticeCurva(30, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
