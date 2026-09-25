package com.poccurves.engine.adapter.out.construcao;
import com.poccurves.engine.application.construcao.CurvaJuros;
import com.poccurves.engine.application.model.ModeloCurva;
import com.poccurves.engine.application.construcao.Vertice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltinModeloConstrucaoTest {

    private final BuiltinModeloConstrucao construtor = new BuiltinModeloConstrucao();

    @Test
    void suportaApenasModelosBuiltin() {
        ModeloCurva builtin = ModeloCurva.builtin(BuiltinModeloConstrucao.CODIGO_TAXA_SWAP_TRANSCRICAO_B3, "nome");
        ModeloCurva groovy = ModeloCurva.importarGroovy("G", "g", "[]", "sha", "tester");

        assertThat(construtor.suporta(builtin)).isTrue();
        assertThat(construtor.suporta(groovy)).isFalse();
    }

    @Test
    void transcreveOsVerticesSemRecalculoQuandoOCodigoEhConhecido() {
        ModeloCurva modelo = ModeloCurva.builtin(BuiltinModeloConstrucao.CODIGO_TAXA_SWAP_TRANSCRICAO_B3, "nome");
        List<Vertice> insumos = List.of(new Vertice(21, null, LocalDate.of(2026, 1, 2), new BigDecimal("13.50"), null));

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
