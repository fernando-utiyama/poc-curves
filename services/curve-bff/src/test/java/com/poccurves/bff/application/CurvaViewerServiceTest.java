package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CurvaViewerServiceTest {

    private CurveApiPort curveApiClient;
    private CurveOrchestratorPort orchestratorClient;
    private CurvaViewerService service;

    @BeforeEach
    void setUp() {
        curveApiClient = Mockito.mock(CurveApiPort.class);
        orchestratorClient = Mockito.mock(CurveOrchestratorPort.class);
        service = new CurvaViewerService(curveApiClient, orchestratorClient);
    }

    @Test
    void deveAgregarTelaDeCurvaCompletaComSucesso() {
        LocalDate dataRef = LocalDate.of(2026, 8, 21);
        UUID versaoId = UUID.randomUUID();

        CurvaViewerResponse baseCurva = new CurvaViewerResponse(
                versaoId, "PRE", "Curva DI1 Pré", "BOOTSTRAPPED", dataRef, "FECHAMENTO",
                1, "PUBLICADA", "CALCULADA", true, "VERSAO_CORRENTE", Instant.now(),
                List.of(new VerticeCurvaDTO(21, 31, LocalDate.of(2026, 9, 21), new BigDecimal("14.129000000000"), new BigDecimal("0.988000000000"))),
                null, null, null, null, null, null, null
        );

        ExecucaoResumoDTO exec = new ExecucaoResumoDTO(
                UUID.randomUUID(), "corr-123", "CONCLUIDA", "CONCLUIDA", 12, null
        );

        when(curveApiClient.getCurvaPublicada("PRE", dataRef, "FECHAMENTO", null, null))
                .thenReturn(Optional.of(baseCurva));
        when(orchestratorClient.getUltimaExecucaoCurva("PRE", dataRef, "FECHAMENTO"))
                .thenReturn(Optional.of(exec));

        CurvaViewerResponse response = service.obterCurvaViewer("PRE", dataRef, "FECHAMENTO", null, null);

        assertThat(response.codigoCurva()).isEqualTo("PRE");
        assertThat(response.numeroVersao()).isEqualTo(1);
        assertThat(response.vertices()).hasSize(1);
        assertThat(response.ultimaExecucao()).isNotNull();
        assertThat(response.ultimaExecucao().correlationId()).isEqualTo("corr-123");
        assertThat(response.secaoVertices().disponivel()).isTrue();
    }

    @Test
    void deveDegradarParcialmenteSeOrquestradorFalhar() {
        LocalDate dataRef = LocalDate.of(2026, 8, 21);
        UUID versaoId = UUID.randomUUID();

        CurvaViewerResponse baseCurva = new CurvaViewerResponse(
                versaoId, "PRE", "Curva DI1 Pré", "BOOTSTRAPPED", dataRef, "FECHAMENTO",
                1, "PUBLICADA", "CALCULADA", true, "VERSAO_CORRENTE", Instant.now(),
                List.of(new VerticeCurvaDTO(21, 31, LocalDate.of(2026, 9, 21), new BigDecimal("14.129000000000"), new BigDecimal("0.988000000000"))),
                null, null, null, null, null, null, null
        );

        when(curveApiClient.getCurvaPublicada("PRE", dataRef, "FECHAMENTO", null, null))
                .thenReturn(Optional.of(baseCurva));
        when(orchestratorClient.getUltimaExecucaoCurva("PRE", dataRef, "FECHAMENTO"))
                .thenThrow(new RuntimeException("Conexão recusada ao orquestrador"));

        CurvaViewerResponse response = service.obterCurvaViewer("PRE", dataRef, "FECHAMENTO", null, null);

        // A resposta continua completa e válida
        assertThat(response.codigoCurva()).isEqualTo("PRE");
        assertThat(response.vertices()).hasSize(1);
        // E a seção de execução é explicitamente marcada como degradada/indisponível
        assertThat(response.ultimaExecucao()).isNull();
        assertThat(response.secaoExecucao().disponivel()).isFalse();
        assertThat(response.secaoExecucao().motivo()).contains("Falha ao obter execução");
    }
}
