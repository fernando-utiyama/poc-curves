package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
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

    public CurvaViewerResponse obterCurvaViewer(String ticker, LocalDate dataReferencia, String momento) {
        // 1-3. Chamadas paralelas: vértices e curva construída (curve-api), última execução (curve-orchestrator).
        CompletableFuture<Optional<CurvaDadosDTO>> verticesFuture = CompletableFuture.supplyAsync(() ->
                curveApiClient.getVertices(ticker, dataReferencia)
        );
        CompletableFuture<Optional<CurvaDadosDTO>> curvaFuture = CompletableFuture.supplyAsync(() ->
                curveApiClient.getCurvaConstruida(ticker, dataReferencia)
        );
        CompletableFuture<Optional<ExecucaoResumoDTO>> execucaoFuture = CompletableFuture.supplyAsync(() ->
                orchestratorClient.getUltimaExecucaoCurva(ticker, dataReferencia, momento)
        );

        Optional<CurvaDadosDTO> verticesOpt;
        SecaoDegradadaDTO secaoVerticesStatus;
        try {
            long t0 = System.currentTimeMillis();
            verticesOpt = verticesFuture.get(5, TimeUnit.SECONDS);
            secaoVerticesStatus = SecaoDegradadaDTO.ok(System.currentTimeMillis() - t0);
        } catch (Exception e) {
            verticesOpt = Optional.empty();
            secaoVerticesStatus = SecaoDegradadaDTO.erro("Falha ao obter vértices do curve-api: " + e.getMessage());
        }

        Optional<CurvaDadosDTO> curvaOpt;
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

        boolean verticesPresentes = verticesOpt.isPresent() && !verticesOpt.get().pontos().isEmpty();
        boolean curvaPresente = curvaOpt.isPresent() && !curvaOpt.get().pontos().isEmpty();

        if (!verticesPresentes && !curvaPresente && execOpt.isEmpty()) {
            throw new NoSuchElementException("Curva '" + ticker + "' não encontrada para a data " + dataReferencia);
        }

        List<PontoCurvaDTO> vertices = verticesOpt.map(CurvaDadosDTO::pontos).orElse(Collections.emptyList());
        List<PontoCurvaDTO> curva = curvaOpt.map(CurvaDadosDTO::pontos).orElse(Collections.emptyList());

        return new CurvaViewerResponse(
                ticker,
                dataReferencia,
                vertices,
                curva,
                execOpt.orElse(null),
                secaoVerticesStatus,
                secaoCurvaStatus,
                secaoExecStatus
        );
    }
}
