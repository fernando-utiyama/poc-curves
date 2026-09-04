package com.poccurves.orchestrator.adapter.out.http;

import com.poccurves.orchestrator.application.FunctionMarketdataPort;
import com.poccurves.orchestrator.domain.IntegracaoIndisponivelException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cliente HTTP para o serviço {@code function-marketdata-http} (Node.js).
 * <p>
 * Executa o comando de aquisição remota {@code POST /acquire} e recebe
 * o reporte de resultado (PUBLISHED, NO_DATA, FAILED).
 */
@Component
public class FunctionMarketdataClient implements FunctionMarketdataPort {

    record AcquisicaoRequest(
            String dataset,
            String referenceDate,
            String faixa,
            String correlationId
    ) {}

    record AcquisicaoResponse(
            String correlationId,
            ResultadoAquisicao resultado
    ) {}

    private final RestClient restClient;

    public FunctionMarketdataClient(@Qualifier("functionMarketdataRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Aciona a aquisição do conjunto de dados no function-marketdata-http.
     * <p>
     * Resposta HTTP 502 representa um resultado de negócio do tipo {@code FAILED}
     * com corpo estruturado, sendo desserializado normalmente sem disparar exceção de transporte.
     *
     * @param dataset identificador do dataset (ex: "BVBG.086", "BVBG.028")
     * @param referenceDate data de referência
     * @param faixa faixa de execução ("ROTINA", "PRIORITARIA", "MASSA")
     * @param correlationId identificador de correlação da orquestração
     * @return resultado da aquisição com status (PUBLISHED, NO_DATA, FAILED) e metadados
     * @throws IntegracaoIndisponivelException em falha de transporte
     */
    @Override
    public ResultadoAquisicao acionar(String dataset, LocalDate referenceDate, String faixa, UUID correlationId) {
        AcquisicaoRequest corpo = new AcquisicaoRequest(
                dataset,
                referenceDate != null ? referenceDate.toString() : null,
                faixa,
                correlationId != null ? correlationId.toString() : null
        );

        try {
            AcquisicaoResponse resposta = restClient.post()
                    .uri("/acquire")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .onStatus(status -> status.value() == 502, (req, res) -> {
                        // 502 é FAILED com corpo válido, não erro de transporte de rede.
                    })
                    .body(AcquisicaoResponse.class);

            return resposta != null ? resposta.resultado() : null;
        } catch (RestClientException e) {
            throw new IntegracaoIndisponivelException(e.getMessage(), e);
        }
    }
}
