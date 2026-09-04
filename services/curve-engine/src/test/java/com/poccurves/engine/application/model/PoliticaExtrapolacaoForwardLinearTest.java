package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticaExtrapolacaoForwardLinearTest {

    private final PoliticaExtrapolacaoForwardLinear politica = new PoliticaExtrapolacaoForwardLinear();

    private Vertice v(int prazo, String taxa) {
        return new Vertice(prazo, null, null, new BigDecimal(taxa), null);
    }

    /**
     * Curva plana (mesma taxa nos 3 vértices) faz a taxa forward implícita entre
     * qualquer par ser exatamente igual à taxa constante — logo a "tendência" (fA-fB)
     * é zero e a extrapolação de FORWARD_LINEAR deve coincidir com FORWARD_CONSTANTE.
     * Comparação NÃO é bit-exata: os dois caminhos passam por sequências diferentes de
     * RoundingPolicy.powerRaw (log/exp em MathContext.DECIMAL128) — FORWARD_LINEAR soma
     * duas chamadas a forwardImplicito mais a aritmética de inclinação, FORWARD_CONSTANTE
     * calcula um único forward direto — e cada powerRaw arredonda em 34 dígitos
     * significativos, então ruído de arredondamento da ordem de 1E-16 entre os dois
     * caminhos é esperado mesmo quando o valor matemático é idêntico (confirmado
     * manualmente: diferença real observada ~4.4E-16 para taxa 0.10). Um epsilon de
     * 1E-10 é ~6 ordens de grandeza maior que esse ruído, então continua provando a
     * consistência sem exigir precisão além do que BigDecimal garante entre caminhos
     * de cálculo diferentes.
     */
    private static final BigDecimal EPSILON_CONSISTENCIA = new BigDecimal("0.0000000001");

    @Test
    void testConsistenciaComForwardConstanteQuandoTendenciaPlana() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(126, "0.10"),
                v(252, "0.10")
        );

        PoliticaExtrapolacaoForwardConstante forwardConstante = new PoliticaExtrapolacaoForwardConstante();

        BigDecimal resultadoAcimaLinear = politica.taxaEm(vertices, 400);
        BigDecimal resultadoAcimaConstante = forwardConstante.taxaEm(vertices, 400);
        assertTrue(resultadoAcimaLinear.subtract(resultadoAcimaConstante).abs().compareTo(EPSILON_CONSISTENCIA) < 0);

        BigDecimal resultadoAbaixoLinear = politica.taxaEm(vertices, 10);
        BigDecimal resultadoAbaixoConstante = forwardConstante.taxaEm(vertices, 10);
        assertTrue(resultadoAbaixoLinear.subtract(resultadoAbaixoConstante).abs().compareTo(EPSILON_CONSISTENCIA) < 0);
    }

    @Test
    void testReprodutibilidadeDeterministica() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(126, "0.11"),
                v(252, "0.12")
        );
        
        BigDecimal res1 = politica.taxaEm(vertices, 300);
        BigDecimal res2 = politica.taxaEm(vertices, 300);
        assertTrue(res1.compareTo(res2) == 0);
    }

    @Test
    void testDirecaoDaTendencia() {
        List<Vertice> vertices = List.of(
                v(21, "0.08"),
                v(126, "0.10"),
                v(252, "0.14")
        );
        
        BigDecimal resMaisProximo = politica.taxaEm(vertices, 300);
        BigDecimal resMaisDistante = politica.taxaEm(vertices, 600);
        
        assertTrue(resMaisDistante.compareTo(resMaisProximo) > 0);
    }

    @Test
    void testIdentificador() {
        assertEquals("FORWARD_LINEAR", politica.identificador());
    }

    @Test
    void testGuardClauseMinimoDeVertices() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(252, "0.15")
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> politica.taxaEm(vertices, 300));
        assertTrue(ex.getMessage().contains("3 vértices"));
    }

    @Test
    void testGuardClausePrazoDentroDoIntervalo() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(126, "0.11"),
                v(252, "0.12")
        );
        assertThrows(IllegalArgumentException.class, () -> politica.taxaEm(vertices, 100));
    }
}
