package com.poccurves.orchestrator.application.usecase;
import com.poccurves.orchestrator.application.model.Agendamento;
import com.poccurves.orchestrator.application.port.AgendamentoRepositoryPort;
import com.poccurves.orchestrator.application.port.AgendamentoSchedulerPort;

import java.util.List;

/**
 * Reconcilia o registro local de agendamentos (por réplica) contra o catálogo persistido —
 * chamado tanto no boot quanto periodicamente (openspec/changes/orchestrator-multi-instance-scheduling),
 * para que criação/edição/ativação/desativação de agendamento convirja em todas as réplicas sem
 * depender de reinício nem de qual réplica atendeu a requisição HTTP original. Substitui o modelo
 * anterior, em que {@code AgendamentoService} chamava {@code AgendamentoSchedulerPort} diretamente
 * só na réplica que recebeu a requisição.
 */
public class ReconciliacaoAgendamentosService {

    private final AgendamentoRepositoryPort agendamentoRepository;
    private final AgendamentoSchedulerPort schedulerRegistry;

    public ReconciliacaoAgendamentosService(
            AgendamentoRepositoryPort agendamentoRepository,
            AgendamentoSchedulerPort schedulerRegistry
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.schedulerRegistry = schedulerRegistry;
    }

    public void reconciliar() {
        List<Agendamento> ativos = agendamentoRepository.listarAtivos();
        schedulerRegistry.reconciliar(ativos);
    }
}
