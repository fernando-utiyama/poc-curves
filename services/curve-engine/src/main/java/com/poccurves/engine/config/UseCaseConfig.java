package com.poccurves.engine.config;
import com.poccurves.engine.domain.construcao.CurveBootstrapper;
import com.poccurves.engine.domain.interpolacao.InterpoladorRegistry;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.domain.validacao.TesteComparacaoCurvaImportada;
import com.poccurves.engine.domain.validacao.TesteEstrutural;
import com.poccurves.engine.domain.validacao.TesteFaixaPlausivelTaxa;
import com.poccurves.engine.domain.validacao.TesteLimiteTaxaForward;
import com.poccurves.engine.domain.validacao.TesteMonotonicidadeFatorDesconto;
import com.poccurves.engine.domain.validacao.TesteReprecificacaoInstrumentosCalibracao;
import com.poccurves.engine.domain.validacao.TesteSuavidadeEstruturaTermo;
import com.poccurves.engine.domain.validacao.TesteValidacao;
import com.poccurves.engine.domain.validacao.TesteVariacaoCurvaAnterior;

import com.poccurves.engine.application.BateriaValidacaoService;
import com.poccurves.engine.application.CacheInterpolacaoPort;
import com.poccurves.engine.application.CompararModelosService;
import com.poccurves.engine.application.ConsoleDesenvolvimentoModeloService;
import com.poccurves.engine.application.ConstrucaoCurvaService;
import com.poccurves.engine.application.CurvaPublicadaEventPort;
import com.poccurves.engine.application.DefinicaoCurvaResolutionRepositoryPort;
import com.poccurves.engine.application.ImportarModeloGroovyService;
import com.poccurves.engine.application.InsumoDI1RepositoryPort;
import com.poccurves.engine.application.InterpolacaoService;
import com.poccurves.engine.application.JsonPort;
import com.poccurves.engine.application.ListarModelosService;
import com.poccurves.engine.adapter.out.construcao.GroovyModeloConstrucao;
import com.poccurves.engine.application.ModeloConstrucaoPort;
import com.poccurves.engine.application.ModeloConstrucaoResolver;
import com.poccurves.engine.application.ModeloCurvaRepositoryPort;
import com.poccurves.engine.application.ProcedenciaCurvaRepositoryPort;
import com.poccurves.engine.application.PromocaoVersaoCurvaService;
import com.poccurves.engine.application.PublicacaoCurvaService;
import com.poccurves.engine.application.ValidacaoCurvaRepositoryPort;
import com.poccurves.engine.application.VersaoCurvaRepositoryPort;
import com.poccurves.engine.application.VerticeCurvaRepositoryPort;
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
    public com.poccurves.engine.domain.construcao.CurveBootstrapper curveBootstrapper() {
        return new com.poccurves.engine.domain.construcao.CurveBootstrapper();
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
