package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReconciliadorCurvaTest {

    private final ReconciliadorCurva reconciliador = new ReconciliadorCurva();

    @Test
    void reconciliar_curvasIdenticas_semDiscrepancia() {
        CurvaJuros c1 = CurvaJuros.de(List.of(
            new Vertice(21, null, LocalDate.of(2022, 1, 2), new BigDecimal("0.10"), null),
            new Vertice(252, null, LocalDate.of(2023, 1, 2), new BigDecimal("0.12"), null)
        ));
        
        List<ReconciliadorCurva.DiscrepanciaReconciliacao> resultado = reconciliador.reconciliar(c1, c1, new BigDecimal("0.001"));
        assertTrue(resultado.isEmpty());
    }

    @Test
    void reconciliar_taxaForaDaTolerancia_geraDiscrepancia() {
        CurvaJuros construida = CurvaJuros.de(List.of(
            new Vertice(21, null, LocalDate.of(2022, 1, 2), new BigDecimal("0.10"), null),
            new Vertice(252, null, LocalDate.of(2023, 1, 2), new BigDecimal("0.125"), null)
        ));
        CurvaJuros referencia = CurvaJuros.de(List.of(
            new Vertice(21, null, LocalDate.of(2022, 1, 2), new BigDecimal("0.10"), null),
            new Vertice(252, null, LocalDate.of(2023, 1, 2), new BigDecimal("0.12"), null)
        ));
        
        List<ReconciliadorCurva.DiscrepanciaReconciliacao> resultado = reconciliador.reconciliar(construida, referencia, new BigDecimal("0.001"));
        
        assertEquals(1, resultado.size());
        ReconciliadorCurva.DiscrepanciaReconciliacao d = resultado.get(0);
        assertEquals(252, d.prazoDiasUteis());
        assertEquals(new BigDecimal("0.125"), d.taxaConstruida());
        assertEquals(new BigDecimal("0.12"), d.taxaReferencia());
        assertEquals(new BigDecimal("0.005"), d.diferencaAbsoluta());
    }

    @Test
    void reconciliar_prazoSoNumaCurva_ignorado() {
        CurvaJuros construida = CurvaJuros.de(List.of(
            new Vertice(21, null, LocalDate.of(2022, 1, 2), new BigDecimal("0.10"), null),
            new Vertice(126, null, LocalDate.of(2022, 7, 1), new BigDecimal("0.11"), null)
        ));
        CurvaJuros referencia = CurvaJuros.de(List.of(
            new Vertice(21, null, LocalDate.of(2022, 1, 2), new BigDecimal("0.10"), null),
            new Vertice(252, null, LocalDate.of(2023, 1, 2), new BigDecimal("0.12"), null)
        ));
        
        List<ReconciliadorCurva.DiscrepanciaReconciliacao> resultado = reconciliador.reconciliar(construida, referencia, new BigDecimal("0.001"));
        
        assertTrue(resultado.isEmpty());
    }

    @Test
    void reconciliar_toleranciaNegativa_lancaException() {
        CurvaJuros c1 = CurvaJuros.de(List.of(
            new Vertice(21, null, LocalDate.of(2022, 1, 2), new BigDecimal("0.10"), null)
        ));
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            reconciliador.reconciliar(c1, c1, new BigDecimal("-0.001"))
        );
        assertTrue(ex.getMessage().contains("toleranciaAbsoluta não pode ser negativa"));
    }
}
