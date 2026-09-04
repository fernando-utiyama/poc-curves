package com.poccurves.orchestrator.adapter.out.http;

import com.poccurves.orchestrator.application.CurveProcessorCargaManualPort;
import com.poccurves.orchestrator.domain.IntegracaoIndisponivelException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cliente HTTP para o endpoint interno de carga manual do {@code curve-processor}.
 * <p>
 * Executa {@code POST /interno/carga-manual} via multipart form-data.
 */
@Component
public class CurveProcessorCargaManualClient implements CurveProcessorCargaManualPort {

    private final RestClient restClient;

    public CurveProcessorCargaManualClient(@Qualifier("curveProcessorRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public RespostaCargaInterna carregar(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            String justificativa,
            String carregadoPor,
            UUID execucaoCurvaId,
            byte[] arquivoBytes,
            String nomeArquivo
    ) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("codigo", codigo);
        body.add("dataReferencia", dataReferencia.toString());
        body.add("momento", momento);
        body.add("justificativa", justificativa);
        body.add("carregadoPor", carregadoPor);
        body.add("execucaoCurvaId", execucaoCurvaId.toString());

        ByteArrayResource fileResource = new ByteArrayResource(arquivoBytes) {
            @Override
            public String getFilename() {
                return nomeArquivo;
            }
        };
        body.add("arquivo", fileResource);

        try {
            return restClient.post()
                    .uri("/interno/carga-manual")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(RespostaCargaInterna.class);
        } catch (RestClientException e) {
            throw new IntegracaoIndisponivelException(e.getMessage(), e);
        }
    }
}
