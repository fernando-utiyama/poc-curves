package com.poccurves.processor.application;
import com.poccurves.processor.domain.ingestao.DatasetDesconhecidoException;
import com.poccurves.processor.domain.parsing.DatasetParser;
import com.poccurves.processor.domain.parsing.DatasetParserRegistry;
import com.poccurves.processor.domain.parsing.ParseFalhouException;
import com.poccurves.processor.domain.parsing.ParseResult;

import com.poccurves.common.event.EventEnvelope;
import com.poccurves.common.event.EventSource;
import com.poccurves.common.event.PayloadKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testa o roteamento por dataset e o parsing — extraídos de {@code IngestaoListener} para cá
 * (application) na migração para arquitetura hexagonal. Cobre a tarefa 8.6 do backlog
 * (dead-letter para dataset desconhecido e payload não parseável) no nível de unidade.
 */
class ProcessarEnvelopeIngestaoUseCaseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventEnvelope envelopeValido(String dataset) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("sourceUrl", "file://teste");
        payload.put("encoding", "UTF-8");
        payload.put("contentHash", "sha256:" + "a".repeat(64));
        payload.put("sizeBytes", 10);
        var records = payload.putArray("records");
        records.addObject().put("raw", "<x/>");

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
    void datasetDesconhecidoLancaExcecaoNomeadaSemChamarIngestaoService() {
        DatasetParserRegistry registryVazio = new DatasetParserRegistry(List.of());
        IngestaoService ingestaoServiceMock = mock(IngestaoService.class);
        ProcessarEnvelopeIngestaoUseCase useCase = new ProcessarEnvelopeIngestaoUseCase(
                registryVazio, ingestaoServiceMock, new MetricasIngestao(new SimpleMeterRegistry()),
                mock(ExecucaoCurvaLeituraRepositoryPort.class), mock(PontoDadoMercadoRepositoryPort.class),
                mock(PublicacaoCurvaService.class), mock(NormalizedEventPort.class));

        EventEnvelope envelope = envelopeValido("DATASET_QUE_NAO_EXISTE");

        assertThatThrownBy(() -> useCase.processar(envelope))
                .isInstanceOf(DatasetDesconhecidoException.class)
                .hasMessageContaining("DATASET_QUE_NAO_EXISTE");

        verify(ingestaoServiceMock, never()).processarBloco(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void payloadNaoParseavelLancaExcecaoNomeadaSemChamarIngestaoService() {
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
                mock(PublicacaoCurvaService.class), mock(NormalizedEventPort.class));

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
