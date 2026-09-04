package com.poccurves.engine.domain.versao;
import com.poccurves.engine.domain.versao.EstadoVersaoCurva;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.OrigemVersao;
import com.poccurves.engine.domain.versao.VersaoCurva;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersaoCurvaTest {

    private final UUID definicaoCurvaId = UUID.randomUUID();
    private final UUID versaoDefinicaoCurvaId = UUID.randomUUID();
    private final LocalDate dataReferencia = LocalDate.of(2023, 10, 20);
    private final MomentoCurva momentoCurva = MomentoCurva.FECHAMENTO;
    private final int numeroVersao = 1;
    private final OrigemVersao origemVersao = OrigemVersao.CALCULADA;
    private final UUID execucaoCurvaId = UUID.randomUUID();

    @Test
    void criar_ComSucesso_NasceEmValidacao() {
        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        );

        assertThat(versao.id()).isNotNull();
        assertThat(versao.definicaoCurvaId()).isEqualTo(definicaoCurvaId);
        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.EM_VALIDACAO);
        assertThat(versao.publicadoEm()).isNull();
    }

    @Test
    void criar_FalhaQuandoCamposNulos() {
        assertThatThrownBy(() -> VersaoCurva.criar(
                null, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        )).isInstanceOf(NullPointerException.class).hasMessageContaining("definicaoCurvaId");

        assertThatThrownBy(() -> VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, 0, origemVersao, execucaoCurvaId
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("numeroVersao");
    }

    @Test
    void publicar_ComSucesso_MudaEstadoEData() {
        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        );

        versao.publicar();

        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.PUBLICADA);
        assertThat(versao.publicadoEm()).isNotNull();
    }

    @Test
    void publicar_FalhaSeNaoEstiverEmValidacao() {
        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        );

        versao.publicar();

        assertThatThrownBy(versao::publicar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EM_VALIDACAO");
    }

    @Test
    void reprovar_ComSucesso() {
        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        );

        versao.reprovar();

        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.REPROVADA);
    }
    
    @Test
    void reprovar_FalhaSeNaoEstiverEmValidacao() {
        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        );
        versao.publicar();

        assertThatThrownBy(versao::reprovar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EM_VALIDACAO");
    }

    @Test
    void substituir_ComSucesso() {
        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        );
        versao.publicar();

        versao.substituir();

        assertThat(versao.estado()).isEqualTo(EstadoVersaoCurva.SUBSTITUIDA);
    }
    
    @Test
    void substituir_FalhaSeNaoEstiverPublicada() {
        VersaoCurva versao = VersaoCurva.criar(
                definicaoCurvaId, versaoDefinicaoCurvaId, dataReferencia,
                momentoCurva, numeroVersao, origemVersao, execucaoCurvaId
        );
        
        assertThatThrownBy(versao::substituir)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLICADA");
    }
}
