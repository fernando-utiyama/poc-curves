package com.poccurves.engine.application.usecase;
import com.poccurves.engine.application.exception.ModeloConstrucaoException;
import com.poccurves.engine.application.model.CurvaJuros;
import com.poccurves.engine.application.model.Vertice;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;

import com.poccurves.engine.dto.EngineDtos.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsoleDesenvolvimentoModeloServiceTest {

    private final ModeloConstrucaoPort groovyModeloConstrucao = mock(ModeloConstrucaoPort.class);
    private final ConsoleDesenvolvimentoModeloService service = new ConsoleDesenvolvimentoModeloService(groovyModeloConstrucao);

    @Test
    void rejeitaScriptVazioSemChamarOMotor() {
        TestarScriptGroovyResponse resposta = service.testar(new TestarScriptGroovyRequest("  ", List.of()));

        assertThat(resposta.status()).isEqualTo("ERRO_COMPILACAO");
    }

    @Test
    void devolveOsVerticesQuandoOScriptRodaComSucesso() {
        CurvaJuros curva = CurvaJuros.de(List.of(new Vertice(21, null, null, new BigDecimal("13.50"), null)));
        when(groovyModeloConstrucao.construir(any(), any())).thenReturn(curva);

        var insumo = new TestarScriptGroovyRequest.InsumoAmostraDTO("DI1F26", new BigDecimal("13.50"), 21, LocalDate.of(2026, 1, 2));
        TestarScriptGroovyResponse resposta = service.testar(new TestarScriptGroovyRequest("[[prazoDiasUteis: 21, taxa: 13.50]]", List.of(insumo)));

        assertThat(resposta.status()).isEqualTo("OK");
        assertThat(resposta.vertices()).hasSize(1);
    }

    @Test
    void devolveErroDeExecucaoSemPropagarAExcecao() {
        when(groovyModeloConstrucao.construir(any(), any()))
                .thenThrow(new ModeloConstrucaoException(ModeloConstrucaoException.Fase.EXECUCAO, "estourou", null));

        TestarScriptGroovyResponse resposta = service.testar(new TestarScriptGroovyRequest("throw new RuntimeException()", List.of()));

        assertThat(resposta.status()).isEqualTo("ERRO_EXECUCAO");
        assertThat(resposta.vertices()).isEmpty();
    }
}
