package com.poccurves.engine.application;
import com.poccurves.engine.domain.construcao.InsumoDI1;
import com.poccurves.engine.domain.construcao.ModeloCurva;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.versao.DefinicaoResolvida;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.OrigemVersao;
import com.poccurves.engine.domain.versao.ProcedenciaCurva;
import com.poccurves.engine.domain.versao.VersaoCurva;
import com.poccurves.engine.domain.versao.VersaoJaExisteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConstrucaoCurvaService {

    private static final Logger log = LoggerFactory.getLogger(ConstrucaoCurvaService.class);

    private final DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository;
    private final ModeloCurvaRepositoryPort modeloCurvaRepository;
    private final VersaoCurvaRepositoryPort versaoCurvaRepository;
    private final VerticeCurvaRepositoryPort verticeCurvaRepository;
    private final ProcedenciaCurvaRepositoryPort procedenciaCurvaRepository;
    private final InsumoDI1RepositoryPort insumoDI1Repository;
    private final ModeloConstrucaoResolver modeloConstrucaoResolver;
    private final JsonPort jsonPort;

    public ConstrucaoCurvaService(
            DefinicaoCurvaResolutionRepositoryPort definicaoCurvaResolutionRepository,
            ModeloCurvaRepositoryPort modeloCurvaRepository,
            VersaoCurvaRepositoryPort versaoCurvaRepository,
            VerticeCurvaRepositoryPort verticeCurvaRepository,
            ProcedenciaCurvaRepositoryPort procedenciaCurvaRepository,
            InsumoDI1RepositoryPort insumoDI1Repository,
            ModeloConstrucaoResolver modeloConstrucaoResolver,
            JsonPort jsonPort) {
        this.definicaoCurvaResolutionRepository = definicaoCurvaResolutionRepository;
        this.modeloCurvaRepository = modeloCurvaRepository;
        this.versaoCurvaRepository = versaoCurvaRepository;
        this.verticeCurvaRepository = verticeCurvaRepository;
        this.procedenciaCurvaRepository = procedenciaCurvaRepository;
        this.insumoDI1Repository = insumoDI1Repository;
        this.modeloConstrucaoResolver = modeloConstrucaoResolver;
        this.jsonPort = jsonPort;
    }

    @Transactional
    public Optional<UUID> construir(String curveCode, LocalDate referenceDate, String curveMomentStr, UUID runId, UUID executionId) {
        if (versaoCurvaRepository.buscarPorExecucaoCurvaId(executionId).isPresent()) {
            log.info("construção ignorada por idempotência: executionId={} já processado", executionId);
            return Optional.empty();
        }

        MomentoCurva curveMoment = MomentoCurva.valueOf(curveMomentStr);

        DefinicaoResolvida definicao = definicaoCurvaResolutionRepository
                .resolverVigente(curveCode, referenceDate)
                .orElseThrow(() -> new IllegalStateException(String.format("definição não encontrada ou sem versão vigente para curveCode=%s e referenceDate=%s", curveCode, referenceDate)));

        if (!"BOOTSTRAPPED".equals(definicao.modoOrigem())) {
            throw new IllegalStateException(String.format("Curva %s não pode ser construída pois está no modoOrigem=%s", curveCode, definicao.modoOrigem()));
        }

        if (definicao.modeloCurvaId() == null) {
            throw new IllegalStateException("nenhum modelo apontado para a curva " + curveCode);
        }

        List<ModeloCurva> modelosAtivos = modeloCurvaRepository.listarAtivos();
        ModeloCurva modelo = modelosAtivos.stream()
                .filter(m -> m.id().equals(definicao.modeloCurvaId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format("modelo inexistente ou desabilitado para a curva %s (modeloCurvaId=%s)", curveCode, definicao.modeloCurvaId())));

        Set<String> codigosJaVisitados = new HashSet<>();
        codigosJaVisitados.add(curveCode);
        verificarDependencias(definicao.dependeDe(), referenceDate, curveMoment, codigosJaVisitados);

        List<InsumoDI1> insumos = insumoDI1Repository.buscarInsumosDI1(definicao.vinculosFonte(), referenceDate);
        CurvaJuros curva = modeloConstrucaoResolver.construir(modelo, insumos);

        int numeroVersao = versaoCurvaRepository.proximoNumeroVersao(definicao.definicaoCurvaId(), referenceDate, curveMoment);
        VersaoCurva versao = VersaoCurva.criar(definicao.definicaoCurvaId(), definicao.versaoDefinicaoCurvaId(), referenceDate, curveMoment, numeroVersao, OrigemVersao.CALCULADA, executionId);

        try {
            versaoCurvaRepository.inserir(versao);
        } catch (VersaoJaExisteException e) {
            log.info("construção concorrente redundante para {}/{}/{}, ignorando", curveCode, referenceDate, curveMoment);
            return Optional.empty();
        }

        verticeCurvaRepository.inserirTodos(versao.id(), curva.vertices());

        String referenciasInsumo = null;
        if (!insumos.isEmpty()) {
            try {
                referenciasInsumo = jsonPort.toJson(insumos.stream().map(InsumoDI1::ticker).collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Falha ao serializar referenciasInsumo", e);
            }
        }

        ProcedenciaCurva procedencia = new ProcedenciaCurva(
                versao.id(), executionId, definicao.numeroVersaoDefinicao(),
                null, referenciasInsumo, null, null, null, null, null, null, null,
                definicao.modeloCurvaId()
        );
        procedenciaCurvaRepository.inserir(procedencia);

        log.info("Construção finalizada: curveCode={}, referenceDate={}, curveMoment={}, numeroVersao={}, quantidadeVertices={}",
                curveCode, referenceDate, curveMoment, numeroVersao, curva.vertices().size());

        return Optional.of(versao.id());
    }

    private void verificarDependencias(List<String> dependeDe, LocalDate referenceDate, MomentoCurva curveMoment, Set<String> codigosJaVisitados) {
        if (dependeDe == null || dependeDe.isEmpty()) {
            return;
        }
        for (String codDependencia : dependeDe) {
            if (!codigosJaVisitados.add(codDependencia)) {
                throw new IllegalStateException("Ciclo de dependência detectado na curva " + codDependencia);
            }
            DefinicaoResolvida defDependencia = definicaoCurvaResolutionRepository
                    .resolverVigente(codDependencia, referenceDate)
                    .orElseThrow(() -> new IllegalStateException(String.format("definição da dependência %s não encontrada ou sem versão vigente", codDependencia)));

            Optional<VersaoCurva> versaoDependencia = versaoCurvaRepository.buscarVersaoVigentePublicada(defDependencia.definicaoCurvaId(), referenceDate, curveMoment);
            if (versaoDependencia.isEmpty()) {
                throw new IllegalStateException(String.format("dependência ausente: %s na data %s", codDependencia, referenceDate));
            }
            verificarDependencias(defDependencia.dependeDe(), referenceDate, curveMoment, codigosJaVisitados);
        }
    }
}
