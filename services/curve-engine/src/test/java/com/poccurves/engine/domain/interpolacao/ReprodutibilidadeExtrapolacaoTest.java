package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoEstrita;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoForwardConstante;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoForwardLinear;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoTaxaConstante;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReprodutibilidadeExtrapolacaoTest {

    private Vertice v(int prazo, String taxa) {
        return new Vertice(prazo, null, null, new BigDecimal(taxa), null);
    }

    @Test
    void testReprodutibilidadeEstrita() {
        PoliticaExtrapolacaoEstrita politica = new PoliticaExtrapolacaoEstrita();
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(126, "0.11"),
                v(252, "0.12")
        );
        
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> politica.taxaEm(vertices, 300));
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> politica.taxaEm(vertices, 300));
        
        assertEquals(ex1.getClass(), ex2.getClass());
        assertEquals(ex1.getMessage(), ex2.getMessage());
    }

    @Test
    void testReprodutibilidadeTaxaConstante() {
        PoliticaExtrapolacaoTaxaConstante politica = new PoliticaExtrapolacaoTaxaConstante();
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
    void testReprodutibilidadeForwardConstante() {
        PoliticaExtrapolacaoForwardConstante politica = new PoliticaExtrapolacaoForwardConstante();
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
    void testReprodutibilidadeForwardLinear() {
        PoliticaExtrapolacaoForwardLinear politica = new PoliticaExtrapolacaoForwardLinear();
        List<Vertice> vertices = List.of(
                v(21, "0.10"),
                v(126, "0.11"),
                v(252, "0.12")
        );
        
        BigDecimal res1 = politica.taxaEm(vertices, 300);
        BigDecimal res2 = politica.taxaEm(vertices, 300);
        assertTrue(res1.compareTo(res2) == 0);
    }
}
