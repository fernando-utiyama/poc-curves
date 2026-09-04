package com.poccurves.bff.config;

import com.poccurves.bff.application.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único ponto de wiring dos casos de uso em {@code application} — eles são POJOs sem anotação
 * Spring (guarda de arquitetura hexagonal, ver openspec/changes/hexagonal-architecture), então
 * esta classe é o adaptador fino que os instancia e injeta as portas concretas.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public CatalogoService catalogoService(CurveApiPort curveApiPort) {
        return new CatalogoService(curveApiPort);
    }

    @Bean
    public ComparacaoService comparacaoService(CurveApiPort curveApiPort, CurveEnginePort curveEnginePort) {
        return new ComparacaoService(curveApiPort, curveEnginePort);
    }

    @Bean
    public InterpolacaoService interpolacaoService(CurveEnginePort curveEnginePort) {
        return new InterpolacaoService(curveEnginePort);
    }

    @Bean
    public ModelosService modelosService(CurveEnginePort curveEnginePort, CurveApiPort curveApiPort) {
        return new ModelosService(curveEnginePort, curveApiPort);
    }

    @Bean
    public ExecucoesService execucoesService(CurveOrchestratorPort curveOrchestratorPort) {
        return new ExecucoesService(curveOrchestratorPort);
    }

    @Bean
    public PainelDoDiaService painelDoDiaService(CurveApiPort curveApiPort, CurveOrchestratorPort curveOrchestratorPort) {
        return new PainelDoDiaService(curveApiPort, curveOrchestratorPort);
    }

    @Bean
    public AlertasService alertasService(CurveOrchestratorPort curveOrchestratorPort, PainelDoDiaService painelDoDiaService) {
        return new AlertasService(curveOrchestratorPort, painelDoDiaService);
    }

    @Bean
    public CurvaViewerService curvaViewerService(CurveApiPort curveApiPort, CurveOrchestratorPort curveOrchestratorPort) {
        return new CurvaViewerService(curveApiPort, curveOrchestratorPort);
    }

    @Bean
    public DisparoManualService disparoManualService(CurveOrchestratorPort curveOrchestratorPort) {
        return new DisparoManualService(curveOrchestratorPort);
    }

    @Bean
    public PendenciasService pendenciasService(CurveOrchestratorPort curveOrchestratorPort) {
        return new PendenciasService(curveOrchestratorPort);
    }

    @Bean
    public CargaManualService cargaManualService(CurveOrchestratorPort curveOrchestratorPort) {
        return new CargaManualService(curveOrchestratorPort);
    }
}
