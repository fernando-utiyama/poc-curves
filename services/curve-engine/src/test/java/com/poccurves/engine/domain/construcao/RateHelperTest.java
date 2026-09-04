package com.poccurves.engine.domain.construcao;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.RateHelper;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class RateHelperTest {

    @Test
    void taxaDi1_insumoValido_devolveTaxa() {
        InsumoDI1 insumo = new InsumoDI1("DI1F26", new BigDecimal("0.105"), 252, LocalDate.of(2026, 1, 2));
        BigDecimal taxa = RateHelper.taxaDi1(insumo);
        assertEquals(new BigDecimal("0.105"), taxa);
    }

    @Test
    void taxaDi1_taxaNula_lancaException() {
        InsumoDI1 insumo = new InsumoDI1("DI1F26", null, 252, LocalDate.of(2026, 1, 2));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> RateHelper.taxaDi1(insumo));
        assertTrue(ex.getMessage().contains("DI1F26"));
    }

    @Test
    void taxaDi1_taxaInvalida_lancaException() {
        InsumoDI1 insumo = new InsumoDI1("DI1F26", new BigDecimal("-1.01"), 252, LocalDate.of(2026, 1, 2));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> RateHelper.taxaDi1(insumo));
        assertTrue(ex.getMessage().contains("-1.01"));
        assertTrue(ex.getMessage().contains("DI1F26"));
    }

    @Test
    void taxaDi1_taxaLimite_lancaException() {
        InsumoDI1 insumo = new InsumoDI1("DI1F26", new BigDecimal("-1.00"), 252, LocalDate.of(2026, 1, 2));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> RateHelper.taxaDi1(insumo));
        assertTrue(ex.getMessage().contains("-1"));
        assertTrue(ex.getMessage().contains("DI1F26"));
    }

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
