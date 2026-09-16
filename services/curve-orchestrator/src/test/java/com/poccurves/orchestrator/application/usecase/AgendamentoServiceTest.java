package com.poccurves.orchestrator.application.usecase;
import com.poccurves.orchestrator.application.model.Agendamento;
import com.poccurves.orchestrator.application.model.Faixa;
import com.poccurves.orchestrator.application.model.MomentoCurva;
import com.poccurves.orchestrator.application.port.AgendamentoRepositoryPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgendamentoServiceTest {

    private AgendamentoRepositoryPort repository;
    private AgendamentoService service;

    @BeforeEach
    void setUp() {
        repository = mock(AgendamentoRepositoryPort.class);
        service = new AgendamentoService(repository);
    }

    @Test
    void cadastrarPersiste() {
        // A convergência do scheduler local de cada réplica não é mais responsabilidade deste
        // serviço — é feita pela reconciliação periódica (ReconciliacaoAgendamentosService,
        // openspec/changes/orchestrator-multi-instance-scheduling).
        Agendamento a = service.cadastrar(
                null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        verify(repository).inserir(a);
        assertThat(a).isNotNull();
    }

    @Test
    void editarAtualiza() {
        Agendamento a = Agendamento.criar(
                null, "CONJUNTO", MomentoCurva.INTRADIA, Faixa.ROTINA,
                "0 0 12 * * ?", "America/Sao_Paulo", 30, 60, "teste"
        );
        when(repository.buscarPorId(a.id())).thenReturn(Optional.of(a));

        service.editar(a.id(), "0 0 13 * * ?", "UTC", 45, 120, Faixa.PRIORITARIA);

        verify(repository).atualizar(a);
    }
}
