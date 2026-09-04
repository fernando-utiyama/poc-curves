package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersaoCurvaTest {

    private VersaoCurva criarPadrao(OrigemVersao origem) {
        return VersaoCurva.criar(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 8, 21),
                MomentoCurva.FECHAMENTO, 1, origem, UUID.randomUUID());
    }

    @Test
    void criaEmEmValidacaoSemPublicadoEm() {
        VersaoCurva versao = criarPadrao(OrigemVersao.IMPORTADA);

        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.EM_VALIDACAO);
        assertThat(versao.publicadoEm()).isNull();
        assertThat(versao.numeroVersao()).isEqualTo(1);
    }

    @Test
    void recusaOrigemCalculada() {
        assertThatThrownBy(() -> VersaoCurva.criar(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                MomentoCurva.FECHAMENTO, 1, OrigemVersao.CALCULADA, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CALCULADA");
    }

    @Test
    void recusaNumeroVersaoMenorQueUm() {
        assertThatThrownBy(() -> VersaoCurva.criar(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                MomentoCurva.FECHAMENTO, 0, OrigemVersao.IMPORTADA, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicarTransicionaParaPublicadaEMarcaInstante() {
        VersaoCurva versao = criarPadrao(OrigemVersao.CARREGADA);

        versao.publicar();

        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
        assertThat(versao.publicadoEm()).isNotNull();
    }

    @Test
    void publicarDuasVezesLancaIllegalStateException() {
        VersaoCurva versao = criarPadrao(OrigemVersao.IMPORTADA);
        versao.publicar();

        assertThatThrownBy(versao::publicar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reprovarTransicionaParaReprovada() {
        VersaoCurva versao = criarPadrao(OrigemVersao.IMPORTADA);

        versao.reprovar();

        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.REPROVADA);
    }

    @Test
    void naoConsegueReprovarUmaVersaoJaPublicada() {
        VersaoCurva versao = criarPadrao(OrigemVersao.IMPORTADA);
        versao.publicar();

        assertThatThrownBy(versao::reprovar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void substituirSoFuncionaAPartirDePublicada() {
        VersaoCurva versao = criarPadrao(OrigemVersao.IMPORTADA);

        assertThatThrownBy(versao::substituir).isInstanceOf(IllegalStateException.class);

        versao.publicar();
        versao.substituir();

        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.SUBSTITUIDA);
    }

    @Test
    void reidratarReproduzOEstadoExatoDaLinha() {
        UUID id = UUID.randomUUID();
        UUID definicaoCurvaId = UUID.randomUUID();
        UUID versaoDefinicaoCurvaId = UUID.randomUUID();
        UUID execucaoCurvaId = UUID.randomUUID();
        var publicadoEm = java.time.Instant.parse("2026-08-21T18:00:00Z");

        VersaoCurva versao = VersaoCurva.reidratar(
                id, definicaoCurvaId, versaoDefinicaoCurvaId, LocalDate.of(2026, 8, 21),
                MomentoCurva.FECHAMENTO, 2, OrigemVersao.IMPORTADA, EstadoVersaoCurva.PUBLICADA,
                execucaoCurvaId, publicadoEm);

        assertThat(versao.id()).isEqualTo(id);
        assertThat(versao.numeroVersao()).isEqualTo(2);
        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
        assertThat(versao.publicadoEm()).isEqualTo(publicadoEm);
    }
}
