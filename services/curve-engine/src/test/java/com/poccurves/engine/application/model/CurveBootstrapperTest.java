package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CurveBootstrapperTest {

    private final CurveBootstrapper bootstrapper = new CurveBootstrapper();

    @Test
    void montarCurvaPreDeDi1_insumosValidos_montaCurvaCorretamente() {
        List<InsumoDI1> insumos = List.of(
            new InsumoDI1("DI1F22", new BigDecimal("0.10"), 21, LocalDate.of(2022, 1, 2)),
            new InsumoDI1("DI1N22", new BigDecimal("0.11"), 126, LocalDate.of(2022, 7, 1)),
            new InsumoDI1("DI1F23", new BigDecimal("0.12"), 252, LocalDate.of(2023, 1, 2))
        );

        CurvaJuros curva = bootstrapper.montarCurvaPreDeDi1(insumos);

        List<Vertice> vertices = curva.vertices();
        assertEquals(3, vertices.size());
        
        assertEquals(21, vertices.get(0).prazoDiasUteis());
        assertEquals(new BigDecimal("0.10"), vertices.get(0).taxa());
        
        assertEquals(126, vertices.get(1).prazoDiasUteis());
        assertEquals(new BigDecimal("0.11"), vertices.get(1).taxa());
        
        assertEquals(252, vertices.get(2).prazoDiasUteis());
        assertEquals(new BigDecimal("0.12"), vertices.get(2).taxa());
    }

    @Test
    void montarCurvaPreDeDi1_insumoIncompleto_ignorado() {
        List<InsumoDI1> insumos = List.of(
            new InsumoDI1("DI1F22", new BigDecimal("0.10"), 21, LocalDate.of(2022, 1, 2)),
            new InsumoDI1("DI1N22", null, 126, LocalDate.of(2022, 7, 1)), // taxa nula
            new InsumoDI1("DI1V22", new BigDecimal("0.11"), null, LocalDate.of(2022, 10, 1)), // prazo nulo
            new InsumoDI1("DI1F23", new BigDecimal("0.12"), 252, LocalDate.of(2023, 1, 2))
        );

        CurvaJuros curva = bootstrapper.montarCurvaPreDeDi1(insumos);

        List<Vertice> vertices = curva.vertices();
        assertEquals(2, vertices.size());
        assertEquals(21, vertices.get(0).prazoDiasUteis());
        assertEquals(252, vertices.get(1).prazoDiasUteis());
    }

    @Test
    void montarCurvaPreDeDi1_listaVazia_lancaException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            bootstrapper.montarCurvaPreDeDi1(List.of())
        );
        assertTrue(ex.getMessage().contains("recebidos 0 insumos"));
    }

    @Test
    void montarCurvaPreDeDi1_soInsumosIncompletos_lancaException() {
        List<InsumoDI1> insumos = List.of(
            new InsumoDI1("DI1N22", null, 126, LocalDate.of(2022, 7, 1))
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            bootstrapper.montarCurvaPreDeDi1(insumos)
        );
        assertTrue(ex.getMessage().contains("nenhum insumo"));
    }

    @Test
    void montarCurvaPreDeDi1_prazosDuplicados_propagaException() {
        List<InsumoDI1> insumos = List.of(
            new InsumoDI1("DI1F22", new BigDecimal("0.10"), 21, LocalDate.of(2022, 1, 2)),
            new InsumoDI1("DI1G22", new BigDecimal("0.11"), 21, LocalDate.of(2022, 2, 1))
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            bootstrapper.montarCurvaPreDeDi1(insumos)
        );
        assertTrue(ex.getMessage().contains("prazo duplicado: 21 dias úteis"));
    }

    @Test
    void montarCurvaPreDeDi1_mesmoInsumoDuasVezes_produzVerticesIdenticos() {
        List<InsumoDI1> insumos = List.of(
            new InsumoDI1("DI1F22", new BigDecimal("0.10"), 21, LocalDate.of(2022, 1, 2)),
            new InsumoDI1("DI1N22", new BigDecimal("0.11"), 126, LocalDate.of(2022, 7, 1)),
            new InsumoDI1("DI1F23", new BigDecimal("0.12"), 252, LocalDate.of(2023, 1, 2))
        );

        CurvaJuros curvaUm = bootstrapper.montarCurvaPreDeDi1(insumos);
        CurvaJuros curvaDois = bootstrapper.montarCurvaPreDeDi1(insumos);

        List<Vertice> verticesUm = curvaUm.vertices();
        List<Vertice> verticesDois = curvaDois.vertices();
        assertEquals(verticesUm.size(), verticesDois.size());
        for (int i = 0; i < verticesUm.size(); i++) {
            assertEquals(verticesUm.get(i).prazoDiasUteis(), verticesDois.get(i).prazoDiasUteis());
            assertEquals(0, verticesUm.get(i).taxa().compareTo(verticesDois.get(i).taxa()),
                    "taxa deve ser identica digito a digito entre as duas construcoes, prazo=" + verticesUm.get(i).prazoDiasUteis());
        }
    }
}

