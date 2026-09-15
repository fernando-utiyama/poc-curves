package com.poccurves.orchestrator.application.port;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface BuildRequestPort {
    void publicar(String curveCode, LocalDate referenceDate, String curveMoment, UUID runId, UUID executionId, LocalTime horarioLimitePublicacao);
}
