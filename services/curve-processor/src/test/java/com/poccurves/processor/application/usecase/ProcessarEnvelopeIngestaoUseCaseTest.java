package com.poccurves.processor.application.usecase;
import com.poccurves.processor.application.exception.DatasetDesconhecidoException;
import com.poccurves.processor.application.exception.ParseFalhouException;
import com.poccurves.processor.application.model.DatasetParser;
import com.poccurves.processor.application.model.DatasetParserRegistry;
import com.poccurves.processor.application.model.ParseResult;
import com.poccurves.processor.application.port.BlobStorageReadPort;
import com.poccurves.processor.application.port.ExecucaoCurvaLeituraRepositoryPort;
import com.poccurves.processor.application.port.NormalizedEventPort;
import com.poccurves.processor.application.port.PontoDadoMercadoRepositoryPort;
import com.poccurves.processor.application.util.MetricasIngestao;

import com.poccurves.common.event.EventEnvelope;
import com.poccurves.common.event.EventSource;
import com.poccurves.common.event.PayloadKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa o roteamento por dataset e o parsing — extraídos de {@code IngestaoListener} para cá
 * (application) na migração para arquitetura hexagonal. Cobre a tarefa 8.6 do backlog
 * (dead-letter para dataset desconhecido e payload não parseável) no nível de unidade.
 */
class ProcessarEnvelopeIngestaoUseCaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final byte[] CONTEUDO_BLOB_TESTE = "<x/>".getBytes(StandardCharsets.UTF_8);

    private static String sha256Hex(byte[] conteudo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(conteudo);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private BlobStorageReadPort blobStorageReadPortFake() {
        BlobStorageReadPort mockPort = mock(BlobStorageReadPort.class);
        when(mockPort.existe("b3", "2026-08-21/teste.xml")).thenReturn(true);
        when(mockPort.baixar("b3", "2026-08-21/teste.xml")).thenReturn(CONTEUDO_BLOB_TESTE);
        return mockPort;
    }

    private EventEnvelope envelopeValido(String dataset) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sourceUrl", "file://teste");
        payload.put("encoding", "UTF-8");
        payload.put("contentHash", "sha256:" + sha256Hex(CONTEUDO_BLOB_TESTE));
        payload.put("sizeBytes", CONTEUDO_BLOB_TESTE.length);
        payload.put("blobContainer", "b3");
        payload.put("blobPath", "2026-08-21/teste.xml");

        return new EventEnvelope(
                UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
                UUID.fromString("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"),
                EventSource.B3,
                dataset,
                LocalDate.of(2026, 8, 21),
                Instant.parse("2026-08-21T18:00:00Z"),
                "1.0",
                PayloadKind.INDIVIDUAL_QUOTES,
                "lote-1",
                1,
                1,
                payload,
                null,
                null
        );
    }

    @Test
    void datasetDesconhecidoLancaExcecaoNomeadaSemChamarIngestaoService() throws Exception {
        DatasetParserRegistry registryVazio = new DatasetParserRegistry(List.of());
        IngestaoService ingestaoServiceMock = mock(IngestaoService.class);
        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registryVazio, ingestaoServiceMock, new MetricasIngestao(new SimpleMeterRegistry()),
                mock(ExecucaoCurvaLeituraRepositoryPort.class), mock(PontoDadoMercadoRepositoryPort.class),
                mock(PublicacaoCurvaService.class), mock(NormalizedEventPort.class), blobStorageReadPortFake());

        EventEnvelope envelope = envelopeValido("DATASET_QUE_NAO_EXISTE");

        assertThatThrownBy(() -> useCase.processar(envelope))
                .isInstanceOf(DatasetDesconhecidoException.class)
                .hasMessageContaining("DATASET_QUE_NAO_EXISTE");

        verify(ingestaoServiceMock, never()).processarBloco(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void payloadNaoParseavelLancaExcecaoNomeadaSemChamarIngestaoService() throws Exception {
        DatasetParser parserQueFalha = new DatasetParser() {
            @Override
            public String dataset() {
                return "DATASET_COM_PARSE_QUEBRADO";
            }

            @Override
            public ParseResult parse(byte[] conteudo, String encoding, LocalDate referenceDate) {
                return new ParseResult.Falha("conteúdo malformado de propósito", "diagnóstico de teste");
            }
        };
        DatasetParserRegistry registry = new DatasetParserRegistry(List.of(parserQueFalha));
        IngestaoService ingestaoServiceMock = mock(IngestaoService.class);
        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registry, ingestaoServiceMock, new MetricasIngestao(new SimpleMeterRegistry()),
                mock(ExecucaoCurvaLeituraRepositoryPort.class), mock(PontoDadoMercadoRepositoryPort.class),
                mock(PublicacaoCurvaService.class), mock(NormalizedEventPort.class), blobStorageReadPortFake());

        EventEnvelope envelope = envelopeValido("DATASET_COM_PARSE_QUEBRADO");

        assertThatThrownBy(() -> useCase.processar(envelope))
                .isInstanceOf(ParseFalhouException.class)
                .hasMessageContaining("conteúdo malformado de propósito");

        verify(ingestaoServiceMock, never()).processarBloco(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
