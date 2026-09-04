package com.poccurves.orchestrator.adapter.out.http;

import com.poccurves.orchestrator.application.BuildRequestPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Cliente HTTP para o serviço {@code curve-engine}.
 * <p>
 * Despacha o comando de construção de curva via {@code POST /api/v1/construcoes}
 * e aguarda confirmação {@code 202 Accepted}.
 */
@Component
public class CurveEngineClient implements BuildRequestPort {

    record ConstrucaoRequest(
            String curveCode,
            String referenceDate,
            String curveMoment,
            String runId,
            String executionId
    ) {}

    private final RestClient restClient;

    public CurveEngineClient(@Qualifier("curveEngineRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void publicar(String curveCode, LocalDate referenceDate, String curveMoment, UUID runId, UUID executionId, LocalTime horarioLimitePublicacao) {
        ConstrucaoRequest corpo = new ConstrucaoRequest(
                curveCode,
                referenceDate != null ? referenceDate.toString() : null,
                curveMoment,
                runId != null ? runId.toString() : null,
                executionId != null ? executionId.toString() : null
        );

        restClient.post()
                .uri("/api/v1/construcoes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toBodilessEntity();
    }
}
