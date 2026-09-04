package com.poccurves.bff.adapter.out.http;

import com.poccurves.bff.application.CurveOrchestratorPort;
import com.poccurves.bff.dto.BffDtos.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.*;

@Component
public class CurveOrchestratorClient implements CurveOrchestratorPort {

    private final RestClient restClient;

    public CurveOrchestratorClient(@Qualifier("curveOrchestratorClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public DisparoManualResponse disparoManual(DisparoManualRequest request) {
        return restClient.post()
                .uri("/api/v1/disparos/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DisparoManualResponse.class);
    }

    public BackfillResponse backfill(BackfillRequest request) {
        return restClient.post()
                .uri("/api/v1/disparos/backfill")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(BackfillResponse.class);
    }

    public ExecucoesResponse getExecucoes(
            String codigoCurva,
            LocalDate dataReferencia,
            String estado,
            int pagina,
            int tamanho
    ) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/execucoes")
                            .queryParamIfPresent("codigoCurva", Optional.ofNullable(codigoCurva))
                            .queryParamIfPresent("dataReferencia", Optional.ofNullable(dataReferencia))
                            .queryParamIfPresent("estado", Optional.ofNullable(estado))
                            .queryParam("pagina", pagina)
                            .queryParam("tamanho", tamanho)
                            .build())
                    .retrieve()
                    .body(ExecucoesResponse.class);
        } catch (Exception e) {
            return new ExecucoesResponse(Collections.emptyList(), 0, pagina, 0);
        }
    }

    public Optional<ExecucaoResumoDTO> getUltimaExecucaoCurva(String codigoCurva, LocalDate dataReferencia, String momento) {
        try {
            var exec = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/execucoes/ultima")
                            .queryParam("codigoCurva", codigoCurva)
                            .queryParam("dataReferencia", dataReferencia)
                            .queryParamIfPresent("momento", Optional.ofNullable(momento))
                            .build())
                    .retrieve()
                    .body(ExecucaoResumoDTO.class);
            return Optional.ofNullable(exec);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public PendenciasSumarioResponse getPendenciasSumario() {
        try {
            return restClient.get()
                    .uri("/api/v1/pendencias/dlq")
                    .retrieve()
                    .body(PendenciasSumarioResponse.class);
        } catch (Exception e) {
            return new PendenciasSumarioResponse(Collections.emptyList(), 0, 0);
        }
    }

    public PendenciasDetalheResponse getPendenciasDetalhe(UUID grupoId, int pagina, int tamanho) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/pendencias/dlq/grupos/{grupoId}")
                        .queryParam("pagina", pagina)
                        .queryParam("tamanho", tamanho)
                        .build(grupoId))
                .retrieve()
                .body(PendenciasDetalheResponse.class);
    }

    public AcaoPendenciaResponse reprocessarPendencias(UUID grupoId, UUID pendenciaId) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/pendencias/dlq/reprocessar")
                        .queryParamIfPresent("grupoId", Optional.ofNullable(grupoId))
                        .queryParamIfPresent("pendenciaId", Optional.ofNullable(pendenciaId))
                        .build())
                .retrieve()
                .body(AcaoPendenciaResponse.class);
    }

    public AcaoPendenciaResponse descartarPendencias(UUID grupoId, UUID pendenciaId, String justificativa) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/pendencias/dlq/descartar")
                        .queryParamIfPresent("grupoId", Optional.ofNullable(grupoId))
                        .queryParamIfPresent("pendenciaId", Optional.ofNullable(pendenciaId))
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new DescartarPendenciaRequest(justificativa))
                .retrieve()
                .body(AcaoPendenciaResponse.class);
    }

    public CargaManualResponse cargaManualCurva(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            String justificativa,
            byte[] fileBytes,
            String fileName
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("codigo", codigo);
        body.add("dataReferencia", dataReferencia.toString());
        body.add("momento", momento);
        body.add("justificativa", justificativa);

        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        body.add("arquivo", fileResource);

        return restClient.post()
                .uri("/api/v1/curvas/carga-manual")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(CargaManualResponse.class);
    }
}
