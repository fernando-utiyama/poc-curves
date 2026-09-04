package com.poccurves.orchestrator.adapter.out.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CurveEngineClientTest {

    private static final String BASE_URL = "http://localhost:8083";

    private MockRestServiceServer mockServer;
    private CurveEngineClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new CurveEngineClient(restClient);
    }

    @Test
    void publicarChamaEndpointConstrucoesComCincoCamposEsperadosESemPublishDeadline() {
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        LocalDate dataReferencia = LocalDate.of(2026, 8, 25);
        LocalTime horarioLimite = LocalTime.of(18, 30);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/construcoes"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.curveCode").value("PRE_DI"))
                .andExpect(jsonPath("$.referenceDate").value("2026-08-25"))
                .andExpect(jsonPath("$.curveMoment").value("FECHAMENTO"))
                .andExpect(jsonPath("$.runId").value(runId.toString()))
                .andExpect(jsonPath("$.executionId").value(executionId.toString()))
                .andExpect(jsonPath("$.publishDeadline").doesNotExist())
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.publicar("PRE_DI", dataReferencia, "FECHAMENTO", runId, executionId, horarioLimite);

        mockServer.verify();
    }

    @Test
    void publicarPropagaExcecaoQuandoServidorRespondeErro() {
        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        LocalDate dataReferencia = LocalDate.of(2026, 8, 25);
        LocalTime horarioLimite = LocalTime.of(18, 30);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/construcoes"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.publicar("PRE_DI", dataReferencia, "FECHAMENTO", runId, executionId, horarioLimite))
                .isInstanceOf(RestClientException.class);

        mockServer.verify();
    }
}
