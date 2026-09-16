package com.poccurves.orchestrator.adapter.in.scheduling;
import com.poccurves.orchestrator.application.usecase.ReconciliacaoAgendamentosService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chama {@link ReconciliacaoAgendamentosService} periodicamente, não só no boot
 * (openspec/changes/orchestrator-multi-instance-scheduling) — com múltiplas réplicas, um
 * agendamento criado, editado, ativado ou desativado só converge em todas as réplicas se cada
 * uma revisitar o catálogo regularmente; antes, só a réplica que atendeu a requisição HTTP
 * original sabia da mudança.
 */
@Component
public class AgendamentoReconciliacaoScheduler {

    private final ReconciliacaoAgendamentosService reconciliacaoAgendamentosService;

    public AgendamentoReconciliacaoScheduler(ReconciliacaoAgendamentosService reconciliacaoAgendamentosService) {
        this.reconciliacaoAgendamentosService = reconciliacaoAgendamentosService;
    }

    @Scheduled(fixedDelayString = "${agendamento.reconciliacao.intervalo-segundos:30}000")
    public void reconciliarPeriodicamente() {
        reconciliacaoAgendamentosService.reconciliar();
    }
}
