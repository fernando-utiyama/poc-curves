package com.poccurves.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class CurveProcessorClientConfig {

    @Value("${services.curve-processor.url}")
    private String curveProcessorUrl;

    @Value("${services.curve-processor.timeout-seconds}")
    private int curveProcessorTimeoutSeconds;

    @Bean
    public RestClient curveProcessorRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(curveProcessorTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(curveProcessorTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(curveProcessorUrl)
                .requestFactory(factory)
                .build();
    }
}
