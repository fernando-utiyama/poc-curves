package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

        CurvaDadosDTO vertices = new CurvaDadosDTO("B3_TAXA_SWAP_DCL", dataRef,
                List.of(new PontoCurvaDTO(LocalDate.of(2026, 9, 21), new BigDecimal("14.129000000000"))));
        CurvaDadosDTO curva = new CurvaDadosDTO("B3_TAXA_SWAP_DCL", dataRef,
                List.of(new PontoCurvaDTO(LocalDate.of(2026, 9, 21), new BigDecimal("14.129000000000"))));

        ExecucaoResumoDTO exec = new ExecucaoResumoDTO(
                UUID.randomUUID(), "corr-123", "CONCLUIDA", "CONCLUIDA", 12, null
        );

        when(curveApiClient.getVertices("B3_TAXA_SWAP_DCL", dataRef)).thenReturn(Optional.of(vertices));
        when(curveApiClient.getCurvaConstruida("B3_TAXA_SWAP_DCL", dataRef)).thenReturn(Optional.of(curva));
        when(orchestratorClient.getUltimaExecucaoCurva("B3_TAXA_SWAP_DCL", dataRef, "FECHAMENTO"))
                .thenReturn(Optional.of(exec));

        CurvaViewerResponse response = service.obterCurvaViewer("B3_TAXA_SWAP_DCL", dataRef, "FECHAMENTO");

        assertThat(response.ticker()).isEqualTo("B3_TAXA_SWAP_DCL");
        assertThat(response.vertices()).hasSize(1);
        assertThat(response.curva()).hasSize(1);
        assertThat(response.ultimaExecucao()).isNotNull();
        assertThat(response.ultimaExecucao().correlationId()).isEqualTo("corr-123");
        assertThat(response.secaoVertices().disponivel()).isTrue();
        assertThat(response.secaoCurva().disponivel()).isTrue();
    }

    @Test
    void deveDegradarParcialmenteSeOrquestradorFalhar() {
        LocalDate dataRef = LocalDate.of(2026, 8, 21);

        CurvaDadosDTO vertices = new CurvaDadosDTO("B3_TAXA_SWAP_DCL", dataRef,
                List.of(new PontoCurvaDTO(LocalDate.of(2026, 9, 21), new BigDecimal("14.129000000000"))));
        CurvaDadosDTO curva = new CurvaDadosDTO("B3_TAXA_SWAP_DCL", dataRef,
                List.of(new PontoCurvaDTO(LocalDate.of(2026, 9, 21), new BigDecimal("14.129000000000"))));

        when(curveApiClient.getVertices("B3_TAXA_SWAP_DCL", dataRef)).thenReturn(Optional.of(vertices));
        when(curveApiClient.getCurvaConstruida("B3_TAXA_SWAP_DCL", dataRef)).thenReturn(Optional.of(curva));
        when(orchestratorClient.getUltimaExecucaoCurva("B3_TAXA_SWAP_DCL", dataRef, "FECHAMENTO"))
                .thenThrow(new RuntimeException("Conexão recusada ao orquestrador"));

        CurvaViewerResponse response = service.obterCurvaViewer("B3_TAXA_SWAP_DCL", dataRef, "FECHAMENTO");

        // A resposta continua completa e válida
        assertThat(response.ticker()).isEqualTo("B3_TAXA_SWAP_DCL");
        assertThat(response.vertices()).hasSize(1);
        assertThat(response.curva()).hasSize(1);
        // E a seção de execução é explicitamente marcada como degradada/indisponível
        assertThat(response.ultimaExecucao()).isNull();
        assertThat(response.secaoExecucao().disponivel()).isFalse();
        assertThat(response.secaoExecucao().motivo()).contains("Falha ao obter execução");
    }
}
