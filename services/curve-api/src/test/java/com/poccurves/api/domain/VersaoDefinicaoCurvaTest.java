package com.poccurves.api.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersaoDefinicaoCurvaTest {

    private VersaoDefinicaoCurva primeiraVersaoPadrao(UUID definicaoCurvaId, LocalDate vigenciaInicio) {
        return VersaoDefinicaoCurva.primeiraVersao(
                definicaoCurvaId,
                "DU/252",
                "B3",
                "LINEAR",
                "ESTRITA",
                "TRUNCAR_6",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                vigenciaInicio
        );
    }


    @Test
    void deveCriarPrimeiraVersaoComArgumentosValidos() {
        UUID definicaoCurvaId = UUID.randomUUID();
        LocalDate vigenciaInicio = LocalDate.of(2026, 1, 1);

        VersaoDefinicaoCurva versao = primeiraVersaoPadrao(definicaoCurvaId, vigenciaInicio);

        assertThat(versao.id()).isNotNull();
        assertThat(versao.definicaoCurvaId()).isEqualTo(definicaoCurvaId);
        assertThat(versao.numeroVersao()).isEqualTo(1);
        assertThat(versao.contagemDias()).isEqualTo("DU/252");
        assertThat(versao.calendario()).isEqualTo("B3");
        assertThat(versao.interpolador()).isEqualTo("LINEAR");
        assertThat(versao.politicaExtrapolacao()).isEqualTo("ESTRITA");
        assertThat(versao.politicaArredondamento()).isEqualTo("TRUNCAR_6");
        assertThat(versao.orcamentoIngestaoSegundos()).isNull();
        assertThat(versao.orcamentoConstrucaoSegundos()).isNull();
        assertThat(versao.orcamentoValidacaoSegundos()).isNull();
        assertThat(versao.orcamentoPublicacaoSegundos()).isNull();
        assertThat(versao.janelaBloqueioMinutos()).isNull();
        assertThat(versao.vinculosFonteJson()).isNull();
        assertThat(versao.dependeDeJson()).isNull();
        assertThat(versao.limitesValidacaoJson()).isNull();
        assertThat(versao.vigenciaInicio()).isEqualTo(vigenciaInicio);
        assertThat(versao.vigenciaFim()).isNull();
    }

    @Test
    void deveLancarExcecaoQuandoCamposObrigatoriosForemEmBranco() {
        UUID definicaoCurvaId = UUID.randomUUID();
        LocalDate vigenciaInicio = LocalDate.of(2026, 1, 1);

        // contagemDias em branco
        assertThatThrownBy(() -> VersaoDefinicaoCurva.primeiraVersao(
                definicaoCurvaId, "", "B3", "LINEAR", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, vigenciaInicio
        )).isInstanceOf(IllegalArgumentException.class);

        // calendario em branco
        assertThatThrownBy(() -> VersaoDefinicaoCurva.primeiraVersao(
                definicaoCurvaId, "DU/252", "", "LINEAR", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, vigenciaInicio
        )).isInstanceOf(IllegalArgumentException.class);

        // interpolador em branco
        assertThatThrownBy(() -> VersaoDefinicaoCurva.primeiraVersao(
                definicaoCurvaId, "DU/252", "B3", "", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, vigenciaInicio
        )).isInstanceOf(IllegalArgumentException.class);

        // politicaExtrapolacao em branco
        assertThatThrownBy(() -> VersaoDefinicaoCurva.primeiraVersao(
                definicaoCurvaId, "DU/252", "B3", "LINEAR", "", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, vigenciaInicio
        )).isInstanceOf(IllegalArgumentException.class);

        // politicaArredondamento em branco
        assertThatThrownBy(() -> VersaoDefinicaoCurva.primeiraVersao(
                definicaoCurvaId, "DU/252", "B3", "LINEAR", "ESTRITA", "",
                null, null, null, null, null, null, null, null, null, vigenciaInicio
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarExcecaoQuandoDefinicaoCurvaIdForNulo() {
        LocalDate vigenciaInicio = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> VersaoDefinicaoCurva.primeiraVersao(
                null, "DU/252", "B3", "LINEAR", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, vigenciaInicio
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveLancarExcecaoQuandoVigenciaInicioForNula() {
        UUID definicaoCurvaId = UUID.randomUUID();

        assertThatThrownBy(() -> VersaoDefinicaoCurva.primeiraVersao(
                definicaoCurvaId, "DU/252", "B3", "LINEAR", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveCriarProximaVersaoEEncerrarVigenciaDaAnterior() {
        UUID definicaoCurvaId = UUID.randomUUID();
        LocalDate vigenciaInicioV1 = LocalDate.of(2026, 1, 1);
        LocalDate vigenciaInicioV2 = LocalDate.of(2026, 6, 1);

        VersaoDefinicaoCurva v1 = primeiraVersaoPadrao(definicaoCurvaId, vigenciaInicioV1);

        VersaoDefinicaoCurva v2 = VersaoDefinicaoCurva.proximaVersao(
                v1,
                "DU/252",
                "B3",
                "LOG_LINEAR",
                "ESTRITA",
                "TRUNCAR_6",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                vigenciaInicioV2
        );

        assertThat(v2.numeroVersao()).isEqualTo(2);
        assertThat(v2.definicaoCurvaId()).isEqualTo(v1.definicaoCurvaId());
        assertThat(v2.interpolador()).isEqualTo("LOG_LINEAR");
        assertThat(v2.vigenciaFim()).isNull();
        assertThat(v1.vigenciaFim()).isEqualTo(vigenciaInicioV2);
    }

    @Test
    void deveLancarExcecaoAoCriarProximaVersaoAPartirDeVersaoJaEncerrada() {
        UUID definicaoCurvaId = UUID.randomUUID();
        VersaoDefinicaoCurva v1 = primeiraVersaoPadrao(definicaoCurvaId, LocalDate.of(2026, 1, 1));

        VersaoDefinicaoCurva.proximaVersao(
                v1, "DU/252", "B3", "LOG_LINEAR", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, LocalDate.of(2026, 6, 1)
        );

        assertThatThrownBy(() -> VersaoDefinicaoCurva.proximaVersao(
                v1, "DU/252", "B3", "CUBIC_SPLINE", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, LocalDate.of(2026, 12, 1)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveLancarExcecaoQuandoVersaoAnteriorForNulaEmProximaVersao() {
        assertThatThrownBy(() -> VersaoDefinicaoCurva.proximaVersao(
                null, "DU/252", "B3", "LOG_LINEAR", "ESTRITA", "TRUNCAR_6",
                null, null, null, null, null, null, null, null, null, LocalDate.of(2026, 6, 1)
        )).isInstanceOf(NullPointerException.class);
    }


    @Test
    void deveLancarExcecaoAoEncerrarVigenciaComDataAnteriorAVigenciaInicio() {
        UUID definicaoCurvaId = UUID.randomUUID();
        VersaoDefinicaoCurva v1 = primeiraVersaoPadrao(definicaoCurvaId, LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> v1.encerrarVigencia(LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveLancarExcecaoAoEncerrarVigenciaComDataNula() {
        UUID definicaoCurvaId = UUID.randomUUID();
        VersaoDefinicaoCurva v1 = primeiraVersaoPadrao(definicaoCurvaId, LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> v1.encerrarVigencia(null))
                .isInstanceOf(NullPointerException.class);
    }
}
