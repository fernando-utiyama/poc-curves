package com.poccurves.engine.application;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.validacao.Classificacao;
import com.poccurves.engine.domain.validacao.ContextoValidacao;
import com.poccurves.engine.domain.validacao.ResultadoValidacao;
import com.poccurves.engine.domain.validacao.TesteComparacaoCurvaImportada;
import com.poccurves.engine.domain.versao.DefinicaoResolvida;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.VersaoCurva;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public class PublicacaoCurvaService {

    private static final Logger log = LoggerFactory.getLogger(PublicacaoCurvaService.class);

    private final ConstrucaoCurvaService construcaoCurvaService;
    private final DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository;
    private final VerticeCurvaRepositoryPort verticeCurvaRepository;
    private final InsumoDI1RepositoryPort insumoDI1Repository;
    private final VersaoCurvaRepositoryPort versaoCurvaRepository;
    private final BateriaValidacaoService bateriaValidacaoService;
    private final CurvaPublicadaEventPort curvaPublicadaEventPublisher;
    private final PromocaoVersaoCurvaService promocaoVersaoCurvaService;

    public PublicacaoCurvaService(
            ConstrucaoCurvaService construcaoCurvaService,
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            VerticeCurvaRepositoryPort verticeCurvaRepository,
            InsumoDI1RepositoryPort insumoDI1Repository,
            VersaoCurvaRepositoryPort versaoCurvaRepository,
            BateriaValidacaoService bateriaValidacaoService,
            CurvaPublicadaEventPort curvaPublicadaEventPublisher,
            PromocaoVersaoCurvaService promocaoVersaoCurvaService) {
        this.construcaoCurvaService = construcaoCurvaService;
        this.definicaoCurvaResolutionRepository = definicaoCurvaResolutionRepository;
        this.verticeCurvaRepository = verticeCurvaRepository;
        this.insumoDI1Repository = insumoDI1Repository;
        this.versaoCurvaRepository = versaoCurvaRepository;
        this.bateriaValidacaoService = bateriaValidacaoService;
        this.curvaPublicadaEventPublisher = curvaPublicadaEventPublisher;
        this.promocaoVersaoCurvaService = promocaoVersaoCurvaService;
    }

    /**
     * Processa pedido de construção de curva.
     * <p>
     * O vínculo entre uma definição BOOTSTRAPPED e a sua definição IMPORTED irmã é resolvido por
     * {@code definicao_curva.codigo_curva_importada_irmao} (db/migration/V20) — uma referência
     * leve por código, sem FK, porque a definição IMPORTED pode não existir ainda quando a
     * BOOTSTRAPPED é cadastrada (e vice-versa). Quando o vínculo existe e há uma versão
     * IMPORTADA publicada para a mesma data/momento, ela alimenta
     * {@code curvaImportadaMesmaData} — ver {@link com.poccurves.engine.domain.TesteComparacaoCurvaImportada}.
     */
    public void processarPedidoConstrucao(String curveCode, LocalDate referenceDate, String curveMomentStr, UUID runId, UUID executionId) {
        Optional<UUID> versaoCurvaIdOpt = construcaoCurvaService.construir(curveCode, referenceDate, curveMomentStr, runId, executionId);

        if (versaoCurvaIdOpt.isEmpty()) {
            return;
        }

        UUID versaoCurvaId = versaoCurvaIdOpt.get();

        DefinicaoResolvida definicao = definicaoCurvaResolutionRepository
                .resolverVigente(curveCode, referenceDate)
                .orElseThrow(() -> new IllegalStateException(String.format("definição não encontrada para curveCode=%s e referenceDate=%s", curveCode, referenceDate)));

        MomentoCurva curveMoment = MomentoCurva.valueOf(curveMomentStr);

        CurvaJuros curvaConstruida = CurvaJuros.de(verticeCurvaRepository.buscarPorVersaoCurva(versaoCurvaId));
        var insumosOriginais = insumoDI1Repository.buscarInsumosDI1(definicao.vinculosFonte(), referenceDate);

        Optional<VersaoCurva> versaoAnteriorOpt = versaoCurvaRepository.buscarUltimaVersaoPublicadaAnterior(definicao.definicaoCurvaId(), referenceDate, curveMoment);
        Optional<CurvaJuros> curvaDiaAnterior = versaoAnteriorOpt.map(va -> CurvaJuros.de(verticeCurvaRepository.buscarPorVersaoCurva(va.id())));

        Optional<CurvaJuros> curvaImportadaMesmaData = Optional.ofNullable(definicao.codigoCurvaImportadaIrmao())
                .flatMap(definicaoCurvaResolutionRepository::resolverIdPorCodigo)
                .flatMap(idIrmao -> versaoCurvaRepository.buscarVersaoVigentePublicada(idIrmao, referenceDate, curveMoment))
                .map(va -> CurvaJuros.de(verticeCurvaRepository.buscarPorVersaoCurva(va.id())));

        ContextoValidacao contexto = new ContextoValidacao(curvaConstruida, insumosOriginais, curvaDiaAnterior, curvaImportadaMesmaData);

        BateriaValidacaoService.VereditoBateria veredito = bateriaValidacaoService.executar(versaoCurvaId, definicao.limitesValidacao(), contexto);

        if (veredito.aprovadaSemBloqueioReprovado()) {
            VersaoCurva versaoPromovida = promocaoVersaoCurvaService.promoverVersao(versaoCurvaId, definicao.definicaoCurvaId(), referenceDate, curveMoment);
            curvaPublicadaEventPublisher.publicar(curveCode, versaoPromovida, contexto.curvaConstruida().vertices().size(), veredito.resultados());
        } else {
            promocaoVersaoCurvaService.reprovarVersao(versaoCurvaId);
            long bloqueantesReprovados = veredito.resultados().stream()
                    .filter(r -> r.classificacao() == Classificacao.BLOQUEANTE && r.resultado() == ResultadoValidacao.REPROVADO)
                    .count();
            log.warn("Curva reprovada na validação: curveCode={}, referenceDate={}, curveMoment={}, bloqueantesReprovados={}", curveCode, referenceDate, curveMoment, bloqueantesReprovados);
        }
    }
}
