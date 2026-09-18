package com.poccurves.api.config;

import com.poccurves.api.application.CurvaDadosRepositoryPort;
import com.poccurves.api.application.CurvaDadosService;
import com.poccurves.api.application.CurvaMercadoRepositoryPort;
import com.poccurves.api.application.CurvaMercadoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único ponto de wiring dos casos de uso em {@code application} — nenhum deles tem anotação
 * Spring (guarda de arquitetura hexagonal, ver openspec/changes/hexagonal-architecture).
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public CurvaMercadoService curvaMercadoService(CurvaMercadoRepositoryPort curvaMercadoRepositoryPort) {
        return new CurvaMercadoService(curvaMercadoRepositoryPort);
    }

    @Bean
    public CurvaDadosService curvaDadosService(CurvaDadosRepositoryPort curvaDadosRepositoryPort) {
        return new CurvaDadosService(curvaDadosRepositoryPort);
    }
}
