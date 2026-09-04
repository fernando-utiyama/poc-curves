package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeloCurvaTest {

    @Test
    void deveCriarModeloBuiltinComCamposPadrao() {
        ModeloCurva modelo = ModeloCurva.builtin("PRE_DI1", "Curva PRE DI1 embutida");

        assertThat(modelo.id()).isNotNull();
        assertThat(modelo.codigo()).isEqualTo("PRE_DI1");
        assertThat(modelo.nome()).isEqualTo("Curva PRE DI1 embutida");
        assertThat(modelo.tipo()).isEqualTo(TipoModelo.BUILTIN);
        assertThat(modelo.estado()).isEqualTo(EstadoModelo.ATIVO);
        assertThat(modelo.codigoFonte()).isNull();
        assertThat(modelo.checksum()).isNull();
        assertThat(modelo.importadoPor()).isNull();
        assertThat(modelo.importadoEm()).isNull();
    }

    @Test
    void deveImportarModeloGroovyComTodosOsCamposPreenchidos() {
        ModeloCurva modelo = ModeloCurva.importarGroovy(
                "CUSTOM_1",
                "Modelo customizado",
                "def x = 1",
                "sha256:abc",
                "operador.x"
        );

        assertThat(modelo.id()).isNotNull();
        assertThat(modelo.codigo()).isEqualTo("CUSTOM_1");
        assertThat(modelo.nome()).isEqualTo("Modelo customizado");
        assertThat(modelo.tipo()).isEqualTo(TipoModelo.GROOVY);
        assertThat(modelo.estado()).isEqualTo(EstadoModelo.ATIVO);
        assertThat(modelo.codigoFonte()).isEqualTo("def x = 1");
        assertThat(modelo.checksum()).isEqualTo("sha256:abc");
        assertThat(modelo.importadoPor()).isEqualTo("operador.x");
        assertThat(modelo.importadoEm()).isNotNull();
    }

    @Test
    void deveLancarExcecaoAoCriarBuiltinComCodigoOuNomeInvalidos() {
        assertThatThrownBy(() -> ModeloCurva.builtin("", "nome"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ModeloCurva.builtin(null, "nome"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ModeloCurva.builtin("cod", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarExcecaoAoImportarGroovyComCamposObrigatoriosInvalidos() {
        assertThatThrownBy(() -> ModeloCurva.importarGroovy("cod", "nome", "", "checksum", "autor"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ModeloCurva.importarGroovy("cod", "nome", "def x = 1", "", "autor"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ModeloCurva.importarGroovy("cod", "nome", "def x = 1", "checksum", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveDesabilitarEAtivarModelo() {
        ModeloCurva modelo = ModeloCurva.builtin("PRE_DI1", "Curva PRE DI1 embutida");
        assertThat(modelo.estado()).isEqualTo(EstadoModelo.ATIVO);

        modelo.desabilitar();
        assertThat(modelo.estado()).isEqualTo(EstadoModelo.DESABILITADO);

        modelo.ativar();
        assertThat(modelo.estado()).isEqualTo(EstadoModelo.ATIVO);
    }

    @Test
    void deveLancarExcecaoAoDesabilitarModeloJaDesabilitado() {
        ModeloCurva modelo = ModeloCurva.builtin("PRE_DI1", "Curva PRE DI1 embutida");
        modelo.desabilitar();

        assertThatThrownBy(modelo::desabilitar)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveLancarExcecaoAoAtivarModeloJaAtivo() {
        ModeloCurva modelo = ModeloCurva.builtin("PRE_DI1", "Curva PRE DI1 embutida");

        assertThatThrownBy(modelo::ativar)
                .isInstanceOf(IllegalStateException.class);
    }
}
