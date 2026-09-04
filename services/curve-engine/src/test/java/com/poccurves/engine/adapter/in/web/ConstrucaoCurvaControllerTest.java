package com.poccurves.engine.adapter.in.web;

import com.poccurves.engine.application.PublicacaoCurvaService;
import com.poccurves.engine.dto.EngineDtos.ConstrucaoCurvaRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ConstrucaoCurvaControllerTest {

    @Mock private PublicacaoCurvaService publicacaoCurvaService;

    private final Executor executorSincrono = Runnable::run;

    @Test
    void requestValidaRetorna202ESubmeteAoExecutor() {
        ConstrucaoCurvaController controller = new ConstrucaoCurvaController(publicacaoCurvaService, executorSincrono);

        ConstrucaoCurvaRequest request = new ConstrucaoCurvaRequest(
                "PRE_DI", LocalDate.of(2026, 10, 10), "FECHAMENTO", UUID.randomUUID(), UUID.randomUUID());

        ResponseEntity<Void> resposta = controller.construir(request);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(publicacaoCurvaService).processarPedidoConstrucao(
                request.curveCode(), request.referenceDate(), request.curveMoment(), request.runId(), request.executionId());
    }

    @Test
    void requestComCurveCodeNuloRetorna400ENaoSubmeteAoExecutor() {
        ConstrucaoCurvaController controller = new ConstrucaoCurvaController(publicacaoCurvaService, executorSincrono);

        ConstrucaoCurvaRequest request = new ConstrucaoCurvaRequest(
                null, LocalDate.of(2026, 10, 10), "FECHAMENTO", UUID.randomUUID(), UUID.randomUUID());

        ResponseEntity<Void> resposta = controller.construir(request);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(publicacaoCurvaService);
    }

    @Test
    void requestComExecutionIdNuloRetorna400() {
        ConstrucaoCurvaController controller = new ConstrucaoCurvaController(publicacaoCurvaService, executorSincrono);

        ConstrucaoCurvaRequest request = new ConstrucaoCurvaRequest(
                "PRE_DI", LocalDate.of(2026, 10, 10), "FECHAMENTO", UUID.randomUUID(), null);

        ResponseEntity<Void> resposta = controller.construir(request);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(publicacaoCurvaService, never()).processarPedidoConstrucao(any(), any(), any(), any(), any());
    }
}
