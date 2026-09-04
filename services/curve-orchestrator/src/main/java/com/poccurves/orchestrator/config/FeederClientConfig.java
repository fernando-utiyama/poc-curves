package com.poccurves.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuração do cliente HTTP para o feeder-marketdata-http.
 * Acionamento de aquisição de insumos (BVBG.086, BVBG.028, etc.).
 */
@Configuration
public class FeederClientConfig {

    @Value("${services.feeder-marketdata.url}")
    private String feederMarketdataUrl;

    @Value("${services.feeder-marketdata.timeout-seconds}")
    private int feederMarketdataTimeoutSeconds;

    @Bean
    public RestClient feederAcquisitionRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(feederMarketdataTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(feederMarketdataTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(feederMarketdataUrl)
                .requestFactory(factory)
                .build();
    }
}
