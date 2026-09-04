package com.poccurves.orchestrator.application;

import com.poccurves.orchestrator.application.DefinicaoCurvaConsultaRepositoryPort;
import com.poccurves.orchestrator.application.FunctionMarketdataPort.ResultadoAquisicao;
import com.poccurves.orchestrator.domain.DefinicaoConsumidora;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.Faixa;
import com.poccurves.orchestrator.domain.IntegracaoIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AquisicaoExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AquisicaoExecutionService.class);

    private final FunctionMarketdataPort functionMarketdataClient;
    private final DefinicaoCurvaConsultaRepositoryPort definicaoCurvaConsultaRepository;
    private final BuildRequestPort buildRequestPublisher;
    private final int maxRetentativas;

    public AquisicaoExecutionService(
            FunctionMarketdataPort functionMarketdataClient,
            DefinicaoCurvaConsultaRepositoryPort definicaoCurvaConsultaRepository,
            BuildRequestPort buildRequestPublisher,
            int maxRetentativas
    ) {
        this.functionMarketdataClient = functionMarketdataClient;
        this.definicaoCurvaConsultaRepository = definicaoCurvaConsultaRepository;
        this.buildRequestPublisher = buildRequestPublisher;
        this.maxRetentativas = maxRetentativas;
    }

    public record ExecutionResult(ResultadoAquisicao resultado, String progressoStatus, String progressoMensagem, boolean erroTransporte) {}

    public ExecutionResult acionarFeederEEncadear(
            ExecucaoCurva execucao,
            String conjuntoDados,
            LocalDate dataReferencia,
            Faixa faixa,
            UUID correlationIdLote
    ) {
        MDC.put("correlacaoId", correlationIdLote.toString());
        MDC.put("alvo", conjuntoDados);
        MDC.put("dataReferencia", dataReferencia.toString());

        try {
            ResultadoAquisicao resultado;
            try {
                resultado = chamarComRetentativa(execucao, conjuntoDados, dataReferencia, faixa, correlationIdLote);
            } catch (IntegracaoIndisponivelException e) {
                log.error("Esgotadas as retentativas ao acionar o feeder: {}", e.getMessage());
                execucao.falhar("FEEDER_INDISPONIVEL", "Falha de transporte ao acionar o feeder: " + e.getMessage());
                return new ExecutionResult(null, "FALHA", "Feeder indisponível: " + e.getMessage(), true);
            }

            if (resultado == null) {
                execucao.falhar("FEEDER_RESPOSTA_NULA", "Resposta nula retornada pelo feeder.");
                return new ExecutionResult(null, "FALHA", "Resposta nula do feeder.", false);
            }

            switch (resultado.kind()) {
                case "PUBLISHED" -> {
                    List<DefinicaoConsumidora> consumidoras =
                            definicaoCurvaConsultaRepository.buscarDefinicoesBootstrappedQueConsomem(
                                    conjuntoDados, dataReferencia);

                    if (consumidoras.isEmpty()) {
                        return new ExecutionResult(
                                resultado,
                                "EM_PROCESSAMENTO",
                                "Aquisição publicada com sucesso (loteId=" + resultado.loteId() + ", "
                                        + resultado.totalBlocos() + " blocos). Nenhuma definição de curva BOOTSTRAPPED consome este conjunto de dados hoje.",
                                false
                        );
                    } else {
                        List<String> publicadasComSucesso = new ArrayList<>();
                        List<String> falharamAoPublicar = new ArrayList<>();

                        for (DefinicaoConsumidora consumidora : consumidoras) {
                            try {
                                buildRequestPublisher.publicar(
                                        consumidora.codigo(),
                                        dataReferencia,
                                        execucao.momentoCurva().name(),
                                        correlationIdLote,
                                        execucao.id(),
                                        consumidora.horarioLimitePublicacao()
                                );
                                publicadasComSucesso.add(consumidora.codigo());
                            } catch (Exception e) {
                                falharamAoPublicar.add(consumidora.codigo() + " (" + e.getMessage() + ")");
                            }
                        }

                        if (!publicadasComSucesso.isEmpty()) {
                            execucao.iniciarConstrucao();
                        }

                        StringBuilder mensagemPublished = new StringBuilder(
                                "Aquisição publicada com sucesso (loteId=" + resultado.loteId() + ", "
                                        + resultado.totalBlocos() + " blocos).");
                        if (!publicadasComSucesso.isEmpty()) {
                            mensagemPublished.append(" Pedido de construção emitido para: ")
                                    .append(String.join(", ", publicadasComSucesso)).append(".");
                        }
                        if (!falharamAoPublicar.isEmpty()) {
                            mensagemPublished.append(" Falha ao emitir pedido de construção para: ")
                                    .append(String.join(", ", falharamAoPublicar)).append(".");
                        }

                        return new ExecutionResult(resultado, "EM_PROCESSAMENTO", mensagemPublished.toString(), false);
                    }
                }
                case "NO_DATA" -> {
                    return new ExecutionResult(resultado, "SEM_DADO", resultado.motivo(), false);
                }
                case "FAILED" -> {
                    execucao.falhar("FEEDER_ACQUISITION_FAILED", resultado.motivo() + " | " + resultado.diagnostico());
                    return new ExecutionResult(resultado, "FALHA", resultado.motivo(), false);
                }
                default -> throw new IllegalStateException("kind de resultado desconhecido: " + resultado.kind());
            }
        } finally {
            MDC.clear();
        }
    }

    private ResultadoAquisicao chamarComRetentativa(
            ExecucaoCurva execucao,
            String conjuntoDados,
            LocalDate dataReferencia,
            Faixa faixa,
            UUID correlationIdLote
    ) {
        int tentativa = 1;
        long waitTimeMs = 1000;

        while (true) {
            try {
                return functionMarketdataClient.acionar(
                        conjuntoDados,
                        dataReferencia,
                        faixa.name(),
                        correlationIdLote
                );
            } catch (IntegracaoIndisponivelException e) {
                execucao.incrementarTentativa();

                if (tentativa >= maxRetentativas) {
                    throw e;
                }

                log.warn("Falha transitória ao acionar o feeder, tentativa {} de {}, tentando de novo em {}ms", tentativa, maxRetentativas, waitTimeMs);

                try {
                    Thread.sleep(waitTimeMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }

                tentativa++;
                waitTimeMs *= 2;
            }
        }
    }
}
