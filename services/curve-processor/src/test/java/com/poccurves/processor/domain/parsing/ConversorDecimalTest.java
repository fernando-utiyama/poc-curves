package com.poccurves.processor.domain.parsing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversorDecimalTest {

    @Test
    void converteValorRealComSeparadorPonto() {
        // 14.129 é a AdjstdQtTax real do contrato DI1Z28 no pregão de 2026-08-21
        BigDecimal resultado = ConversorDecimal.paraBigDecimal("14.129", '.');
        assertThat(resultado).isEqualByComparingTo("14.129");
    }

    @Test
    void preservaEscalaExataDoTextoOriginal() {
        BigDecimal resultado = ConversorDecimal.paraBigDecimal("74199.92", '.');
        assertThat(resultado.scale()).isEqualTo(2);
    }

    @Test
    void converteValorComSeparadorVirgula() {
        BigDecimal resultado = ConversorDecimal.paraBigDecimal("14,129", ',');
        assertThat(resultado).isEqualByComparingTo("14.129");
    }

    @Test
    void naoConfundeSeparadorDeMilharComDecimal_quandoSeparadorDeclaradoEhVirgula() {
        // Se a fonte diz que "," é o decimal, "." não deveria estar presente;
        // este teste documenta que a função não tenta adivinhar milhar, só troca o separador declarado.
        BigDecimal resultado = ConversorDecimal.paraBigDecimal("1234,56", ',');
        assertThat(resultado).isEqualByComparingTo("1234.56");
    }

    @Test
    void lancaExcecaoParaValorNaoNumerico() {
        assertThatThrownBy(() -> ConversorDecimal.paraBigDecimal("abc", '.'))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("abc");
    }

    @Test
    void lancaExcecaoParaValorVazio() {
        assertThatThrownBy(() -> ConversorDecimal.paraBigDecimal("", '.'))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lancaExcecaoParaValorNulo() {
        assertThatThrownBy(() -> ConversorDecimal.paraBigDecimal(null, '.'))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
