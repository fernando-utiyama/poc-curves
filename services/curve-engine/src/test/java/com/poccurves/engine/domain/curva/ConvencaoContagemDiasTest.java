package com.poccurves.engine.domain.curva;
import com.poccurves.engine.domain.curva.ConvencaoContagemDias;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConvencaoContagemDiasTest {

    @Test
    void shouldReturnCorrectBaseForConventions() {
        assertThat(ConvencaoContagemDias.ATUAL_360.base()).isEqualTo(360);
        assertThat(ConvencaoContagemDias.ATUAL_365.base()).isEqualTo(365);
    }

    @Test
    void shouldCalculateYearFractionForAtual360() {
        LocalDate dataInicio = LocalDate.of(2026, 1, 1);
        LocalDate dataFim = dataInicio.plusDays(90);
        BigDecimal fracao = ConvencaoContagemDias.ATUAL_360.fracaoAno(dataInicio, dataFim, MathContext.DECIMAL64);
        assertThat(fracao).isEqualByComparingTo(new BigDecimal("0.25"));
    }

    @Test
    void shouldCalculateYearFractionForAtual365() {
        LocalDate dataInicio = LocalDate.of(2026, 1, 1);
        LocalDate dataFim = dataInicio.plusDays(73);
        BigDecimal fracao = ConvencaoContagemDias.ATUAL_365.fracaoAno(dataInicio, dataFim, MathContext.DECIMAL64);
        assertThat(fracao).isEqualByComparingTo(new BigDecimal("0.2"));
    }

    @Test
    void shouldReturnZeroWhenDataFimEqualsDataInicio() {
        LocalDate dataInicio = LocalDate.of(2026, 1, 1);
        BigDecimal fracao360 = ConvencaoContagemDias.ATUAL_360.fracaoAno(dataInicio, dataInicio, MathContext.DECIMAL64);
        BigDecimal fracao365 = ConvencaoContagemDias.ATUAL_365.fracaoAno(dataInicio, dataInicio, MathContext.DECIMAL64);
        assertThat(fracao360).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fracao365).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDataFimIsBeforeDataInicio() {
        LocalDate dataInicio = LocalDate.of(2026, 1, 1).plusDays(10);
        LocalDate dataFim = LocalDate.of(2026, 1, 1);
        assertThatThrownBy(() -> ConvencaoContagemDias.ATUAL_360.fracaoAno(dataInicio, dataFim, MathContext.DECIMAL64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenArgumentsAreNullInFracaoAno() {
        LocalDate dataInicio = LocalDate.of(2026, 1, 1);
        LocalDate dataFim = dataInicio.plusDays(30);

        assertThatThrownBy(() -> ConvencaoContagemDias.ATUAL_360.fracaoAno(null, dataFim, MathContext.DECIMAL64))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> ConvencaoContagemDias.ATUAL_360.fracaoAno(dataInicio, null, MathContext.DECIMAL64))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> ConvencaoContagemDias.ATUAL_360.fracaoAno(dataInicio, dataFim, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCalculateDiasCorridosCorrectly() {
        LocalDate dataInicio = LocalDate.of(2026, 1, 1);
        LocalDate dataFim = dataInicio.plusDays(45);
        long dias = ConvencaoContagemDias.diasCorridos(dataInicio, dataFim);
        assertThat(dias).isEqualTo(45L);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenArgumentsAreNullInDiasCorridos() {
        LocalDate data = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> ConvencaoContagemDias.diasCorridos(null, data))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> ConvencaoContagemDias.diasCorridos(data, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCalculateYearFractionForBase252() {
        BigDecimal fracaoIntegral = ConvencaoContagemDias.fracaoAnoBase252(252L, MathContext.DECIMAL64);
        assertThat(fracaoIntegral).isEqualByComparingTo(BigDecimal.ONE);

        BigDecimal fracaoMeia = ConvencaoContagemDias.fracaoAnoBase252(126L, MathContext.DECIMAL64);
        assertThat(fracaoMeia).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDiasUteisIsNegative() {
        assertThatThrownBy(() -> ConvencaoContagemDias.fracaoAnoBase252(-1L, MathContext.DECIMAL64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("diasUteis não pode ser negativo");
    }

    @Test
    void shouldThrowNullPointerExceptionWhenMathContextIsNullInBase252() {
        assertThatThrownBy(() -> ConvencaoContagemDias.fracaoAnoBase252(252L, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("mathContext não pode ser nulo");
    }
}
