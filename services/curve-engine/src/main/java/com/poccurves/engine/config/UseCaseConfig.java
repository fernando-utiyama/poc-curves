package com.poccurves.engine.config;
import com.poccurves.engine.application.model.CurveBootstrapper;
import com.poccurves.engine.application.model.InterpoladorRegistry;
import com.poccurves.engine.application.model.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.application.port.CacheInterpolacaoPort;
import com.poccurves.engine.application.port.CurvaPublicadaEventPort;
import com.poccurves.engine.application.port.DefinicaoCurvaResolutionRepositoryPort;
import com.poccurves.engine.application.port.InsumoDI1RepositoryPort;
import com.poccurves.engine.application.port.JsonPort;
import com.poccurves.engine.application.port.ModeloConstrucaoPort;
import com.poccurves.engine.application.port.ModeloCurvaRepositoryPort;
import com.poccurves.engine.application.port.ProcedenciaCurvaRepositoryPort;
import com.poccurves.engine.application.port.ValidacaoCurvaRepositoryPort;
import com.poccurves.engine.application.port.VersaoCurvaRepositoryPort;
import com.poccurves.engine.application.port.VerticeCurvaRepositoryPort;
import com.poccurves.engine.application.service.BateriaValidacaoService;
import com.poccurves.engine.application.service.ConstrucaoCurvaService;
import com.poccurves.engine.application.service.ModeloConstrucaoResolver;
import com.poccurves.engine.application.service.PromocaoVersaoCurvaService;
import com.poccurves.engine.application.usecase.CompararModelosService;
import com.poccurves.engine.application.usecase.ConsoleDesenvolvimentoModeloService;
import com.poccurves.engine.application.usecase.ImportarModeloGroovyService;
import com.poccurves.engine.application.usecase.InterpolacaoService;
import com.poccurves.engine.application.usecase.ListarModelosService;
import com.poccurves.engine.application.usecase.PublicacaoCurvaService;
import com.poccurves.engine.application.validator.TesteComparacaoCurvaImportada;
import com.poccurves.engine.application.validator.TesteEstrutural;
import com.poccurves.engine.application.validator.TesteFaixaPlausivelTaxa;
import com.poccurves.engine.application.validator.TesteLimiteTaxaForward;
import com.poccurves.engine.application.validator.TesteMonotonicidadeFatorDesconto;
import com.poccurves.engine.application.validator.TesteReprecificacaoInstrumentosCalibracao;
import com.poccurves.engine.application.validator.TesteSuavidadeEstruturaTermo;
import com.poccurves.engine.application.validator.TesteValidacao;
import com.poccurves.engine.application.validator.TesteVariacaoCurvaAnterior;

import com.poccurves.engine.adapter.out.construcao.GroovyModeloConstrucao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class UseCaseConfig {

    @Bean
    public List<TesteValidacao> testesValidacao() {
        return List.of(
                new TesteComparacaoCurvaImportada(),
                new TesteEstrutural(),
                new TesteFaixaPlausivelTaxa(),
                new TesteLimiteTaxaForward(),
                new TesteMonotonicidadeFatorDesconto(),
                new TesteReprecificacaoInstrumentosCalibracao(),
                new TesteSuavidadeEstruturaTermo(),
                new TesteVariacaoCurvaAnterior()
        );
    }

    /**
     * Classe pura de domínio (sem framework), mas precisa existir como bean porque
     * {@link com.poccurves.engine.adapter.out.construcao.BuiltinModeloConstrucao} a recebe por
     * injeção de construtor — sem este @Bean o contexto Spring não sobe (gap pré-existente,
     * nunca notado antes porque o serviço nunca tinha sido rodado com spring-boot:run).
     */
    @Bean
    public com.poccurves.engine.application.model.CurveBootstrapper curveBootstrapper() {
        return new com.poccurves.engine.application.model.CurveBootstrapper();
    }

    /** Spring injeta aqui a lista dos dois @Component que implementam a porta (BuiltinModeloConstrucao, GroovyModeloConstrucao). */
    @Bean
    public ModeloConstrucaoResolver modeloConstrucaoResolver(List<ModeloConstrucaoPort> implementacoes) {
        return new ModeloConstrucaoResolver(implementacoes);
    }

    @Bean
    public ConstrucaoCurvaService construcaoCurvaService(
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            VersaoCurvaRepositoryPort versaoCurvaRepository,
            VerticeCurvaRepositoryPort verticeCurvaRepository,
            ProcedenciaCurvaRepositoryPort procedenciaCurvaRepository,
            InsumoDI1RepositoryPort insumoDI1Repository,
            ModeloConstrucaoResolver modeloConstrucaoResolver,
            JsonPort jsonPort) {
        return new ConstrucaoCurvaService(
                definicaoCurvaResolutionRepository, modeloCurvaRepository, versaoCurvaRepository,
                verticeCurvaRepository, procedenciaCurvaRepository, insumoDI1Repository,
                modeloConstrucaoResolver, jsonPort);
    }

    @Bean
    public PromocaoVersaoCurvaService promocaoVersaoCurvaService(VersaoCurvaRepositoryPort versaoCurvaRepository) {
        return new PromocaoVersaoCurvaService(versaoCurvaRepository);
    }

    @Bean
    public BateriaValidacaoService bateriaValidacaoService(
            List<TesteValidacao> testesValidacao, ValidacaoCurvaRepositoryPort validacaoCurvaRepository) {
        return new BateriaValidacaoService(testesValidacao, validacaoCurvaRepository);
    }

    @Bean
    public PublicacaoCurvaService publicacaoCurvaService(
            ConstrucaoCurvaService construcaoCurvaService,
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            VerticeCurvaRepositoryPort verticeCurvaRepository,
            InsumoDI1RepositoryPort insumoDI1Repository,
            VersaoCurvaRepositoryPort versaoCurvaRepository,
            BateriaValidacaoService bateriaValidacaoService,
            CurvaPublicadaEventPort curvaPublicadaEventPublisher,
            PromocaoVersaoCurvaService promocaoVersaoCurvaService) {
        return new PublicacaoCurvaService(
                construcaoCurvaService, definicaoCurvaResolutionRepository, verticeCurvaRepository,
                insumoDI1Repository, versaoCurvaRepository, bateriaValidacaoService,
                curvaPublicadaEventPublisher, promocaoVersaoCurvaService);
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
    public CompararModelosService compararModelosService(
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            InsumoDI1RepositoryPort insumoDI1Repository,
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            ModeloConstrucaoResolver modeloConstrucaoResolver) {
        return new CompararModelosService(
                definicaoCurvaResolutionRepository, insumoDI1Repository, modeloCurvaRepository, modeloConstrucaoResolver);
    }

    @Bean
    public ConsoleDesenvolvimentoModeloService consoleDesenvolvimentoModeloService(
            GroovyModeloConstrucao groovyModeloConstrucao) {
        return new ConsoleDesenvolvimentoModeloService(groovyModeloConstrucao);
    }
}
