package com.poccurves.orchestrator.adapter.in.scheduling;
import com.poccurves.orchestrator.application.usecase.ReconciliacaoService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Chama {@link ReconciliacaoService} periodicamente, não só no boot
 * (openspec/changes/orchestrator-multi-instance-scheduling) — com múltiplas réplicas, uma
 * execução presa deixada por uma réplica que caiu no meio do processamento só é recuperada se
 * OUTRA réplica viva detectar isso; esperar por um reinício não é mais suficiente. Mesmo
 * `limiteMinutos` de {@code adapter.in.bootstrap.ReconciliacaoInicializacaoRunner}, lido da
 * mesma propriedade — as duas chamadas (boot e periódica) precisam do mesmo critério de
 * "presa de verdade".
 */
@Component
public class ReconciliacaoExecucoesPresasScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliacaoExecucoesPresasScheduler.class);

    private final ReconciliacaoService reconciliacaoService;
    private final long limiteMinutos;

    public ReconciliacaoExecucoesPresasScheduler(
            ReconciliacaoService reconciliacaoService,
            @Value("${agendamento.execucao-presa.limite-minutos:60}") long limiteMinutos
    ) {
        this.reconciliacaoService = reconciliacaoService;
        this.limiteMinutos = limiteMinutos;
    }

    @Scheduled(fixedDelayString = "${agendamento.execucao-presa.intervalo-segundos:60}000")
    public void reconciliarPeriodicamente() {
        int quantidade = reconciliacaoService.reconciliarExecucoesPresas(Duration.ofMinutes(limiteMinutos));
        if (quantidade > 0) {
            log.warn("Reconciliação periódica: {} execuções presas encontradas e marcadas como falhas.", quantidade);
        }
    }
}
