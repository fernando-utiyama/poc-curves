package com.poccurves.orchestrator.adapter.in.bootstrap;
import com.poccurves.orchestrator.application.usecase.ReconciliacaoAgendamentosService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * No boot, converge o registro local de agendamentos desta réplica para o catálogo persistido —
 * mesma reconciliação que roda periodicamente depois (ver {@code adapter.in.scheduling.
 * AgendamentoReconciliacaoScheduler}, openspec/changes/orchestrator-multi-instance-scheduling).
 * Antes desta mudança, este runner registrava cada agendamento individualmente e nunca mais
 * revisitava o catálogo — agendamento criado/editado depois do boot só era conhecido pela réplica
 * que atendeu a requisição HTTP.
 */
@Component
public class AgendamentoBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoBootstrap.class);

    private final ReconciliacaoAgendamentosService reconciliacaoAgendamentosService;

    public AgendamentoBootstrap(ReconciliacaoAgendamentosService reconciliacaoAgendamentosService) {
        this.reconciliacaoAgendamentosService = reconciliacaoAgendamentosService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Inicializando bootstrap de agendamentos...");
        reconciliacaoAgendamentosService.reconciliar();
        log.info("Bootstrap de agendamentos concluído.");
    }
}
