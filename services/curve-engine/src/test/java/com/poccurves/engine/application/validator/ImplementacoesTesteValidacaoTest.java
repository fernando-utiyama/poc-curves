package com.poccurves.engine.application.validator;
import com.poccurves.engine.application.model.ContextoValidacao;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.InsumoDI1;
import com.poccurves.engine.application.model.ResultadoValidacao;
import com.poccurves.engine.application.model.Vertice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImplementacoesTesteValidacaoTest {

    private ContextoValidacao contextoBase(List<Vertice> vertices) {
        return new ContextoValidacao(
                CurvaJuros.de(vertices),
                List.of(),
                Optional.empty(),
                Optional.empty()
        );
    }

    private Vertice v(int prazo, String taxa) {
        return new Vertice(prazo, prazo, LocalDate.now().plusDays(prazo), new BigDecimal(taxa), null);
    }

    @Nested
    class Estrutural {
        private final TesteEstrutural teste = new TesteEstrutural();

        @Test
        void aprovado() {
            var ctx = contextoBase(List.of(v(1, "0.10"), v(2, "0.11")));
            var res = teste.executar(ctx, new BigDecimal("2"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void reprovadoPorFaltaDeCobertura() {
            var ctx = contextoBase(List.of(v(1, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("2"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
            assertThat(res.detalhe()).contains("menor que a exigida");
        }
    }

    @Nested
    class Monotonicidade {
        private final TesteMonotonicidadeFatorDesconto teste = new TesteMonotonicidadeFatorDesconto();

        @Test
        void aprovadoDecrescente() {
            var ctx = contextoBase(List.of(v(21, "0.10"), v(42, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.0"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void reprovadoAumento() {
            var ctx = contextoBase(List.of(v(21, "0.10"), v(42, "-0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.0"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        }

        @Test
        void naoAplicavel() {
            var ctx = contextoBase(List.of(v(21, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.0"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        }
    }

    @Nested
    class LimiteTaxaForward {
        private final TesteLimiteTaxaForward teste = new TesteLimiteTaxaForward();

        @Test
        void aprovado() {
            var ctx = contextoBase(List.of(v(21, "0.10"), v(42, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.15"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void reprovado() {
            var ctx = contextoBase(List.of(v(21, "0.10"), v(42, "0.20")));
            var res = teste.executar(ctx, new BigDecimal("0.15"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        }

        @Test
        void naoAplicavel() {
            var ctx = contextoBase(List.of(v(21, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.15"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        }
    }

    @Nested
    class FaixaPlausivel {
        private final TesteFaixaPlausivelTaxa teste = new TesteFaixaPlausivelTaxa();

        @Test
        void aprovado() {
            var ctx = contextoBase(List.of(v(21, "0.05")));
            var res = teste.executar(ctx, new BigDecimal("0.10"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void reprovadoNegativo() {
            var ctx = contextoBase(List.of(v(21, "-0.01")));
            var res = teste.executar(ctx, new BigDecimal("0.10"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        }

        @Test
        void reprovadoAcimaLimite() {
            var ctx = contextoBase(List.of(v(21, "0.15")));
            var res = teste.executar(ctx, new BigDecimal("0.10"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        }
    }

    @Nested
    class Suavidade {
        private final TesteSuavidadeEstruturaTermo teste = new TesteSuavidadeEstruturaTermo();

        @Test
        void aprovado() {
            var ctx = contextoBase(List.of(v(21, "0.10"), v(42, "0.11"), v(63, "0.12")));
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void reprovado() {
            var ctx = contextoBase(List.of(v(21, "0.10"), v(42, "0.15"), v(63, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.001"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        }

        @Test
        void naoAplicavel() {
            var ctx = contextoBase(List.of(v(21, "0.10"), v(42, "0.11")));
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        }
    }

    @Nested
    class Reprecificacao {
        private final TesteReprecificacaoInstrumentosCalibracao teste = new TesteReprecificacaoInstrumentosCalibracao();

        @Test
        void aprovado() {
            var insumo = new InsumoDI1("DI1F23", new BigDecimal("0.10"), 21, LocalDate.now().plusDays(21));
            var ctx = new ContextoValidacao(
                    CurvaJuros.de(List.of(v(21, "0.10"))),
                    List.of(insumo),
                    Optional.empty(),
                    Optional.empty()
            );
            var res = teste.executar(ctx, new BigDecimal("0.001"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void reprovado() {
            var insumo = new InsumoDI1("DI1F23", new BigDecimal("0.10"), 21, LocalDate.now().plusDays(21));
            var ctx = new ContextoValidacao(
                    CurvaJuros.de(List.of(v(21, "0.12"))),
                    List.of(insumo),
                    Optional.empty(),
                    Optional.empty()
            );
            var res = teste.executar(ctx, new BigDecimal("0.001"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        }

        @Test
        void naoAplicavel() {
            var ctx = contextoBase(List.of(v(21, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.001"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        }
    }

    @Nested
    class VariacaoCurvaAnterior {
        private final TesteVariacaoCurvaAnterior teste = new TesteVariacaoCurvaAnterior();

        @Test
        void aprovado() {
            var curva = CurvaJuros.de(List.of(v(21, "0.10")));
            var curvaAnt = CurvaJuros.de(List.of(v(21, "0.095")));
            var ctx = new ContextoValidacao(curva, List.of(), Optional.of(curvaAnt), Optional.empty());
            
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void reprovado() {
            var curva = CurvaJuros.de(List.of(v(21, "0.15")));
            var curvaAnt = CurvaJuros.de(List.of(v(21, "0.095")));
            var ctx = new ContextoValidacao(curva, List.of(), Optional.of(curvaAnt), Optional.empty());
            
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        }

        @Test
        void naoAplicavelSemCurva() {
            var ctx = contextoBase(List.of(v(21, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        }

        @Test
        void naoAplicavelPrazosDisjuntos() {
            var curva = CurvaJuros.de(List.of(v(21, "0.10")));
            var curvaAnt = CurvaJuros.de(List.of(v(42, "0.095")));
            var ctx = new ContextoValidacao(curva, List.of(), Optional.of(curvaAnt), Optional.empty());
            
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        }
    }

    @Nested
    class ComparacaoCurvaImportada {
        private final TesteComparacaoCurvaImportada teste = new TesteComparacaoCurvaImportada();

        @Test
        void aprovado() {
            var curva = CurvaJuros.de(List.of(v(21, "0.10")));
            var curvaImp = CurvaJuros.de(List.of(v(21, "0.105")));
            var ctx = new ContextoValidacao(curva, List.of(), Optional.empty(), Optional.of(curvaImp));
            
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.APROVADO);
        }

        @Test
        void naoAplicavelSemCurva() {
            var ctx = contextoBase(List.of(v(21, "0.10")));
            var res = teste.executar(ctx, new BigDecimal("0.01"));
            assertThat(res.resultado()).isEqualTo(ResultadoValidacao.NAO_APLICAVEL);
        }
    }
}
