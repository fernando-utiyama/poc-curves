package com.poccurves.engine.application;
import com.poccurves.engine.domain.curva.Vertice;
import com.poccurves.engine.domain.interpolacao.ConfiguracaoInterpolacao;
import com.poccurves.engine.domain.interpolacao.Interpolador;
import com.poccurves.engine.domain.interpolacao.InterpoladorRegistry;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacao;
import com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacaoRegistry;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.VersaoCurva;

import com.poccurves.engine.dto.EngineDtos.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InterpolacaoService {

    private final DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository;
    private final VersaoCurvaRepositoryPort versaoCurvaRepository;
    private final VerticeCurvaRepositoryPort verticeCurvaRepository;
    private final InterpoladorRegistry interpoladorRegistry;
    private final PoliticaExtrapolacaoRegistry politicaExtrapolacaoRegistry;
    private final CacheInterpolacaoPort cacheInterpolacaoPort;

    public InterpolacaoService(
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            VersaoCurvaRepositoryPort versaoCurvaRepository,
            VerticeCurvaRepositoryPort verticeCurvaRepository,
            InterpoladorRegistry interpoladorRegistry,
            PoliticaExtrapolacaoRegistry politicaExtrapolacaoRegistry,
            CacheInterpolacaoPort cacheInterpolacaoPort) {
        this.definicaoCurvaResolutionRepository = definicaoCurvaResolutionRepository;
        this.versaoCurvaRepository = versaoCurvaRepository;
        this.verticeCurvaRepository = verticeCurvaRepository;
        this.interpoladorRegistry = interpoladorRegistry;
        this.politicaExtrapolacaoRegistry = politicaExtrapolacaoRegistry;
        this.cacheInterpolacaoPort = cacheInterpolacaoPort;
    }

    public Optional<InterpolacaoResponse> interpolar(String codigoCurva, InterpolacaoRequest request) {
        MomentoCurva momento = (request.momento() == null || request.momento().isBlank())
                ? MomentoCurva.FECHAMENTO
                : MomentoCurva.valueOf(request.momento());

        Optional<java.util.UUID> definicaoIdOpt = definicaoCurvaResolutionRepository.resolverIdPorCodigo(codigoCurva);
        if (definicaoIdOpt.isEmpty()) {
            return Optional.empty();
        }
        java.util.UUID definicaoId = definicaoIdOpt.get();

        Optional<VersaoCurva> versaoOpt = request.versao() != null
                ? versaoCurvaRepository.buscarPorNumeroVersao(definicaoId, request.dataReferencia(), momento, request.versao())
                : versaoCurvaRepository.buscarVersaoVigentePublicada(definicaoId, request.dataReferencia(), momento);
        if (versaoOpt.isEmpty()) {
            return Optional.empty();
        }
        VersaoCurva versao = versaoOpt.get();

        List<Vertice> vertices = verticeCurvaRepository.buscarPorVersaoCurva(versao.id());

        ConfiguracaoInterpolacao config = definicaoCurvaResolutionRepository
                .resolverConfiguracaoInterpolacao(codigoCurva, request.dataReferencia())
                .orElseThrow(() -> new IllegalStateException(
                        "versao_curva publicada existe mas versao_definicao_curva vigente não foi encontrada para codigoCurva="
                                + codigoCurva + " dataReferencia=" + request.dataReferencia()));

        var interpolador = interpoladorRegistry.resolver(config.interpolador());
        var politicaExtrapolacao = politicaExtrapolacaoRegistry.resolver(config.politicaExtrapolacao());

        String chavePrefixo = "interpolacao:" + codigoCurva + "|" + request.dataReferencia() + "|" + versao.id() + "|" + interpolador.identificador();

        MathContext mathContext = MathContext.DECIMAL128;
        int prazoMinimo = vertices.get(0).prazoDiasUteis();
        int prazoMaximo = vertices.get(vertices.size() - 1).prazoDiasUteis();

        List<ItemInterpolacaoResultado> resultados = new ArrayList<>();
        for (int prazo : request.prazosDiasUteis()) {
            resultados.add(calcularItem(vertices, interpolador, politicaExtrapolacao, prazo, prazoMinimo, prazoMaximo, mathContext, chavePrefixo));
        }

        return Optional.of(new InterpolacaoResponse(codigoCurva, versao.numeroVersao(), interpolador.identificador(), resultados));
    }

    private ItemInterpolacaoResultado calcularItem(
            List<Vertice> vertices,
            com.poccurves.engine.domain.interpolacao.Interpolador interpolador,
            com.poccurves.engine.domain.interpolacao.PoliticaExtrapolacao politicaExtrapolacao,
            int prazo,
            int prazoMinimo,
            int prazoMaximo,
            MathContext mathContext,
            String chavePrefixo
    ) {
        String chave = chavePrefixo + "|" + prazo;
        Optional<ItemInterpolacaoResultado> cacheado = cacheInterpolacaoPort.buscar(chave, prazo);
        if (cacheado.isPresent()) {
            return cacheado.get();
        }

        for (Vertice vertice : vertices) {
            if (vertice.prazoDiasUteis() == prazo) {
                ItemInterpolacaoResultado resultado = new ItemInterpolacaoResultado(
                        prazo,
                        vertice.taxa().toPlainString(),
                        vertice.fatorDesconto() != null ? vertice.fatorDesconto().toPlainString() : null,
                        "VERTICE_EXATO",
                        null
                );
                cacheInterpolacaoPort.armazenar(chave, resultado);
                return resultado;
            }
        }

        if (prazo >= prazoMinimo && prazo <= prazoMaximo) {
            BigDecimal taxa = interpolador.taxaEm(vertices, prazo, mathContext);
            ItemInterpolacaoResultado resultado = new ItemInterpolacaoResultado(
                    prazo,
                    taxa.toPlainString(),
                    null,
                    "INTERPOLADO",
                    null
            );
            cacheInterpolacaoPort.armazenar(chave, resultado);
            return resultado;
        }

        try {
            BigDecimal taxa = politicaExtrapolacao.taxaEm(vertices, prazo);
            ItemInterpolacaoResultado resultado = new ItemInterpolacaoResultado(
                    prazo,
                    taxa.toPlainString(),
                    null,
                    "EXTRAPOLADO",
                    null
            );
            cacheInterpolacaoPort.armazenar(chave, resultado);
            return resultado;
        } catch (IllegalArgumentException e) {
            ItemInterpolacaoResultado resultado = new ItemInterpolacaoResultado(
                    prazo,
                    null,
                    null,
                    "ERRO_FORA_INTERVALO",
                    e.getMessage()
            );
            cacheInterpolacaoPort.armazenar(chave, resultado);
            return resultado;
        }
    }
}
