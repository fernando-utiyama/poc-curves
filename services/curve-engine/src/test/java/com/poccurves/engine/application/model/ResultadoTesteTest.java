package com.poccurves.engine.application.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultadoTesteTest {

    @Test
    void deveConstruirComTodosOsCamposEAcessoresRetornamValoresCorretos() {
        BigDecimal medida = new BigDecimal("1.5");
        BigDecimal limite = new BigDecimal("2.0");
        ResultadoTeste resultado = new ResultadoTeste(
                "MEU_TESTE",
                Classificacao.BLOQUEANTE,
                ResultadoValidacao.APROVADO,
                medida,
                limite,
                "detalhe qualquer"
        );

        assertThat(resultado.identificador()).isEqualTo("MEU_TESTE");
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.BLOQUEANTE);
        assertThat(resultado.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        assertThat(resultado.medidaObservada()).isEqualTo(medida);
        assertThat(resultado.limiteAplicado()).isEqualTo(limite);
        assertThat(resultado.detalhe()).isEqualTo("detalhe qualquer");
    }

    @Test
    void deveConstruirComCamposOpcionaisNulos() {
        ResultadoTeste resultado = new ResultadoTeste(
                "MEU_TESTE",
                Classificacao.BLOQUEANTE,
                ResultadoValidacao.APROVADO,
                null,
                null,
                null
        );

        assertThat(resultado.identificador()).isEqualTo("MEU_TESTE");
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.BLOQUEANTE);
        assertThat(resultado.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        assertThat(resultado.medidaObservada()).isNull();
        assertThat(resultado.limiteAplicado()).isNull();
        assertThat(resultado.detalhe()).isNull();
    }

    @Test
    void deveLancarExcecaoQuandoIdentificadorNulo() {
        assertThatThrownBy(() -> new ResultadoTeste(
                null,
                Classificacao.BLOQUEANTE,
                ResultadoValidacao.APROVADO,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("identificador");
    }

    @Test
    void deveLancarExcecaoQuandoIdentificadorEmBranco() {
        assertThatThrownBy(() -> new ResultadoTeste(
                "",
                Classificacao.BLOQUEANTE,
                ResultadoValidacao.APROVADO,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("identificador");

        assertThatThrownBy(() -> new ResultadoTeste(
                "   ",
                Classificacao.BLOQUEANTE,
                ResultadoValidacao.APROVADO,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("identificador");
    }

    @Test
    void deveLancarExcecaoQuandoClassificacaoNula() {
        assertThatThrownBy(() -> new ResultadoTeste(
                "MEU_TESTE",
                null,
                ResultadoValidacao.APROVADO,
                null,
                null,
                null
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("classificacao");
    }

    @Test
    void deveLancarExcecaoQuandoResultadoNulo() {
        assertThatThrownBy(() -> new ResultadoTeste(
                "MEU_TESTE",
                Classificacao.BLOQUEANTE,
                null,
                null,
                null,
                null
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("resultado");
    }
}
