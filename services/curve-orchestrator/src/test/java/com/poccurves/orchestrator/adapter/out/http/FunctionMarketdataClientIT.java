package com.poccurves.orchestrator.adapter.out.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: verifica {@link FunctionMarketdataClient} contra
 * um function-marketdata-http de verdade (caminhos PUBLISHED/NO_DATA — exige o
 * container `function-marketdata-http` do compose de pé, porta 8091 exposta) e
 * contra um servidor HTTP falso local que devolve 502 (caminho FAILED — não
 * dá para forçar uma falha real de aquisição B3 de forma determinística, mas
 * o formato do corpo 502 já foi confirmado real via curl nesta sessão).
 */
class FunctionMarketdataClientIT {

    private HttpServer servidorFalso;

    @AfterEach
    void pararServidorFalso() {
        if (servidorFalso != null) {
            servidorFalso.stop(0);
        }
    }

    private static RestClient clienteParaPorta(int porta) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(30));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder()
                .baseUrl("http://localhost:" + porta)
                .requestFactory(factory)
                .build();
    }

    @Test
    void acionarRetornaNoDataParaDataFuturaSemPublicacao() {
        FunctionMarketdataClient client = new FunctionMarketdataClient(clienteParaPorta(8091));

        FunctionMarketdataClient.ResultadoAquisicao resultado = client.acionar(
                "BVBG.086", LocalDate.of(2035, 6, 15), "ROTINA", UUID.randomUUID());

        assertThat(resultado).isNotNull();
        assertThat(resultado.kind()).isEqualTo("NO_DATA");
        assertThat(resultado.motivo()).isNotBlank();
    }

    @Test
    void acionarDesserializaCorpoFailedEmRespostaHttp502SemLancarExcecao() throws Exception {
        // Servidor HTTP falso local reproduzindo exatamente o formato 502 real do
        // function-marketdata-http (confirmado via azure-function-handler.ts: status
        // 502 quando resultado.kind === 'FAILED', corpo {correlationId, resultado}).
        servidorFalso = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidorFalso.createContext("/acquire", exchange -> {
            String corpo = """
                    {"correlationId":"11111111-1111-1111-1111-111111111111","resultado":{"kind":"FAILED","motivo":"falha simulada de transporte","diagnostico":"ECONNRESET simulado pelo teste"}}
                    """;
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(502, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        servidorFalso.start();
        int porta = servidorFalso.getAddress().getPort();

        FunctionMarketdataClient client = new FunctionMarketdataClient(clienteParaPorta(porta));

        FunctionMarketdataClient.ResultadoAquisicao resultado = client.acionar(
                "BVBG.028", LocalDate.of(2026, 8, 21), "PRIORITARIA", UUID.randomUUID());

        assertThat(resultado).isNotNull();
        assertThat(resultado.kind()).isEqualTo("FAILED");
        assertThat(resultado.motivo()).isEqualTo("falha simulada de transporte");
        assertThat(resultado.diagnostico()).isEqualTo("ECONNRESET simulado pelo teste");
    }
}
