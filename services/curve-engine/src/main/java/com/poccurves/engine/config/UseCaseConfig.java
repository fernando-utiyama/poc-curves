package com.poccurves.engine.config;
import com.poccurves.engine.application.construcao.InterpoladorRegistry;
import com.poccurves.engine.application.construcao.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.application.port.BtrsCurvaPrimrConsultaRepositoryPort;
import com.poccurves.engine.application.port.CacheInterpolacaoPort;
import com.poccurves.engine.application.port.ConfgCurvaRepositoryPort;
import com.poccurves.engine.application.port.CurvaDataRepositoryPort;
import com.poccurves.engine.application.port.DadoCurvaRepositoryPort;
import com.poccurves.engine.application.port.DefinicaoCurvaResolutionRepositoryPort;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;
import com.poccurves.engine.application.port.VersaoCurvaRepositoryPort;
import com.poccurves.engine.application.port.VerticeCurvaRepositoryPort;
import com.poccurves.engine.application.service.ConstrucaoCurvaB3Service;
import com.poccurves.engine.application.service.ModeloConstrucaoResolver;
import com.poccurves.engine.application.usecase.ConsoleDesenvolvimentoModeloService;
import com.poccurves.engine.application.usecase.ImportarModeloGroovyService;
import com.poccurves.engine.application.usecase.InterpolacaoService;
import com.poccurves.engine.application.usecase.ListarModelosService;

import com.poccurves.engine.adapter.out.construcao.GroovyModeloConstrucao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class UseCaseConfig {

    /** Spring injeta aqui a lista dos dois @Component que implementam a porta (BuiltinModeloConstrucao, GroovyModeloConstrucao). */
    @Bean
    public ModeloConstrucaoResolver modeloConstrucaoResolver(List<ModeloConstrucaoPort> implementacoes) {
        return new ModeloConstrucaoResolver(implementacoes);
    }

    @Bean
    public ConstrucaoCurvaB3Service construcaoCurvaB3Service(
            BtrsCurvaPrimrConsultaRepositoryPort btrsCurvaPrimrRepository,
            ConfgCurvaRepositoryPort confgCurvaRepository,
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            ModeloConstrucaoResolver modeloConstrucaoResolver,
            DadoCurvaRepositoryPort dadoCurvaRepository,
            CurvaDataRepositoryPort curvaDataRepository) {
        return new ConstrucaoCurvaB3Service(
                btrsCurvaPrimrRepository, confgCurvaRepository, modeloCurvaRepository,
                modeloConstrucaoResolver, dadoCurvaRepository, curvaDataRepository);
    }

    @Bean
    public InterpolacaoService interpolacaoService(
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            VersaoCurvaRepositoryPort versaoCurvaRepository,
            VerticeCurvaRepositoryPort verticeCurvaRepository,
            InterpoladorRegistry interpoladorRegistry,
            PoliticaExtrapolacaoRegistry politicaExtrapolacaoRegistry,
            CacheInterpolacaoPort cacheInterpolacaoPort) {
        return new InterpolacaoService(
                definicaoCurvaResolutionRepository, versaoCurvaRepository, verticeCurvaRepository,
                interpoladorRegistry, politicaExtrapolacaoRegistry, cacheInterpolacaoPort);
    }

    @Bean
    public ListarModelosService listarModelosService(ModeloCurvaRepositoryPort modeloCurvaRepository) {
        return new ListarModelosService(modeloCurvaRepository);
    }

    /**
     * Injeta especificamente GroovyModeloConstrucao (não o resolver genérico) — importar um
     * modelo GROOVY sempre valida contra a implementação Groovy, nunca precisa escolher entre
     * BUILTIN/GROOVY como a construção real faz.
     */
    @Bean
    public ImportarModeloGroovyService importarModeloGroovyService(
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            GroovyModeloConstrucao groovyModeloConstrucao) {
        return new ImportarModeloGroovyService(modeloCurvaRepository, groovyModeloConstrucao);
    }

    @Bean
    public ConsoleDesenvolvimentoModeloService consoleDesenvolvimentoModeloService(
            GroovyModeloConstrucao groovyModeloConstrucao) {
        return new ConsoleDesenvolvimentoModeloService(groovyModeloConstrucao);
    }
}
