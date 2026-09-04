package com.poccurves.processor.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Métricas de ingestão (tarefa 7.3), via Micrometer — autoconfigurado pelo
 * spring-boot-starter-actuator, exposto em /actuator/metrics. Micrometer não é
 * banido da fronteira domain/application (ver ArchitectureTest deste módulo):
 * é tratado como concern técnico transversal (mesma categoria de slf4j), não
 * como porta de infraestrutura substituível.
 */
public class MetricasIngestao {

    private final Counter eventosConsumidos;
    private final Counter pontosGravados;
    private final Counter divergencias;
    private final Counter curvasPublicadas;
    private final Counter mensagensDeadLetter;

    public MetricasIngestao(MeterRegistry registry) {
        this.eventosConsumidos = Counter.builder("curve_processor.eventos.consumidos")
                .description("Total de eventos de ingestão consumidos das três faixas")
                .register(registry);
        this.pontosGravados = Counter.builder("curve_processor.pontos.gravados")
                .description("Total de pontos de dado de mercado gravados")
                .register(registry);
        this.divergencias = Counter.builder("curve_processor.divergencias")
                .description("Total de divergências detectadas na regravação de pontos")
                .register(registry);
        this.curvasPublicadas = Counter.builder("curve_processor.curvas.publicadas")
                .description("Total de versões de curva publicadas (importada ou carregada)")
                .register(registry);
        this.mensagensDeadLetter = Counter.builder("curve_processor.dead_letter.mensagens")
                .description("Total de mensagens encaminhadas à dead-letter")
                .register(registry);
    }

    public Counter eventosConsumidos() {
        return eventosConsumidos;
    }

    public Counter pontosGravados() {
        return pontosGravados;
    }

    public Counter divergencias() {
        return divergencias;
    }

    public Counter curvasPublicadas() {
        return curvasPublicadas;
    }

    public Counter mensagensDeadLetter() {
        return mensagensDeadLetter;
    }
}
