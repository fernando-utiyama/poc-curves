package com.poccurves.orchestrator.adapter.in.bootstrap;
import com.poccurves.orchestrator.application.usecase.ReconciliacaoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReconciliacaoInicializacaoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoInicializacaoRunner.class);

    private final ReconciliacaoService reconciliacaoService;
    private final long limiteMinutos;

    public ReconciliacaoInicializacaoRunner(
            ReconciliacaoService reconciliacaoService,
            @Value("${agendamento.execucao-presa.limite-minutos:60}") long limiteMinutos
    ) {
        this.reconciliacaoService = reconciliacaoService;
        this.limiteMinutos = limiteMinutos;
    }

    @Override
    public void run(ApplicationArguments args) {
        int quantidadePresas = reconciliacaoService.reconciliarExecucoesPresas(Duration.ofMinutes(limiteMinutos));

        if (quantidadePresas == 0) {
            log.info("Reconciliação concluída: 0 execuções presas encontradas.");
        } else {
            log.warn("Reconciliação concluída: {} execuções presas encontradas e marcadas como falhas.", quantidadePresas);
        }
    }
}
