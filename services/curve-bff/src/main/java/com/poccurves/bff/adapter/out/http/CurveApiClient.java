package com.poccurves.bff.adapter.out.http;

import com.poccurves.bff.application.CurveApiPort;
import com.poccurves.bff.dto.BffDtos.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class CurveApiClient implements CurveApiPort {

    private final RestClient restClient;

    public CurveApiClient(@Qualifier("curveApiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CatalogoResponse getCatalogo() {
        return restClient.get()
                .uri("/curvas")
                .retrieve()
                .body(CatalogoResponse.class);
    }

    @Override
    public Optional<CurvaMercadoDTO> getCurva(String ticker) {
        try {
            return Optional.ofNullable(
                    restClient.get()
                            .uri("/curvas/{ticker}", ticker)
                            .retrieve()
                            .body(CurvaMercadoDTO.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CurvaDadosDTO> getVertices(String ticker, LocalDate dataReferencia) {
        try {
            return Optional.ofNullable(
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/curvas/{ticker}/vertices")
                                    .queryParam("dataReferencia", dataReferencia)
                                    .build(ticker))
                            .retrieve()
                            .body(CurvaDadosDTO.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CurvaDadosDTO> getCurvaConstruida(String ticker, LocalDate dataReferencia) {
        try {
            return Optional.ofNullable(
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/curvas/{ticker}/curva")
                                    .queryParam("dataReferencia", dataReferencia)
                                    .build(ticker))
                            .retrieve()
                            .body(CurvaDadosDTO.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public ComparacaoResponse compararCurvas(ComparacaoCurvasRequest request) {
        return restClient.post()
                .uri("/curvas/comparacao")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ComparacaoResponse.class);
    }
}
