package com.poccurves.engine.application.construcao;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class RateHelperTest {

    @Test
    void taxaCdi_taxaValida_devolveTaxa() {
        BigDecimal taxa = RateHelper.taxaCdi(new BigDecimal("0.1390"));
        assertEquals(new BigDecimal("0.1390"), taxa);
    }

    @Test
    void taxaCdi_taxaNula_lancaException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> RateHelper.taxaCdi(null));
        assertTrue(ex.getMessage().contains("CDI"));
    }

    @Test
    void taxaCdi_taxaInvalida_lancaException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RateHelper.taxaCdi(new BigDecimal("-1.01")));
        assertTrue(ex.getMessage().contains("-1.01"));
    }

    @Test
    void taxaCdi_taxaLimite_lancaException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RateHelper.taxaCdi(new BigDecimal("-1.00")));
        assertTrue(ex.getMessage().contains("-1"));
    }

    @Test
    void taxaInflacaoImplicita_lancaException() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, RateHelper::taxaInflacaoImplicita);
        assertTrue(ex.getMessage().contains("fonte de dado"));
    }
}
