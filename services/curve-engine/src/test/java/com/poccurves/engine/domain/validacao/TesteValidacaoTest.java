package com.poccurves.engine.domain.validacao;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.validacao.Classificacao;
import com.poccurves.engine.domain.validacao.ContextoValidacao;
import com.poccurves.engine.domain.validacao.ResultadoTeste;
import com.poccurves.engine.domain.validacao.ResultadoValidacao;
import com.poccurves.engine.domain.validacao.TesteValidacao;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TesteValidacaoTest {

    private static class TesteFalso implements TesteValidacao {

        @Override
        public String identificador() {
            return "TESTE_FALSO";
        }

        @Override
        public ResultadoTeste executar(ContextoValidacao contexto, BigDecimal limite) {
            return new ResultadoTeste(
                    "TESTE_FALSO",
                    Classificacao.AVISO,
                    ResultadoValidacao.APROVADO,
                    null,
                    limite,
                    "curva com " + contexto.curvaConstruida().vertices().size() + " vértices"
            );
        }
    }

    @Test
    void deveExecutarTesteSobreContextoEProduzirResultado() {
        CurvaJuros curva = CurvaJuros.de(List.of(
                new Vertice(30, null, null, new BigDecimal("0.10"), null)
        ));
        ContextoValidacao contexto = new ContextoValidacao(curva, List.of(), Optional.empty(), Optional.empty());

        TesteValidacao teste = new TesteFalso();
        assertThat(teste.identificador()).isEqualTo("TESTE_FALSO");

        ResultadoTeste resultado = teste.executar(contexto, new BigDecimal("0.05"));

        assertThat(resultado.identificador()).isEqualTo("TESTE_FALSO");
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.AVISO);
        assertThat(resultado.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        assertThat(resultado.limiteAplicado()).isEqualByComparingTo("0.05");
        assertThat(resultado.detalhe()).contains("1 vértices");
    }

    @Test
    void comClassificacaoDevolveCopiaComClassificacaoSubstituida() {
        ResultadoTeste original = new ResultadoTeste(
                "TESTE_FALSO", Classificacao.AVISO, ResultadoValidacao.APROVADO, null, null, null);

        ResultadoTeste substituido = original.comClassificacao(Classificacao.BLOQUEANTE);

        assertThat(substituido.classificacao()).isEqualTo(Classificacao.BLOQUEANTE);
        assertThat(substituido.identificador()).isEqualTo(original.identificador());
        assertThat(substituido.resultado()).isEqualTo(original.resultado());
    }
}
