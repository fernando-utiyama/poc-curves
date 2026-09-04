package com.poccurves.processor.adapter.in.messaging;
import com.poccurves.processor.domain.parsing.EnvelopeInvalidoException;

import com.poccurves.common.event.EventEnvelope;
import com.poccurves.common.event.EventEnvelopeSchemaValidator;
import com.poccurves.processor.application.ProcessarEnvelopeIngestaoUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Testa só o que o listener ainda faz depois da migração para arquitetura hexagonal
 * (validação de envelope + desserialização + delegação ao caso de uso) — roteamento por
 * dataset, parsing e persistência agora são responsabilidade de
 * {@link ProcessarEnvelopeIngestaoUseCase}, testados separadamente em
 * {@code ProcessarEnvelopeIngestaoUseCaseTest} (application).
 */
class IngestaoListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventEnvelopeSchemaValidator envelopeValidator = new EventEnvelopeSchemaValidator();

    private String envelopeValidoJson(String dataset) {
        return """
                {
                  "eventId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "correlationId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
                  "source": "B3",
                  "dataset": "%s",
                  "referenceDate": "2026-08-21",
                  "producedAt": "2026-08-21T18:00:00Z",
                  "schemaVersion": "1.0",
                  "payloadKind": "INDIVIDUAL_QUOTES",
                  "loteId": "lote-1",
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {
                    "sourceUrl": "file://teste",
                    "encoding": "UTF-8",
                    "contentHash": "sha256:%s",
                    "sizeBytes": 10,
                    "records": [{"raw": "<x/>"}]
                  }
                }
                """.formatted(dataset, "a".repeat(64));
    }

    private ConsumerRecord<String, String> registro(String valorJson) {
        return new ConsumerRecord<>("marketdata.rotina.v1", 0, 0L, "chave", valorJson);
    }

    @Test
    void envelopeInvalidoLancaExcecaoNomeadaSemChamarOCasoDeUso() {
        ProcessarEnvelopeIngestaoUseCase useCaseMock = mock(ProcessarEnvelopeIngestaoUseCase.class);
        IngestaoListener listener = new IngestaoListener(objectMapper, envelopeValidator, useCaseMock);

        Acknowledgment ack = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = registro("{\"eventId\": \"nao-eh-um-uuid\"}");

        assertThatThrownBy(() -> listener.ouvirRotina(record, ack))
                .isInstanceOf(EnvelopeInvalidoException.class);

        verify(useCaseMock, never()).processar(any());
        verify(ack, never()).acknowledge();
    }

    @Test
    void envelopeValidoEhRepassadoAoCasoDeUsoEAckConfirmado() {
        ProcessarEnvelopeIngestaoUseCase useCaseMock = mock(ProcessarEnvelopeIngestaoUseCase.class);
        IngestaoListener listener = new IngestaoListener(objectMapper, envelopeValidator, useCaseMock);

        Acknowledgment ack = mock(Acknowledgment.class);
        ConsumerRecord<String, String> record = registro(envelopeValidoJson("BVBG_086"));

        listener.ouvirRotina(record, ack);

        verify(useCaseMock).processar(any(EventEnvelope.class));
        verify(ack).acknowledge();
    }
}
