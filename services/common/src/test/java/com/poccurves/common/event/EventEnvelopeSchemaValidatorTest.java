package com.poccurves.common.event;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeSchemaValidatorTest {

    private ObjectMapper objectMapper;
    private EventEnvelopeSchemaValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new EventEnvelopeSchemaValidator();
    }

    @Test
    @DisplayName("Envelope válido com source B3 sem submittedBy/justification deve ser válido")
    void validB3EnvelopeWithoutManualFields() throws Exception {
        String json = """
                {
                  "eventId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "correlationId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
                  "source": "B3",
                  "dataset": "PR_DI1",
                  "referenceDate": "2026-08-21",
                  "producedAt": "2026-08-21T18:00:00Z",
                  "schemaVersion": "1.0",
                  "payloadKind": "INDIVIDUAL_QUOTES",
                  "loteId": "lote-abc-1",
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {}
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        assertThat(validator.isValid(node)).isTrue();
        assertThat(validator.validate(node)).isEmpty();
    }

    @Test
    @DisplayName("Envelope sem campo obrigatório loteId deve ser inválido")
    void invalidEnvelopeMissingRequiredFieldLoteId() throws Exception {
        String json = """
                {
                  "eventId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "correlationId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
                  "source": "B3",
                  "dataset": "PR_DI1",
                  "referenceDate": "2026-08-21",
                  "producedAt": "2026-08-21T18:00:00Z",
                  "schemaVersion": "1.0",
                  "payloadKind": "INDIVIDUAL_QUOTES",
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {}
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        assertThat(validator.isValid(node)).isFalse();
        List<Error> errors = validator.validate(node);
        assertThat(errors).isNotEmpty();
    }

    @Test
    @DisplayName("Envelope com source MANUAL incluindo submittedBy e justification deve ser válido")
    void validManualEnvelopeWithRequiredManualFields() throws Exception {
        String json = """
                {
                  "eventId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "correlationId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
                  "source": "MANUAL",
                  "dataset": "PR_DI1",
                  "referenceDate": "2026-08-21",
                  "producedAt": "2026-08-21T18:00:00Z",
                  "schemaVersion": "1.0",
                  "payloadKind": "INDIVIDUAL_QUOTES",
                  "loteId": "lote-abc-1",
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {},
                  "submittedBy": "operador.mesa",
                  "justification": "Ajuste manual de fechamento"
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        assertThat(validator.isValid(node)).isTrue();
        assertThat(validator.validate(node)).isEmpty();
    }

    @Test
    @DisplayName("Envelope com source MANUAL omitindo submittedBy e justification deve ser inválido")
    void invalidManualEnvelopeMissingManualFields() throws Exception {
        String json = """
                {
                  "eventId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
                  "correlationId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
                  "source": "MANUAL",
                  "dataset": "PR_DI1",
                  "referenceDate": "2026-08-21",
                  "producedAt": "2026-08-21T18:00:00Z",
                  "schemaVersion": "1.0",
                  "payloadKind": "INDIVIDUAL_QUOTES",
                  "loteId": "lote-abc-1",
                  "sequencia": 1,
                  "totalBlocos": 1,
                  "payload": {}
                }
                """;
        JsonNode node = objectMapper.readTree(json);

        assertThat(validator.isValid(node)).isFalse();
        List<Error> errors = validator.validate(node);
        assertThat(errors).isNotEmpty();
    }
}
