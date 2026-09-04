package com.poccurves.orchestrator.adapter.out.metrics;

import com.poccurves.orchestrator.application.MetricasRepositoryPort;
import com.poccurves.orchestrator.domain.EstadoExecucao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecucaoMetricsBinderTest {

    @Test
    void registraUmGaugePorEstadoMaisTentativasEDuracaoMedia() {
        MetricasRepositoryPort metricasRepository = mock(MetricasRepositoryPort.class);
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (EstadoExecucao estado : EstadoExecucao.values()) {
            contagem.put(estado.name(), 0);
        }
        contagem.put("EXECUTANDO", 5);
        when(metricasRepository.contarPorEstado()).thenReturn(contagem);
        when(metricasRepository.mediaTentativas()).thenReturn(1.5);
        when(metricasRepository.duracaoMediaSegundos()).thenReturn(42.0);

        MeterRegistry registry = new SimpleMeterRegistry();
        new ExecucaoMetricsBinder(metricasRepository).bindTo(registry);

        for (EstadoExecucao estado : EstadoExecucao.values()) {
            Gauge gauge = registry.find("orchestrator.execucoes.por.estado").tag("estado", estado.name()).gauge();
            assertThat(gauge).as("gauge para o estado " + estado).isNotNull();
        }

        Gauge executandoGauge = registry.find("orchestrator.execucoes.por.estado").tag("estado", "EXECUTANDO").gauge();
        assertThat(executandoGauge.value()).isEqualTo(5.0);

        Gauge tentativasGauge = registry.find("orchestrator.execucoes.tentativas.media").gauge();
        assertThat(tentativasGauge.value()).isEqualTo(1.5);

        Gauge duracaoGauge = registry.find("orchestrator.execucoes.duracao.media.segundos").gauge();
        assertThat(duracaoGauge.value()).isEqualTo(42.0);
    }
}
