package com.poccurves.api.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinicaoCurvaTest {

    private static final String CODIGO_VALIDO = "PRE-DI1";
    private static final String NOME_VALIDO = "Curva DI1 Pré";
    private static final String MOEDA_VALIDA = "BRL";
    private static final ModoOrigem MODO_ORIGEM_VALIDO = ModoOrigem.BOOTSTRAPPED;
    private static final LocalTime HORARIO_VALIDO = LocalTime.of(18, 0);
    private static final String CRIADO_POR_VALIDO = "usuario.teste";

    private DefinicaoCurva criarCurvaValida() {
        return DefinicaoCurva.criar(
                CODIGO_VALIDO,
                NOME_VALIDO,
                MOEDA_VALIDA,
                MODO_ORIGEM_VALIDO,
                HORARIO_VALIDO,
                CRIADO_POR_VALIDO
        );
    }

    @Test
    void deveCriarDefinicaoCurvaComArgumentosValidos() {
        DefinicaoCurva definicao = criarCurvaValida();

        assertThat(definicao.id()).isNotNull();
        assertThat(definicao.codigo()).isEqualTo(CODIGO_VALIDO);
        assertThat(definicao.nome()).isEqualTo(NOME_VALIDO);
        assertThat(definicao.moeda()).isEqualTo(MOEDA_VALIDA);
        assertThat(definicao.modoOrigem()).isEqualTo(MODO_ORIGEM_VALIDO);
        assertThat(definicao.horarioLimitePublicacao()).isEqualTo(HORARIO_VALIDO);
        assertThat(definicao.estado()).isEqualTo(EstadoDefinicaoCurva.RASCUNHO);
        assertThat(definicao.criadoEm()).isNotNull();
        assertThat(definicao.criadoPor()).isEqualTo(CRIADO_POR_VALIDO);
    }

    @Test
    void deveLancarExcecaoQuandoCodigoForNuloOuEmBranco() {
        assertThatThrownBy(() -> DefinicaoCurva.criar(
                null,
                NOME_VALIDO,
                MOEDA_VALIDA,
                MODO_ORIGEM_VALIDO,
                HORARIO_VALIDO,
                CRIADO_POR_VALIDO
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DefinicaoCurva.criar(
                "",
                NOME_VALIDO,
                MOEDA_VALIDA,
                MODO_ORIGEM_VALIDO,
                HORARIO_VALIDO,
                CRIADO_POR_VALIDO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarExcecaoQuandoNomeForEmBranco() {
        assertThatThrownBy(() -> DefinicaoCurva.criar(
                CODIGO_VALIDO,
                "",
                MOEDA_VALIDA,
                MODO_ORIGEM_VALIDO,
                HORARIO_VALIDO,
                CRIADO_POR_VALIDO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarExcecaoQuandoMoedaTiverTamanhoDiferenteDeTres() {
        assertThatThrownBy(() -> DefinicaoCurva.criar(
                CODIGO_VALIDO,
                NOME_VALIDO,
                "BR",
                MODO_ORIGEM_VALIDO,
                HORARIO_VALIDO,
                CRIADO_POR_VALIDO
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DefinicaoCurva.criar(
                CODIGO_VALIDO,
                NOME_VALIDO,
                "BRLL",
                MODO_ORIGEM_VALIDO,
                HORARIO_VALIDO,
                CRIADO_POR_VALIDO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarExcecaoQuandoCriadoPorForEmBranco() {
        assertThatThrownBy(() -> DefinicaoCurva.criar(
                CODIGO_VALIDO,
                NOME_VALIDO,
                MOEDA_VALIDA,
                MODO_ORIGEM_VALIDO,
                HORARIO_VALIDO,
                ""
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarExcecaoQuandoModoOrigemForNulo() {
        assertThatThrownBy(() -> DefinicaoCurva.criar(
                CODIGO_VALIDO,
                NOME_VALIDO,
                MOEDA_VALIDA,
                null,
                HORARIO_VALIDO,
                CRIADO_POR_VALIDO
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveRenomearDefinicaoCurva() {
        DefinicaoCurva definicao = criarCurvaValida();

        definicao.renomear("Novo Nome");

        assertThat(definicao.nome()).isEqualTo("Novo Nome");
    }

    @Test
    void deveLancarExcecaoAoRenomearComNomeNuloOuEmBranco() {
        DefinicaoCurva definicao = criarCurvaValida();

        assertThatThrownBy(() -> definicao.renomear(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> definicao.renomear(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveAtivarEAposentarDefinicaoCurva() {
        DefinicaoCurva definicao = criarCurvaValida();

        definicao.ativar();
        assertThat(definicao.estado()).isEqualTo(EstadoDefinicaoCurva.ATIVA);

        definicao.aposentar();
        assertThat(definicao.estado()).isEqualTo(EstadoDefinicaoCurva.APOSENTADA);
    }

    @Test
    void deveLancarExcecaoAoAtivarDuasVezesSeguidas() {
        DefinicaoCurva definicao = criarCurvaValida();
        definicao.ativar();

        assertThatThrownBy(definicao::ativar)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveLancarExcecaoAoAposentarDiretoAPartirDeRascunho() {
        DefinicaoCurva definicao = criarCurvaValida();

        assertThatThrownBy(definicao::aposentar)
                .isInstanceOf(IllegalStateException.class);
    }
}
