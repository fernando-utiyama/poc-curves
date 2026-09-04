package com.poccurves.engine.adapter.out.construcao;
import com.poccurves.engine.domain.construcao.CurveBootstrapper;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.curva.Vertice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltinModeloConstrucaoTest {

    private final BuiltinModeloConstrucao construtor = new BuiltinModeloConstrucao(new CurveBootstrapper());

    @Test
    void suportaApenasModelosBuiltin() {
        ModeloCurva builtin = ModeloCurva.builtin("PRE_DI1_B3", "nome");
        ModeloCurva groovy = ModeloCurva.importarGroovy("G", "g", "[]", "sha", "tester");

        assertThat(construtor.suporta(builtin)).isTrue();
        assertThat(construtor.suporta(groovy)).isFalse();
    }

    @Test
    void despachaParaOCurveBootstrapperQuandoOCodigoEhConhecido() {
        ModeloCurva modelo = ModeloCurva.builtin("PRE_DI1_B3", "nome");
        List<InsumoDI1> insumos = List.of(new InsumoDI1("DI1F26", new BigDecimal("13.50"), 21, LocalDate.of(2026, 1, 2)));

        CurvaJuros curva = construtor.construir(modelo, insumos);

        assertThat(curva.vertices()).containsExactly(new Vertice(21, null, LocalDate.of(2026, 1, 2), new BigDecimal("13.50"), null));
    }

    @Test
    void lancaExcecaoParaCodigoDeModeloBuiltinDesconhecido() {
        ModeloCurva modelo = ModeloCurva.builtin("MODELO_INEXISTENTE", "nome");

        assertThatThrownBy(() -> construtor.construir(modelo, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MODELO_INEXISTENTE");
    }
}
