package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.interpolacao.InterpoladorLinear;
import com.poccurves.engine.domain.interpolacao.InterpoladorMonotonicoConvexo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterpoladorMonotonicoConvexoTest {

    private final InterpoladorMonotonicoConvexo interpolador = new InterpoladorMonotonicoConvexo();
    private final MathContext mc = MathContext.DECIMAL128;

    private Vertice v(int prazo, String taxa) {
        return new Vertice(prazo, null, null, new BigDecimal(taxa), null);
    }

    @Test
    void testVerticeExato() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(63, "0.11"),
                v(126, "0.12"),
                v(252, "0.13")
        );
        for (Vertice vertice : vertices) {
            BigDecimal resultado = interpolador.taxaEm(vertices, vertice.prazoDiasUteis(), mc);
            assertTrue(vertice.taxa().compareTo(resultado) == 0);
        }
    }

    @Test
    void testRetaExataCom2Vertices() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(252, "0.15")
        );
        InterpoladorLinear linear = new InterpoladorLinear();
        BigDecimal esperado = linear.taxaEm(vertices, 100, mc);
        BigDecimal resultado = interpolador.taxaEm(vertices, 100, mc);
        assertTrue(esperado.compareTo(resultado) == 0);
    }

    @Test
    void testSemOvershootEmDadosMonotonicosCrescentes() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(63, "0.105"),
                v(126, "0.108"),
                v(252, "0.120"),
                v(504, "0.121")
        );
        
        for (int i = 0; i < vertices.size() - 1; i++) {
            Vertice v1 = vertices.get(i);
            Vertice v2 = vertices.get(i + 1);
            int t1 = v1.prazoDiasUteis();
            int t2 = v2.prazoDiasUteis();
            BigDecimal r1 = v1.taxa();
            BigDecimal r2 = v2.taxa();
            
            double[] fatores = {0.1, 0.25, 0.5, 0.75, 0.9};
            for (double s : fatores) {
                int prazo = t1 + (int) Math.round(s * (t2 - t1));
                BigDecimal resultado = interpolador.taxaEm(vertices, prazo, mc);
                assertTrue(resultado.compareTo(r1) >= 0 && resultado.compareTo(r2) <= 0);
            }
        }
    }

    @Test
    void testTangenteZeroEmExtremoLocal() {
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(126, "0.15"),
                v(252, "0.08")
        );
        BigDecimal pico = new BigDecimal("0.15");
        
        BigDecimal res1 = interpolador.taxaEm(vertices, 120, mc);
        BigDecimal res2 = interpolador.taxaEm(vertices, 130, mc);
        
        assertTrue(res1.compareTo(pico) <= 0);
        assertTrue(res2.compareTo(pico) <= 0);
    }

    @Test
    void testIdentificador() {
        assertEquals("MONOTONICO_CONVEXO", interpolador.identificador());
    }

    @Test
    void testGuardClauses() {
        List<Vertice> v1 = List.of(v(21, "0.10"));
        assertThrows(IllegalArgumentException.class, () -> interpolador.taxaEm(v1, 100, mc));
        
        List<Vertice> vertices = List.of(v(21, "0.10"), v(252, "0.15"));
        assertThrows(IllegalArgumentException.class, () -> interpolador.taxaEm(vertices, 10, mc));
        assertThrows(IllegalArgumentException.class, () -> interpolador.taxaEm(vertices, 300, mc));
    }
}
