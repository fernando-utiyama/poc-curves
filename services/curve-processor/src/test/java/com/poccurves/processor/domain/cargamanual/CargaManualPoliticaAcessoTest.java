package com.poccurves.processor.domain.cargamanual;
import com.poccurves.processor.domain.parsing.ArquivoCargaExcedeTamanhoException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CargaManualPoliticaAcessoTest {

    @Test
    void aceitaArquivoDentroDoLimiteEPerfilOperador() {
        assertThatCode(() -> CargaManualPoliticaAcesso.validar(1024, "OPERADOR")).doesNotThrowAnyException();
    }

    @Test
    void aceitaPerfilAdministrador() {
        assertThatCode(() -> CargaManualPoliticaAcesso.validar(1024, "ADMINISTRADOR")).doesNotThrowAnyException();
    }

    @Test
    void recusaArquivoAcimaDoLimite() {
        assertThatThrownBy(() -> CargaManualPoliticaAcesso.validar(CargaManualPoliticaAcesso.TAMANHO_MAXIMO_BYTES + 1, "OPERADOR"))
                .isInstanceOf(ArquivoCargaExcedeTamanhoException.class);
    }

    @Test
    void recusaPerfilNaoAutorizado() {
        assertThatThrownBy(() -> CargaManualPoliticaAcesso.validar(1024, "LEITOR"))
                .isInstanceOf(PerfilNaoAutorizadoParaCargaException.class);
    }

    @Test
    void recusaPerfilNulo() {
        assertThatThrownBy(() -> CargaManualPoliticaAcesso.validar(1024, null))
                .isInstanceOf(PerfilNaoAutorizadoParaCargaException.class);
    }
}
