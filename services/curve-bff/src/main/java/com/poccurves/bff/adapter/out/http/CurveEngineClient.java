package com.poccurves.bff.adapter.out.http;

import com.poccurves.bff.application.CurveEnginePort;
import com.poccurves.bff.dto.BffDtos.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class CurveEngineClient implements CurveEnginePort {

    private final RestClient restClient;

    public CurveEngineClient(@Qualifier("curveEngineClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public InterpolacaoResponse interpolar(String codigo, InterpolacaoRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/interpolacao")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(InterpolacaoResponse.class);
        } catch (Exception e) {
            // Em caso de indisponibilidade do motor, devolve resposta com status degradado
            return new InterpolacaoResponse(
                    codigo,
                    request.versao() != null ? request.versao() : 1,
                    "DESCONHECIDO",
                    Collections.emptyList(),
                    SecaoDegradadaDTO.erro("Serviço curve-engine indisponível: " + e.getMessage())
            );
        }
    }

    public ComparacaoResponse compararModelos(ComparacaoModelosRequest request) {
        return restClient.post()
                .uri("/api/v1/modelos/comparar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ComparacaoResponse.class);
    }

    public ImportarModeloResponse validarScriptGroovy(ImportarModeloGroovyRequest request) {
        return restClient.post()
                .uri("/api/v1/modelos/validar-groovy")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ImportarModeloResponse.class);
    }
}
