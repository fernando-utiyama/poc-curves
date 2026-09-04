package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaExtrapolacaoEstritaTest {

    private final PoliticaExtrapolacaoEstrita politica = new PoliticaExtrapolacaoEstrita();

    private final List<Vertice> vertices = List.of(
            new Vertice(30, null, null, new BigDecimal("0.10"), null),
            new Vertice(90, null, null, new BigDecimal("0.15"), null)
    );

    @Test
    void shouldReturnEstritaIdentifier() {
        assertThat(politica.identificador()).isEqualTo("ESTRITA");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoIsBelowFirstVertice() {
        assertThatThrownBy(() -> politica.taxaEm(vertices, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10")
                .hasMessageContaining("30")
                .hasMessageContaining("90");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoIsAboveLastVertice() {
        assertThatThrownBy(() -> politica.taxaEm(vertices, 200))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenVerticesListIsEmpty() {
        assertThatThrownBy(() -> politica.taxaEm(List.of(), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenVerticesListIsNull() {
        assertThatThrownBy(() -> politica.taxaEm(null, 10))
                .isInstanceOf(NullPointerException.class);
    }
}
