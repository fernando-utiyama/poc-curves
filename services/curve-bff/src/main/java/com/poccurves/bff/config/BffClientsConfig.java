package com.poccurves.bff.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class BffClientsConfig {

    @Value("${services.curve-api.url:http://localhost:8082}")
    private String curveApiUrl;

    @Value("${services.curve-api.timeout-seconds:5}")
    private int curveApiTimeout;

    @Value("${services.curve-engine.url:http://localhost:8083}")
    private String curveEngineUrl;

    @Value("${services.curve-engine.timeout-seconds:3}")
    private int curveEngineTimeout;

    @Value("${services.curve-orchestrator.url:http://localhost:8084}")
    private String curveOrchestratorUrl;

    @Value("${services.curve-orchestrator.timeout-seconds:5}")
    private int curveOrchestratorTimeout;

    private ClientHttpRequestInterceptor headerPropagationInterceptor() {
        return (request, body, execution) -> {
            // Propaga Correlation ID do MDC
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
            }

            // Propaga contexto do usuário autenticado para auditoria nas APIs internas
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                request.getHeaders().set("X-User", auth.getName());
                String roles = auth.getAuthorities().stream()
                        .map(a -> a.getAuthority().replace("ROLE_", ""))
                        .reduce((a, b) -> a + "," + b)
                        .orElse("VIEWER");
                request.getHeaders().set("X-User-Roles", roles);
            }

            // Relay do token JWT original — curve-api valida a própria
            // autenticação/autorização de forma independente (defesa em
            // profundidade, corrigido na auditoria desta sessão: antes só o
            // X-User/X-User-Roles derivados eram propagados, que são
            // facilmente forjáveis por quem chamasse curve-api diretamente,
            // já que a porta 8082 é publicada no host).
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                request.getHeaders().setBearerAuth(jwtAuth.getToken().getTokenValue());
            }

            return execution.execute(request, body);
        };
    }

    private SimpleClientHttpRequestFactory createRequestFactory(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return factory;
    }

    @Bean
    public RestClient curveApiClient() {
        return RestClient.builder()
                .baseUrl(curveApiUrl)
                .requestFactory(createRequestFactory(curveApiTimeout))
                .requestInterceptor(headerPropagationInterceptor())
                .build();
    }

    @Bean
    public RestClient curveEngineClient() {
        return RestClient.builder()
                .baseUrl(curveEngineUrl)
                .requestFactory(createRequestFactory(curveEngineTimeout))
                .requestInterceptor(headerPropagationInterceptor())
                .build();
    }

    @Bean
    public RestClient curveOrchestratorClient() {
        return RestClient.builder()
                .baseUrl(curveOrchestratorUrl)
                .requestFactory(createRequestFactory(curveOrchestratorTimeout))
                .requestInterceptor(headerPropagationInterceptor())
                .build();
    }
}
