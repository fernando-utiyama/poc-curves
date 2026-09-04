package com.poccurves.engine.adapter.out.http;
import com.poccurves.engine.application.model.Classificacao;
import com.poccurves.engine.application.model.EstadoVersaoCurva;
import com.poccurves.engine.application.model.MomentoCurva;
import com.poccurves.engine.application.model.OrigemVersao;
import com.poccurves.engine.application.model.ResultadoTeste;
import com.poccurves.engine.application.model.ResultadoValidacao;
import com.poccurves.engine.application.model.VersaoCurva;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OrchestradorCallbackClientTest {

    private static final String BASE_URL = "http://curve-orchestrator.local";

    private MockRestServiceServer mockServer;
    private OrchestradorCallbackClient client;

    private final UUID execucaoId = UUID.randomUUID();
    private final UUID definicaoId = UUID.randomUUID();
    private final UUID versaoId = UUID.randomUUID();
    private final LocalDate dataRef = LocalDate.of(2026, 10, 10);

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new OrchestradorCallbackClient(restClient);
    }

    @Test
    void publicarChamaEndpointCertoComOCorpoEsperado() {
        VersaoCurva versao = VersaoCurva.reconstituir(
                versaoId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 2,
                OrigemVersao.CALCULADA, EstadoVersaoCurva.PUBLICADA, execucaoId, null);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/execucoes/" + execucaoId + "/concluida"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PUBLICADA"))
                .andExpect(jsonPath("$.curveCode").value("PRE_DI"))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.vertexCount").value(45))
                .andRespond(withSuccess());

        client.publicar("PRE_DI", versao, 45, List.of());

        mockServer.verify();
    }

    @Test
    void publicarInclueWarningsQuandoResultadosCarregamAvisoReprovado() {
        VersaoCurva versao = VersaoCurva.reconstituir(
                versaoId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1,
                OrigemVersao.CALCULADA, EstadoVersaoCurva.PUBLICADA, execucaoId, null);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/execucoes/" + execucaoId + "/concluida"))
                .andExpect(method(POST))
                .andRespond(withSuccess());

        ResultadoTeste aviso = new ResultadoTeste(
                "TESTE_SUAVIDADE", Classificacao.AVISO, ResultadoValidacao.REPROVADO, null, null, "acima do limite");

        assertThatCode(() -> client.publicar("PRE_DI", versao, 10, List.of(aviso))).doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void notificarFalhaChamaMesmoEndpointComStatusErroEMotivo() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/execucoes/" + execucaoId + "/concluida"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.status").value("ERRO"))
                .andExpect(jsonPath("$.motivo").value("insumo ausente"))
                .andRespond(withSuccess());

        client.notificarFalha(execucaoId, "insumo ausente");

        mockServer.verify();
    }

    @Test
    void publicarNaoPropagaExcecaoQuandoCallbackFalha() {
        VersaoCurva versao = VersaoCurva.reconstituir(
                versaoId, definicaoId, UUID.randomUUID(), dataRef, MomentoCurva.FECHAMENTO, 1,
                OrigemVersao.CALCULADA, EstadoVersaoCurva.PUBLICADA, execucaoId, null);

        mockServer.expect(requestTo(BASE_URL + "/api/v1/execucoes/" + execucaoId + "/concluida"))
                .andRespond(withServerError());

        assertThatCode(() -> client.publicar("PRE_DI", versao, 10, List.of())).doesNotThrowAnyException();

        mockServer.verify();
    }

    @Test
    void notificarFalhaNaoPropagaExcecaoQuandoCallbackFalha() {
        mockServer.expect(requestTo(BASE_URL + "/api/v1/execucoes/" + execucaoId + "/concluida"))
                .andRespond(withServerError());

        assertThatCode(() -> client.notificarFalha(execucaoId, "erro qualquer")).doesNotThrowAnyException();

        mockServer.verify();
    }
}
