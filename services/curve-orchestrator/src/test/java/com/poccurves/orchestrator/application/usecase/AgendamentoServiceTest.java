package com.poccurves.orchestrator.application.usecase;
import com.poccurves.orchestrator.application.model.Agendamento;
import com.poccurves.orchestrator.application.model.Faixa;
import com.poccurves.orchestrator.application.model.MomentoCurva;
import com.poccurves.orchestrator.application.port.AgendamentoRepositoryPort;
import com.poccurves.orchestrator.application.port.AgendamentoSchedulerPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgendamentoServiceTest {

    private AgendamentoRepositoryPort repository;
    private AgendamentoSchedulerPort registry;
    private AgendamentoService service;

    @BeforeEach
    void setUp() {
        repository = mock(AgendamentoRepositoryPort.class);
        registry = mock(AgendamentoSchedulerPort.class);
        service = new AgendamentoService(repository, registry);
    }

    @Test
    void cadastrarPersisteERegistra() {
        Agendamento a = service.cadastrar(
                null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        verify(repository).inserir(a);
        verify(registry).registrar(a);
        assertThat(a).isNotNull();
    }

    @Test
    void editarAtualizaEReagenda() {
        Agendamento a = Agendamento.criar(
                null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        when(repository.buscarPorId(a.id())).thenReturn(Optional.of(a));

        service.editar(a.id(), "0 0 13 * * ?", "UTC", 45, 120, Faixa.PRIORITARIA);

        verify(repository).atualizar(a);
        verify(registry).reagendar(a);
    }
}
