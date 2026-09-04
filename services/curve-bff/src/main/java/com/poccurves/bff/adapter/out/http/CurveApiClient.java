package com.poccurves.bff.adapter.out.http;

import com.poccurves.bff.application.CurveApiPort;
import com.poccurves.bff.dto.BffDtos.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Component
public class CurveApiClient implements CurveApiPort {

    private final RestClient restClient;

    public CurveApiClient(@Qualifier("curveApiClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public CatalogoResponse getCatalogo(String codigo, String modoOrigem, String estado, int pagina, int tamanho) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/curvas/definicoes")
                        .queryParamIfPresent("codigo", Optional.ofNullable(codigo))
                        .queryParamIfPresent("modoOrigem", Optional.ofNullable(modoOrigem))
                        .queryParamIfPresent("estado", Optional.ofNullable(estado))
                        .queryParam("pagina", pagina)
                        .queryParam("tamanhoPagina", tamanho)
                        .build())
                .retrieve()
                .body(CatalogoResponse.class);
    }

    public DefinicaoCurvaDTO getDefinicaoCurva(String codigo) {
        return restClient.get()
                .uri("/curvas/definicoes/{codigo}", codigo)
                .retrieve()
                .body(DefinicaoCurvaDTO.class);
    }

    public DefinicaoCurvaDTO criarDefinicaoCurva(String codigo, CriarOuAtualizarDefinicaoCurvaRequest req) {
        // Envia para o curve-api
        return restClient.post()
                .uri("/curvas/definicoes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(DefinicaoCurvaDTO.class);
    }

    public DefinicaoCurvaDTO atualizarDefinicaoCurva(String codigo, CriarOuAtualizarDefinicaoCurvaRequest req) {
        return restClient.put()
                .uri("/curvas/definicoes/{codigo}", codigo)
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(DefinicaoCurvaDTO.class);
    }

    public byte[] downloadModeloCarga(String codigo, String formato) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/curvas/definicoes/{codigo}/modelo-carga")
                        .queryParam("formato", formato)
                        .build(codigo))
                .retrieve()
                .body(byte[].class);
    }

    public Optional<CurvaViewerResponse> getCurvaPublicada(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            Integer versao,
            Instant asOf
    ) {
        try {
            CurvaViewerResponse resp = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/curvas/{codigo}")
                            .queryParam("dataReferencia", dataReferencia)
                            .queryParamIfPresent("momento", Optional.ofNullable(momento))
                            .queryParamIfPresent("versao", Optional.ofNullable(versao))
                            .queryParamIfPresent("asOf", Optional.ofNullable(asOf))
                            .build(codigo))
                    .retrieve()
                    .body(CurvaViewerResponse.class);
            return Optional.ofNullable(resp);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public ComparacaoResponse compararCurvas(ComparacaoCurvasRequest request) {
        return restClient.post()
                .uri("/curvas/comparacao")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ComparacaoResponse.class);
    }
}
