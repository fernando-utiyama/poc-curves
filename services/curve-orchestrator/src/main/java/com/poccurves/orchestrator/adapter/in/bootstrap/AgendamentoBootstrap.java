package com.poccurves.orchestrator.adapter.in.bootstrap;

import com.poccurves.orchestrator.application.AgendamentoRepositoryPort;
import com.poccurves.orchestrator.application.AgendamentoSchedulerPort;
import com.poccurves.orchestrator.domain.Agendamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgendamentoBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoBootstrap.class);

    private final AgendamentoRepositoryPort agendamentoRepository;
    private final AgendamentoSchedulerPort schedulerRegistry;

    public AgendamentoBootstrap(
            AgendamentoRepositoryPort agendamentoRepository,
            AgendamentoSchedulerPort schedulerRegistry
    ) {
        this.agendamentoRepository = agendamentoRepository;
        this.schedulerRegistry = schedulerRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Inicializando bootstrap de agendamentos...");
        List<Agendamento> ativos = agendamentoRepository.listarAtivos();
        for (Agendamento agendamento : ativos) {
            schedulerRegistry.registrar(agendamento);
        }
        log.info("Bootstrap concluído: {} agendamentos registrados.", ativos.size());
    }
}
