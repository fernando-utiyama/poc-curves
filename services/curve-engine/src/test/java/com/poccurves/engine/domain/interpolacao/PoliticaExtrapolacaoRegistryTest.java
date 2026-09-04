package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacao;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoTaxaConstante;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaExtrapolacaoRegistryTest {

    @Test
    void shouldResolveRegisteredPolicy() {
        PoliticaExtrapolacaoTaxaConstante taxaConstante = new PoliticaExtrapolacaoTaxaConstante();
        PoliticaExtrapolacaoRegistry registry = new PoliticaExtrapolacaoRegistry(List.of(taxaConstante));

        PoliticaExtrapolacao resolved = registry.resolver("TAXA_CONSTANTE");

        assertThat(resolved).isSameAs(taxaConstante);
        assertThat(resolved.identificador()).isEqualTo("TAXA_CONSTANTE");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenPolicyNotFound() {
        PoliticaExtrapolacaoRegistry registry = new PoliticaExtrapolacaoRegistry(List.of(new PoliticaExtrapolacaoTaxaConstante()));

        assertThatThrownBy(() -> registry.resolver("NAO_EXISTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAO_EXISTE");
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDuplicateIdentifiersAreRegistered() {
        PoliticaExtrapolacao fake1 = new PoliticaExtrapolacao() {
            @Override
            public String identificador() {
                return "DUPLICADO";
            }

            @Override
            public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis) {
                return BigDecimal.ZERO;
            }
        };

        PoliticaExtrapolacao fake2 = new PoliticaExtrapolacao() {
            @Override
            public String identificador() {
                return "DUPLICADO";
            }

            @Override
            public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis) {
                return BigDecimal.ZERO;
            }
        };

        assertThatThrownBy(() -> new PoliticaExtrapolacaoRegistry(List.of(fake1, fake2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUPLICADO");
    }

    @Test
    void shouldThrowNullPointerExceptionWhenPoliticasListIsNull() {
        assertThatThrownBy(() -> new PoliticaExtrapolacaoRegistry(null))
                .isInstanceOf(NullPointerException.class);
    }
}
