package com.poccurves.processor.application.validator;
import com.poccurves.processor.application.model.Classificacao;
import com.poccurves.processor.application.model.ResultadoTesteCarga;
import com.poccurves.processor.application.model.ResultadoValidacaoCarga;
import com.poccurves.processor.application.model.VerticeCurva;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TesteTaxasNaoNegativasTest {

    private final TesteTaxasNaoNegativas teste = new TesteTaxasNaoNegativas();

    @Test
    void aprovaQuandoTodasAsTaxasSaoNaoNegativas() {
        List<VerticeCurva> vertices = List.of(
                new VerticeCurva(1, null, null, new BigDecimal("14.129"), null),
                new VerticeCurva(21, null, null, BigDecimal.ZERO, null));

        ResultadoTesteCarga resultado = teste.executar(vertices);

        assertThat(resultado.resultado()).isEqualTo(ResultadoValidacaoCarga.APROVADO);
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.BLOQUEANTE);
    }

    @Test
    void reprovaQuandoQualquerTaxaEhNegativa() {
        List<VerticeCurva> vertices = List.of(
                new VerticeCurva(1, null, null, new BigDecimal("14.129"), null),
                new VerticeCurva(21, null, null, new BigDecimal("-0.5"), null));

        ResultadoTesteCarga resultado = teste.executar(vertices);

        assertThat(resultado.resultado()).isEqualTo(ResultadoValidacaoCarga.REPROVADO);
        assertThat(resultado.detalhe()).contains("21");
    }
}
