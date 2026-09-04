package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogoModelosTest {

    @Test
    void deveResolverModeloRegistradoPorCodigo() {
        ModeloCurva modelo = ModeloCurva.builtin("PRE_DI1", "nome");
        CatalogoModelos catalogo = new CatalogoModelos(List.of(modelo));

        ModeloCurva resolvido = catalogo.resolver("PRE_DI1");

        assertThat(resolvido).isNotNull();
        assertThat(resolvido.codigo()).isEqualTo("PRE_DI1");
    }

    @Test
    void deveLancarExcecaoAoResolverCodigoNaoRegistrado() {
        CatalogoModelos catalogo = new CatalogoModelos(List.of(
                ModeloCurva.builtin("PRE_DI1", "nome")
        ));

        assertThatThrownBy(() -> catalogo.resolver("NAO_EXISTE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAO_EXISTE");
    }

    @Test
    void deveLancarExcecaoAoRegistrarModelosComCodigoDuplicado() {
        List<ModeloCurva> modelos = List.of(
                ModeloCurva.builtin("DUP", "Primeiro Modelo"),
                ModeloCurva.builtin("DUP", "Segundo Modelo")
        );

        assertThatThrownBy(() -> new CatalogoModelos(modelos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DUP");
    }

    @Test
    void deveLancarExcecaoQuandoListaDeModelosForNula() {
        assertThatThrownBy(() -> new CatalogoModelos(null))
                .isInstanceOf(NullPointerException.class);
    }
}
