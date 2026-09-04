package com.poccurves.orchestrator.adapter.in.bootstrap;

import com.poccurves.orchestrator.application.ReconciliacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReconciliacaoInicializacaoRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoInicializacaoRunner.class);

    private final ReconciliacaoService reconciliacaoService;

    public ReconciliacaoInicializacaoRunner(ReconciliacaoService reconciliacaoService) {
        this.reconciliacaoService = reconciliacaoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int quantidadePresas = reconciliacaoService.reconciliarExecucoesPresas();

        if (quantidadePresas == 0) {
            log.info("Reconciliação concluída: 0 execuções presas encontradas.");
        } else {
            log.warn("Reconciliação concluída: {} execuções presas encontradas e marcadas como falhas.", quantidadePresas);
        }
    }
}
