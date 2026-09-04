package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.AgendamentoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgendamentoControllerTest {

    @Test
    void listarComUltimaExecucaoVazio() {
        AgendamentoService service = mock(AgendamentoService.class);
        AgendamentoController controller = new AgendamentoController(service);

        when(service.buscarComUltimaExecucao()).thenReturn(List.of());

        ResponseEntity<?> response = controller.listarComUltimaExecucao();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
