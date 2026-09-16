package com.poccurves.orchestrator.adapter.out.http;
import com.poccurves.orchestrator.application.port.ConstrucaoCurvaB3Port;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

/**
 * Cliente HTTP para o despacho de construção das curvas TS B3 no curve-engine.
 * <p>
 * Despacha via {@code POST /api/v1/curvas-b3/construir} e aguarda confirmação
 * {@code 202 Accepted} — mesmo cliente REST já usado por {@link CurveEngineClient}, endpoint
 * diferente.
 */
@Component
public class CurveEngineB3Client implements ConstrucaoCurvaB3Port {

    record ConstrucaoCurvaB3Request(String tickerIndcd, String referenceDate) {}

    private final RestClient restClient;

    public CurveEngineB3Client(@Qualifier("curveEngineRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void construir(String tickerIndcd, LocalDate referenceDate) {
        ConstrucaoCurvaB3Request corpo = new ConstrucaoCurvaB3Request(
                tickerIndcd,
                referenceDate != null ? referenceDate.toString() : null);

        restClient.post()
                .uri("/api/v1/curvas-b3/construir")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .toBodilessEntity();
    }
}
