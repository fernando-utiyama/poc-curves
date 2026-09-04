package com.poccurves.engine.application.service;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.ContextoValidacao;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.LimiteValidacao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;
import com.poccurves.engine.application.model.Vertice;
import com.poccurves.engine.application.port.ValidacaoCurvaRepositoryPort;
import com.poccurves.engine.application.validator.TesteValidacao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BateriaValidacaoServiceTest {

    private ValidacaoCurvaRepositoryPort validacaoCurvaRepository;
    private TesteValidacao testeMock;
    private BateriaValidacaoService service;
    private ContextoValidacao contexto;

    @BeforeEach
    void setup() {
        validacaoCurvaRepository = mock(ValidacaoCurvaRepositoryPort.class);
        testeMock = mock(TesteValidacao.class);
        when(testeMock.identificador()).thenReturn("TESTE_MOCK");

        service = new BateriaValidacaoService(List.of(testeMock), validacaoCurvaRepository);
        // CurvaJuros.de exige ao menos 1 vértice por design -- o conteúdo real não importa aqui,
        // TesteValidacao é mockado em todos os testes desta classe.
        CurvaJuros curvaPlaceholder = CurvaJuros.de(List.of(new Vertice(21, null, null, new BigDecimal("0.10"), null)));
        contexto = new ContextoValidacao(curvaPlaceholder, List.of(), Optional.empty(), Optional.empty());
    }

    @Test
    void testeDesconhecidoLancaExcecao() {
        var limites = List.of(new LimiteValidacao("DESCONHECIDO", Classificacao.BLOQUEANTE, BigDecimal.ONE));

        assertThatThrownBy(() -> service.executar(UUID.randomUUID(), limites, contexto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("desconhecido")
                .hasMessageContaining("DESCONHECIDO");
    }

    @Test
    void testeQueLancaExcecaoNaoPropagaEGeraReprovacao() {
        var limites = List.of(new LimiteValidacao("TESTE_MOCK", Classificacao.AVISO, BigDecimal.ONE));
        when(testeMock.executar(any(), any())).thenThrow(new RuntimeException("Erro interno forçado"));

        var veredito = service.executar(UUID.randomUUID(), limites, contexto);

        assertThat(veredito.resultados()).hasSize(1);
        var resultado = veredito.resultados().get(0);
        assertThat(resultado.resultado()).isEqualTo(ResultadoValidacao.REPROVADO);
        assertThat(resultado.classificacao()).isEqualTo(Classificacao.AVISO);
        assertThat(resultado.detalhe()).contains("Erro interno forçado");
        assertThat(resultado.medidaObservada()).isNull();
    }

    @Test
    void classificacaoSobrescritaComAConfiguradaNoLimite() {
        var limites = List.of(new LimiteValidacao("TESTE_MOCK", Classificacao.BLOQUEANTE, BigDecimal.ONE));

        // Teste tenta devolver AVISO
        when(testeMock.executar(any(), any())).thenReturn(
                new ResultadoTeste("TESTE_MOCK", Classificacao.AVISO, ResultadoValidacao.APROVADO, BigDecimal.ZERO, BigDecimal.ONE, "ok")
        );

        var veredito = service.executar(UUID.randomUUID(), limites, contexto);

        assertThat(veredito.resultados().get(0).classificacao()).isEqualTo(Classificacao.BLOQUEANTE); // Veio de LimiteValidacao
    }

    @Test
    void aprovadaSemBloqueioReprovado() {
        UUID versaoId = UUID.randomUUID();

        // Cenário 1: Aviso reprovado -> Não bloqueia
        var limitesAviso = List.of(new LimiteValidacao("TESTE_MOCK", Classificacao.AVISO, BigDecimal.ONE));
        when(testeMock.executar(any(), any())).thenReturn(
                new ResultadoTeste("TESTE_MOCK", Classificacao.AVISO, ResultadoValidacao.REPROVADO, BigDecimal.TEN, BigDecimal.ONE, "falhou")
        );
        var veredito1 = service.executar(versaoId, limitesAviso, contexto);
        assertThat(veredito1.aprovadaSemBloqueioReprovado()).isTrue();

        // Cenário 2: Bloqueante reprovado -> Bloqueia
        var limitesBloq = List.of(new LimiteValidacao("TESTE_MOCK", Classificacao.BLOQUEANTE, BigDecimal.ONE));
        var veredito2 = service.executar(versaoId, limitesBloq, contexto);
        assertThat(veredito2.aprovadaSemBloqueioReprovado()).isFalse();

        // Cenário 3: Bloqueante aprovado -> Não bloqueia
        when(testeMock.executar(any(), any())).thenReturn(
                new ResultadoTeste("TESTE_MOCK", Classificacao.AVISO, ResultadoValidacao.APROVADO, BigDecimal.ZERO, BigDecimal.ONE, "ok")
        );
        var veredito3 = service.executar(versaoId, limitesBloq, contexto);
        assertThat(veredito3.aprovadaSemBloqueioReprovado()).isTrue();
    }

    @Test
    void semLimitesRetornaAprovadoNaoPersiste() {
        var veredito = service.executar(UUID.randomUUID(), List.of(), contexto);

        assertThat(veredito.aprovadaSemBloqueioReprovado()).isTrue();
        assertThat(veredito.resultados()).isEmpty();
        verify(validacaoCurvaRepository, never()).inserirTodos(any(), any());
    }
}
