package com.poccurves.orchestrator.adapter.in.bootstrap;
import com.poccurves.orchestrator.application.usecase.ReconciliacaoAgendamentosService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgendamentoBootstrapTest {

    @Test
    void chamaAReconciliacaoDeAgendamentosNaInicializacao() {
        ReconciliacaoAgendamentosService reconciliacaoAgendamentosService = mock(ReconciliacaoAgendamentosService.class);
        AgendamentoBootstrap bootstrap = new AgendamentoBootstrap(reconciliacaoAgendamentosService);

        bootstrap.run(mock(ApplicationArguments.class));

        verify(reconciliacaoAgendamentosService).reconciliar();
    }
}
