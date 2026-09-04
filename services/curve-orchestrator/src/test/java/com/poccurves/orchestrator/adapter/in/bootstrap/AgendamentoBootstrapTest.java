package com.poccurves.orchestrator.adapter.in.bootstrap;

import com.poccurves.orchestrator.application.AgendamentoRepositoryPort;
import com.poccurves.orchestrator.application.AgendamentoSchedulerPort;
import com.poccurves.orchestrator.domain.Agendamento;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.MomentoCurva;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.mockito.Mockito.*;

class AgendamentoBootstrapTest {

    @Test
    void registraTodosAtivosNaInicializacao() {
        AgendamentoRepositoryPort repository = mock(AgendamentoRepositoryPort.class);
        AgendamentoSchedulerPort registry = mock(AgendamentoSchedulerPort.class);
        AgendamentoBootstrap bootstrap = new AgendamentoBootstrap(repository, registry);

        Agendamento a = Agendamento.criar(
                null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        when(repository.listarAtivos()).thenReturn(List.of(a));

        bootstrap.run(mock(ApplicationArguments.class));

        verify(registry).registrar(a);
    }
}
