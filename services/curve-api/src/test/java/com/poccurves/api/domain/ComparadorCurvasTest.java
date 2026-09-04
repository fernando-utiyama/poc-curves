package com.poccurves.api.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComparadorCurvasTest {

    @Test
    @DisplayName("Deve comparar duas curvas com os mesmos prazos e retornar pontos ordenados com presenteEmAmbas true")
    void deveCompararCurvasComMesmosPrazos() {
        Map<Integer, BigDecimal> curvaA = Map.of(
                30, new BigDecimal("10.50"),
                60, new BigDecimal("11.00"),
                90, new BigDecimal("11.50")
        );
        Map<Integer, BigDecimal> curvaB = Map.of(
                30, new BigDecimal("10.75"),
                60, new BigDecimal("11.10"),
                90, new BigDecimal("11.60")
        );

        List<PontoComparacao> resultado = ComparadorCurvas.comparar(curvaA, curvaB);

        assertThat(resultado).hasSize(3);
        assertThat(resultado).extracting(PontoComparacao::prazoDiasUteis)
                .containsExactly(30, 60, 90);
        assertThat(resultado).allMatch(PontoComparacao::presenteEmAmbas);

        assertThat(resultado.get(0)).isEqualTo(new PontoComparacao(30, new BigDecimal("10.50"), new BigDecimal("10.75")));
        assertThat(resultado.get(1)).isEqualTo(new PontoComparacao(60, new BigDecimal("11.00"), new BigDecimal("11.10")));
        assertThat(resultado.get(2)).isEqualTo(new PontoComparacao(90, new BigDecimal("11.50"), new BigDecimal("11.60")));
    }

    @Test
    @DisplayName("Deve identificar prazo presente apenas na curva A com taxaB nula e presenteEmAmbas false")
    void deveIdentificarPrazoPresenteApenasNaCurvaA() {
        Map<Integer, BigDecimal> curvaA = Map.of(30, new BigDecimal("10.50"));
        Map<Integer, BigDecimal> curvaB = Map.of();

        List<PontoComparacao> resultado = ComparadorCurvas.comparar(curvaA, curvaB);

        assertThat(resultado).hasSize(1);
        PontoComparacao ponto = resultado.get(0);
        assertThat(ponto.prazoDiasUteis()).isEqualTo(30);
        assertThat(ponto.taxaA()).isNotNull().isEqualByComparingTo("10.50");
        assertThat(ponto.taxaB()).isNull();
        assertThat(ponto.presenteEmAmbas()).isFalse();
    }

    @Test
    @DisplayName("Deve identificar prazo presente apenas na curva B com taxaA nula e presenteEmAmbas false")
    void deveIdentificarPrazoPresenteApenasNaCurvaB() {
        Map<Integer, BigDecimal> curvaA = Map.of();
        Map<Integer, BigDecimal> curvaB = Map.of(45, new BigDecimal("10.80"));

        List<PontoComparacao> resultado = ComparadorCurvas.comparar(curvaA, curvaB);

        assertThat(resultado).hasSize(1);
        PontoComparacao ponto = resultado.get(0);
        assertThat(ponto.prazoDiasUteis()).isEqualTo(45);
        assertThat(ponto.taxaA()).isNull();
        assertThat(ponto.taxaB()).isNotNull().isEqualByComparingTo("10.80");
        assertThat(ponto.presenteEmAmbas()).isFalse();
    }

    @Test
    @DisplayName("Deve retornar lista vazia ao comparar duas curvas vazias")
    void deveRetornarListaVaziaParaCurvasVazias() {
        List<PontoComparacao> resultado = ComparadorCurvas.comparar(Map.of(), Map.of());

        assertThat(resultado).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Deve lançar NullPointerException quando curvaA ou curvaB for nula")
    void deveLancarNullPointerExceptionQuandoCurvaNula() {
        assertThatThrownBy(() -> ComparadorCurvas.comparar(null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("curvaA não pode ser nula");

        assertThatThrownBy(() -> ComparadorCurvas.comparar(Map.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("curvaB não pode ser nula");
    }

    @Test
    @DisplayName("Deve ordenar prazos crescentemente independentemente da ordem de inserção nos mapas de entrada")
    void deveOrdenarPrazosCrescentementeIndependentementeDaOrdemDeInsercao() {
        Map<Integer, BigDecimal> curvaA = new LinkedHashMap<>();
        curvaA.put(90, new BigDecimal("11.50"));
        curvaA.put(30, new BigDecimal("10.50"));
        curvaA.put(60, new BigDecimal("11.00"));

        Map<Integer, BigDecimal> curvaB = new LinkedHashMap<>();
        curvaB.put(60, new BigDecimal("11.10"));
        curvaB.put(90, new BigDecimal("11.60"));
        curvaB.put(30, new BigDecimal("10.75"));

        List<PontoComparacao> resultado = ComparadorCurvas.comparar(curvaA, curvaB);

        assertThat(resultado).extracting(PontoComparacao::prazoDiasUteis)
                .containsExactly(30, 60, 90);
    }
}
