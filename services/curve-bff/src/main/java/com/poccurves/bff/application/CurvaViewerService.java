package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CurvaViewerService {

    private final CurveApiPort curveApiClient;
    private final CurveOrchestratorPort orchestratorClient;

    public CurvaViewerService(CurveApiPort curveApiClient, CurveOrchestratorPort orchestratorClient) {
        this.curveApiClient = curveApiClient;
        this.orchestratorClient = orchestratorClient;
    }

    public CurvaViewerResponse obterCurvaViewer(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            Integer versao,
            Instant asOf
    ) {
        long startTotal = System.currentTimeMillis();

        // 1. Chamada paralela ao curve-api (curva, vértices, procedência, validação)
        CompletableFuture<Optional<CurvaViewerResponse>> curvaFuture = CompletableFuture.supplyAsync(() ->
                curveApiClient.getCurvaPublicada(codigo, dataReferencia, momento, versao, asOf)
        );

        // 2. Chamada paralela ao curve-orchestrator (última execução)
        CompletableFuture<Optional<ExecucaoResumoDTO>> execucaoFuture = CompletableFuture.supplyAsync(() ->
                orchestratorClient.getUltimaExecucaoCurva(codigo, dataReferencia, momento)
        );

        // Aguarda respostas com timeout
        Optional<CurvaViewerResponse> curvaOpt;
        SecaoDegradadaDTO secaoCurvaStatus;
        try {
            long t0 = System.currentTimeMillis();
            curvaOpt = curvaFuture.get(5, TimeUnit.SECONDS);
            secaoCurvaStatus = SecaoDegradadaDTO.ok(System.currentTimeMillis() - t0);
        } catch (Exception e) {
            curvaOpt = Optional.empty();
            secaoCurvaStatus = SecaoDegradadaDTO.erro("Falha ao obter curva do curve-api: " + e.getMessage());
        }

        Optional<ExecucaoResumoDTO> execOpt;
        SecaoDegradadaDTO secaoExecStatus;
        try {
            long t0 = System.currentTimeMillis();
            execOpt = execucaoFuture.get(5, TimeUnit.SECONDS);
            secaoExecStatus = SecaoDegradadaDTO.ok(System.currentTimeMillis() - t0);
        } catch (Exception e) {
            execOpt = Optional.empty();
            secaoExecStatus = SecaoDegradadaDTO.erro("Falha ao obter execução do curve-orchestrator: " + e.getMessage());
        }

        if (curvaOpt.isEmpty() && execOpt.isEmpty()) {
            throw new NoSuchElementException("Curva '" + codigo + "' não encontrada para a data " + dataReferencia);
        }

        if (curvaOpt.isPresent()) {
            CurvaViewerResponse base = curvaOpt.get();
            return new CurvaViewerResponse(
                    base.versaoCurvaId(),
                    base.codigoCurva(),
                    base.nomeCurva(),
                    base.modoOrigem(),
                    base.dataReferencia(),
                    base.momento(),
                    base.numeroVersao(),
                    base.estadoVersao(),
                    base.origemVersao(),
                    base.isVersaoCorrente(),
                    base.razaoSelecaoVersao(),
                    base.publicadoEm(),
                    base.vertices() != null ? base.vertices() : Collections.emptyList(),
                    base.procedencia(),
                    base.validacao(),
                    execOpt.orElse(null),
                    base.vertices() != null ? SecaoDegradadaDTO.ok(10L) : SecaoDegradadaDTO.erro("Vértices indisponíveis"),
                    base.procedencia() != null ? SecaoDegradadaDTO.ok(5L) : SecaoDegradadaDTO.erro("Procedência indisponível"),
                    base.validacao() != null ? SecaoDegradadaDTO.ok(5L) : SecaoDegradadaDTO.erro("Validação indisponível"),
                    secaoExecStatus
            );
        } else {
            // Curva ainda não publicada, mas execução em andamento/falha disponível
            ExecucaoResumoDTO exec = execOpt.get();
            return new CurvaViewerResponse(
                    null,
                    codigo.toUpperCase(),
                    codigo.toUpperCase(),
                    "BOOTSTRAPPED",
                    dataReferencia,
                    momento != null ? momento : "FECHAMENTO",
                    0,
                    "NAO_PUBLICADA",
                    "NENHUMA",
                    false,
                    "NENHUMA",
                    null,
                    Collections.emptyList(),
                    null,
                    null,
                    exec,
                    SecaoDegradadaDTO.erro("Curva ainda não publicada na data"),
                    SecaoDegradadaDTO.erro("Curva ainda não publicada na data"),
                    SecaoDegradadaDTO.erro("Curva ainda não publicada na data"),
                    secaoExecStatus
            );
        }
    }
}
