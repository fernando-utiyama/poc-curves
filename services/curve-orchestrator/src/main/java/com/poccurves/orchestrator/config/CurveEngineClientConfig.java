package com.poccurves.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuração do cliente HTTP para o curve-engine.
 * Despacho de comando de construção de curvas (POST /api/v1/construcoes).
 */
@Configuration
public class CurveEngineClientConfig {

    @Value("${services.curve-engine.url}")
    private String curveEngineUrl;

    @Value("${services.curve-engine.timeout-seconds}")
    private int curveEngineTimeoutSeconds;

    @Bean
    public RestClient curveEngineRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(curveEngineTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(curveEngineTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(curveEngineUrl)
                .requestFactory(factory)
                .build();
    }
}
