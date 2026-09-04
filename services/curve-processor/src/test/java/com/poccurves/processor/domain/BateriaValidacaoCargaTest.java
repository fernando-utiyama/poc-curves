package com.poccurves.processor.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BateriaValidacaoCargaTest {

    private final BateriaValidacaoCarga bateria = new BateriaValidacaoCarga();

    @Test
    void aprovadaQuandoNenhumTesteBloqueanteReprova() {
        List<VerticeCurva> vertices = List.of(new VerticeCurva(1, null, null, new BigDecimal("14.129"), null));

        List<ResultadoTesteCarga> resultados = bateria.executar(vertices);

        assertThat(bateria.aprovada(resultados)).isTrue();
        assertThat(resultados).extracting(ResultadoTesteCarga::identificador)
                .contains("TAXAS_NAO_NEGATIVAS", "ADERENCIA_CURVA_REFERENCIA");
    }

    @Test
    void reprovadaQuandoTesteBloqueanteReprova() {
        List<VerticeCurva> vertices = List.of(new VerticeCurva(1, null, null, new BigDecimal("-1"), null));

        List<ResultadoTesteCarga> resultados = bateria.executar(vertices);

        assertThat(bateria.aprovada(resultados)).isFalse();
    }

    @Test
    void testeNaoAplicavelNuncaBloqueiaAprovacao() {
        List<VerticeCurva> vertices = List.of(new VerticeCurva(1, null, null, new BigDecimal("14.129"), null));

        List<ResultadoTesteCarga> resultados = bateria.executar(vertices);
        ResultadoTesteCarga aderencia = resultados.stream()
                .filter(r -> r.identificador().equals("ADERENCIA_CURVA_REFERENCIA"))
                .findFirst().orElseThrow();

        assertThat(aderencia.resultado()).isEqualTo(ResultadoValidacaoCarga.NAO_APLICAVEL);
        assertThat(bateria.aprovada(resultados)).isTrue();
    }
}
