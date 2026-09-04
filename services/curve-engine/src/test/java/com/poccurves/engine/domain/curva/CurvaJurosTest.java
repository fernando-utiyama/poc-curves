package com.poccurves.engine.domain.curva;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.curva.Vertice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurvaJurosTest {

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoDiasUteisIsZero() {
        assertThatThrownBy(() -> new Vertice(0, null, null, new BigDecimal("0.1050"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoDiasUteisIsNegative() {
        assertThatThrownBy(() -> new Vertice(-1, null, null, new BigDecimal("0.1050"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTaxaIsNull() {
        assertThatThrownBy(() -> new Vertice(30, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldOrderVerticesByPrazoDiasUteisAscending() {
        Vertice v1 = new Vertice(21, null, null, new BigDecimal("0.1000"), null);
        Vertice v2 = new Vertice(42, null, null, new BigDecimal("0.1050"), null);
        Vertice v3 = new Vertice(63, null, null, new BigDecimal("0.1100"), null);

        CurvaJuros curva = CurvaJuros.de(List.of(v3, v1, v2));

        assertThat(curva.vertices()).containsExactly(v1, v2, v3);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenVerticesHaveDuplicatePrazo() {
        Vertice v1 = new Vertice(21, null, null, new BigDecimal("0.1000"), null);
        Vertice v2 = new Vertice(21, null, null, new BigDecimal("0.1050"), null);

        assertThatThrownBy(() -> CurvaJuros.de(List.of(v1, v2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("21");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenVerticesListIsEmpty() {
        assertThatThrownBy(() -> CurvaJuros.de(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenVerticesListIsNull() {
        assertThatThrownBy(() -> CurvaJuros.de(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldReturnExactTaxaWhenPrazoIsAVertice() {
        Vertice v1 = new Vertice(21, null, null, new BigDecimal("0.1000"), null);
        Vertice v2 = new Vertice(42, null, null, new BigDecimal("0.1050"), null);

        CurvaJuros curva = CurvaJuros.de(List.of(v1, v2));

        assertThat(curva.taxaEm(21)).isEqualByComparingTo(new BigDecimal("0.1000"));
        assertThat(curva.taxaEm(42)).isEqualByComparingTo(new BigDecimal("0.1050"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoIsNotAVerticeForTaxaEm() {
        Vertice v1 = new Vertice(21, null, null, new BigDecimal("0.1000"), null);
        CurvaJuros curva = CurvaJuros.de(List.of(v1));

        assertThatThrownBy(() -> curva.taxaEm(30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnEmptyOptionalWhenFatorDescontoIsNullAndPresentWhenNotNull() {
        Vertice v1 = new Vertice(21, null, null, new BigDecimal("0.1000"), null);
        Vertice v2 = new Vertice(42, null, null, new BigDecimal("0.1050"), new BigDecimal("0.9850"));

        CurvaJuros curva = CurvaJuros.de(List.of(v1, v2));

        assertThat(curva.fatorDescontoEm(21)).isEmpty();
        assertThat(curva.fatorDescontoEm(42))
                .isPresent()
                .hasValueSatisfying(fd -> assertThat(fd).isEqualByComparingTo(new BigDecimal("0.9850")));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPrazoIsNotAVerticeForFatorDescontoEm() {
        Vertice v1 = new Vertice(21, null, null, new BigDecimal("0.1000"), null);
        CurvaJuros curva = CurvaJuros.de(List.of(v1));

        assertThatThrownBy(() -> curva.fatorDescontoEm(30))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
