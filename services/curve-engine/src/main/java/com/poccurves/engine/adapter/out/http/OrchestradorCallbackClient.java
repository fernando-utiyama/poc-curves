package com.poccurves.engine.adapter.out.http;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.VersaoCurva;
import com.poccurves.engine.application.port.CurvaPublicadaEventPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class OrchestradorCallbackClient implements CurvaPublicadaEventPort {

    private static final Logger log = LoggerFactory.getLogger(OrchestradorCallbackClient.class);

    private final RestClient restClient;

    public OrchestradorCallbackClient(@Qualifier("curveOrchestratorClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public record ConclusaoSucessoPayload(
            UUID executionId,
            String status,
            String curveCode,
            UUID versionId,
            int versionNumber,
            int vertexCount
    ) {}

    public record ConclusaoFalhaPayload(
            UUID executionId,
            String status,
            String motivo
    ) {}

    @Override
    public void publicar(String curveCode, VersaoCurva versao, int vertexCount, List<ResultadoTeste> resultadosValidacao) {
        ConclusaoSucessoPayload payload = new ConclusaoSucessoPayload(
                versao.execucaoCurvaId(),
                "PUBLICADA",
                curveCode,
                versao.id(),
                versao.numeroVersao(),
                vertexCount
        );

        try {
            restClient.post()
                    .uri("/api/v1/execucoes/{executionId}/concluida", versao.execucaoCurvaId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Callback de conclusão publicado com sucesso para executionId={}, curveCode={}",
                    versao.execucaoCurvaId(), curveCode);
        } catch (Exception e) {
            // Limitação conhecida e aceitável desta revisão: a execução pode ficar presa em CONSTRUINDO
            // se o callback falhar depois da publicação ter sucesso no banco de dados. O mecanismo de
            // detecção é o orçamento de tempo/alerta preditivo já existente no orchestrator, sem retry aqui.
            log.error("Falha ao enviar callback de conclusão para executionId={}, curveCode={}",
                    versao.execucaoCurvaId(), curveCode, e);
        }
    }

    @Override
    public void notificarFalha(UUID executionId, String motivo) {
        ConclusaoFalhaPayload payload = new ConclusaoFalhaPayload(
                executionId,
                "ERRO",
                motivo
        );

        try {
            restClient.post()
                    .uri("/api/v1/execucoes/{executionId}/concluida", executionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Callback de falha enviado com sucesso para executionId={}", executionId);
        } catch (Exception e) {
            // Falha silenciosa: a falha de entrega do callback é apenas registrada em log para não mascarar a exceção de negócio.
            log.error("Falha ao enviar callback de falha para executionId={}", executionId, e);
        }
    }
}
