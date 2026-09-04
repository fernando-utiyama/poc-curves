package com.poccurves.orchestrator.application;

import java.util.Map;

public interface MetricasRepositoryPort {
    Map<String, Integer> contarPorEstado();
    Double mediaTentativas();
    Double duracaoMediaSegundos();
}
