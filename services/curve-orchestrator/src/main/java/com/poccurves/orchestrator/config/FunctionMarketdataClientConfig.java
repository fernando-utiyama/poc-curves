package com.poccurves.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuração do cliente HTTP para o function-marketdata-http.
 * Acionamento de aquisição de insumos (BVBG.086, BVBG.028, etc.).
 */
@Configuration
public class FunctionMarketdataClientConfig {

    @Value("${services.function-marketdata.url}")
    private String functionMarketdataUrl;

    @Value("${services.function-marketdata.timeout-seconds}")
    private int functionMarketdataTimeoutSeconds;

    @Bean
    public RestClient functionMarketdataRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(functionMarketdataTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(functionMarketdataTimeoutSeconds));
        return RestClient.builder()
                .baseUrl(functionMarketdataUrl)
                .requestFactory(factory)
                .build();
    }
}
