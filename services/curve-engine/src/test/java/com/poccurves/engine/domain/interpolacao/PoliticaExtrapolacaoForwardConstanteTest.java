package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoForwardConstante;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaExtrapolacaoForwardConstanteTest {

    private final PoliticaExtrapolacaoForwardConstante extrapolador = new PoliticaExtrapolacaoForwardConstante();

    @Test
    void shouldReturnTaxaDoPrimeiroVerticeWhenOnlyOneVerticeExists() {
        List<Vertice> vertices = List.of(
                new Vertice(252, null, null, new BigDecimal("0.10"), null)
        );
        BigDecimal taxa = extrapolador.taxaEm(vertices, 504);
        assertThat(taxa).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    void shouldThrowExceptionWhenPrazoIsInsideInterval() {
        List<Vertice> vertices = List.of(
                new Vertice(252, null, null, new BigDecimal("0.10"), null),
                new Vertice(504, null, null, new BigDecimal("0.12"), null)
        );
        assertThatThrownBy(() -> extrapolador.taxaEm(vertices, 300))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não requer extrapolação");
    }

    @Test
    void shouldMaintainForwardRateConstantWhenExtrapolatingAbove() {
        List<Vertice> vertices = List.of(
                new Vertice(252, null, null, new BigDecimal("0.10"), null),
                new Vertice(504, null, null, new BigDecimal("0.12"), null),
                new Vertice(756, null, null, new BigDecimal("0.14"), null)
        );

        double r1 = 0.12; // t1 = 504
        double r2 = 0.14; // t2 = 756
        double df1 = Math.pow(1.0 + r1, -504 / 252.0);
        double df2 = Math.pow(1.0 + r2, -756 / 252.0);
        
        double fEsperado = Math.pow(df1 / df2, 252.0 / (756 - 504)) - 1.0;

        int prazoExtrapolado = 1008;
        BigDecimal taxaExtrapolada = extrapolador.taxaEm(vertices, prazoExtrapolado);
        
        double dfExtrapolado = Math.pow(1.0 + taxaExtrapolada.doubleValue(), -prazoExtrapolado / 252.0);
        
        double fObtido = Math.pow(df2 / dfExtrapolado, 252.0 / (prazoExtrapolado - 756)) - 1.0;

        assertThat(fObtido).isCloseTo(fEsperado, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void shouldMaintainForwardRateConstantWhenExtrapolatingBelow() {
        List<Vertice> vertices = List.of(
                new Vertice(252, null, null, new BigDecimal("0.10"), null),
                new Vertice(504, null, null, new BigDecimal("0.12"), null),
                new Vertice(756, null, null, new BigDecimal("0.14"), null)
        );

        double r1 = 0.10; // t1 = 252
        double r2 = 0.12; // t2 = 504
        double df1 = Math.pow(1.0 + r1, -252 / 252.0);
        double df2 = Math.pow(1.0 + r2, -504 / 252.0);
        
        double fEsperado = Math.pow(df1 / df2, 252.0 / (504 - 252)) - 1.0;

        int prazoExtrapolado = 126;
        BigDecimal taxaExtrapolada = extrapolador.taxaEm(vertices, prazoExtrapolado);
        
        double dfExtrapolado = Math.pow(1.0 + taxaExtrapolada.doubleValue(), -prazoExtrapolado / 252.0);
        
        double fObtido = Math.pow(dfExtrapolado / df1, 252.0 / (252 - prazoExtrapolado)) - 1.0;

        assertThat(fObtido).isCloseTo(fEsperado, org.assertj.core.data.Offset.offset(1e-12));
    }
}
