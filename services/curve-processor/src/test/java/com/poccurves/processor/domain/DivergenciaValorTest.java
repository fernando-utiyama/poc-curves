package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DivergenciaValorTest {

    @Test
    void deveRetornarVazioQuandoValoresForemIguaisComMesmaEscala() {
        Optional<DivergenciaValor> resultado = DivergenciaValor.detectar(
                "DI1F27",
                new BigDecimal("10.500"),
                new BigDecimal("10.500")
        );

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveRetornarVazioQuandoValoresForemNumericamenteIguaisComEscalaDiferente() {
        Optional<DivergenciaValor> resultado = DivergenciaValor.detectar(
                "DI1F27",
                new BigDecimal("1.10"),
                new BigDecimal("1.100")
        );

        assertThat(resultado).isEmpty();
    }

    @Test
    void deveRetornarDivergenciaQuandoValoresForemDiferentes() {
        Optional<DivergenciaValor> resultado = DivergenciaValor.detectar(
                "DI1F27",
                new BigDecimal("10.500"),
                new BigDecimal("10.600")
        );

        assertThat(resultado).isPresent();
        DivergenciaValor divergencia = resultado.get();
        assertThat(divergencia.chaveInstrumento()).isEqualTo("DI1F27");
        assertThat(divergencia.valorAnterior()).isEqualByComparingTo(new BigDecimal("10.500"));
        assertThat(divergencia.valorNovo()).isEqualByComparingTo(new BigDecimal("10.600"));
    }

    @Test
    void deveLancarIllegalArgumentExceptionQuandoChaveInstrumentoForNulaOuVaziaEmDetectar() {
        assertThatThrownBy(() -> DivergenciaValor.detectar(null, BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chaveInstrumento");

        assertThatThrownBy(() -> DivergenciaValor.detectar("", BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chaveInstrumento");
    }

    @Test
    void deveLancarNullPointerExceptionQuandoValoresForemNulosEmDetectar() {
        assertThatThrownBy(() -> DivergenciaValor.detectar("chave", null, BigDecimal.TEN))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("valorAnterior");

        assertThatThrownBy(() -> DivergenciaValor.detectar("chave", BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("valorNovo");
    }

    @Test
    void deveLancarIllegalArgumentExceptionAoConstruirDiretamenteComChaveInvalida() {
        assertThatThrownBy(() -> new DivergenciaValor("", BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chaveInstrumento");
    }
}
