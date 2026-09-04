package com.poccurves.api.config;

import com.poccurves.api.application.*;
import com.poccurves.api.domain.ModeloCargaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único ponto de wiring dos casos de uso em {@code application} e das classes puras em
 * {@code domain} que precisam virar bean — nenhuma delas tem anotação Spring (guarda de
 * arquitetura hexagonal, ver openspec/changes/hexagonal-architecture).
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public ModeloCargaService modeloCargaService() {
        return new ModeloCargaService();
    }

    @Bean
    public DefinicaoCoerenciaValidator definicaoCoerenciaValidator(
            DefinicaoCurvaRepositoryPort definicaoCurvaRepositoryPort,
            VersaoDefinicaoCurvaRepositoryPort versaoDefinicaoCurvaRepositoryPort,
            ModeloCurvaRepositoryPort modeloCurvaRepositoryPort,
            JsonPort jsonPort
    ) {
        return new DefinicaoCoerenciaValidator(
                definicaoCurvaRepositoryPort, versaoDefinicaoCurvaRepositoryPort, modeloCurvaRepositoryPort, jsonPort);
    }

    @Bean
    public DefinicaoCurvaService definicaoCurvaService(
            DefinicaoCurvaRepositoryPort definicaoCurvaRepositoryPort,
            VersaoDefinicaoCurvaRepositoryPort versaoDefinicaoCurvaRepositoryPort,
            ModeloCurvaRepositoryPort modeloCurvaRepositoryPort,
            DefinicaoCoerenciaValidator definicaoCoerenciaValidator,
            ModeloCargaService modeloCargaService,
            JsonPort jsonPort
    ) {
        return new DefinicaoCurvaService(
                definicaoCurvaRepositoryPort, versaoDefinicaoCurvaRepositoryPort, modeloCurvaRepositoryPort,
                definicaoCoerenciaValidator, modeloCargaService, jsonPort);
    }

    @Bean
    public CurvaConsultaService curvaConsultaService(CurvaConsultaRepositoryPort curvaConsultaRepositoryPort) {
        return new CurvaConsultaService(curvaConsultaRepositoryPort);
    }
}
