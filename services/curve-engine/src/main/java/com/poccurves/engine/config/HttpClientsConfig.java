package com.poccurves.engine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class HttpClientsConfig {

    @Value("${services.curve-orchestrator.url:http://localhost:8084}")
    private String curveOrchestratorUrl;

    @Value("${services.curve-orchestrator.timeout-seconds:5}")
    private int curveOrchestratorTimeout;

    private SimpleClientHttpRequestFactory createRequestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }

    @Bean
    public RestClient curveOrchestratorClient() {
        return RestClient.builder()
                .baseUrl(curveOrchestratorUrl)
                .requestFactory(createRequestFactory(curveOrchestratorTimeout))
                .build();
    }
}
