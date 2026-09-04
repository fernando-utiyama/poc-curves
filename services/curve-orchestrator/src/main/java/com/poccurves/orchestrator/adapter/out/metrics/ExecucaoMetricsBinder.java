package com.poccurves.orchestrator.adapter.out.metrics;

import com.poccurves.orchestrator.application.MetricasRepositoryPort;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class ExecucaoMetricsBinder implements MeterBinder {

    private final MetricasRepositoryPort metricasRepository;

    public ExecucaoMetricsBinder(MetricasRepositoryPort metricasRepository) {
        this.metricasRepository = metricasRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (EstadoExecucao estado : EstadoExecucao.values()) {
            Gauge.builder("orchestrator.execucoes.por.estado", metricasRepository,
                          repo -> repo.contarPorEstado().getOrDefault(estado.name(), 0))
                 .tag("estado", estado.name())
                 .register(registry);
        }

        Gauge.builder("orchestrator.execucoes.tentativas.media", metricasRepository, MetricasRepositoryPort::mediaTentativas)
             .register(registry);

        Gauge.builder("orchestrator.execucoes.duracao.media.segundos", metricasRepository, MetricasRepositoryPort::duracaoMediaSegundos)
             .register(registry);
    }
}
