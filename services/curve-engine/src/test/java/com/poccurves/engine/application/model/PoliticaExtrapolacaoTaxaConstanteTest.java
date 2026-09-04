package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaExtrapolacaoTaxaConstanteTest {

    private final PoliticaExtrapolacaoTaxaConstante politica = new PoliticaExtrapolacaoTaxaConstante();

    private final List<Vertice> vertices = List.of(
            new Vertice(30, null, null, new BigDecimal("0.10"), null),
            new Vertice(90, null, null, new BigDecimal("0.15"), null)
    );

    @Test
    void shouldReturnTaxaConstanteIdentifier() {
        assertThat(politica.identificador()).isEqualTo("TAXA_CONSTANTE");
    }

    @Test
    void shouldReturnFirstVerticeRateWhenPrazoIsBelowFirstVertice() {
        assertThat(politica.taxaEm(vertices, 10))
                .isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    void shouldReturnLastVerticeRateWhenPrazoIsAboveLastVertice() {
        assertThat(politica.taxaEm(vertices, 200))
                .isEqualByComparingTo(new BigDecimal("0.15"));
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
