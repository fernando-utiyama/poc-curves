package com.poccurves.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Record que representa o envelope de evento padronizado do sistema,
 * espelhando o contrato contracts/events/envelope.schema.json.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        UUID eventId,
        UUID correlationId,
        EventSource source,
        String dataset,
        LocalDate referenceDate,
        Instant producedAt,
        String schemaVersion,
        PayloadKind payloadKind,
        String loteId,
        int sequencia,
        int totalBlocos,
        JsonNode payload,
        String submittedBy,
        String justification
) {
    private static final Pattern SCHEMA_VERSION_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]+$");

    public EventEnvelope {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId não pode ser nulo");
        }
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId não pode ser nulo");
        }
        if (source == null) {
            throw new IllegalArgumentException("source não pode ser nulo");
        }
        if (dataset == null || dataset.isBlank()) {
            throw new IllegalArgumentException("dataset não pode ser nulo ou vazio");
        }
        if (referenceDate == null) {
            throw new IllegalArgumentException("referenceDate não pode ser nulo");
        }
        if (producedAt == null) {
            throw new IllegalArgumentException("producedAt não pode ser nulo");
        }
        if (schemaVersion == null || !SCHEMA_VERSION_PATTERN.matcher(schemaVersion).matches()) {
            throw new IllegalArgumentException("schemaVersion deve seguir o padrão '^[0-9]+\\.[0-9]+$'");
        }
        if (payloadKind == null) {
            throw new IllegalArgumentException("payloadKind não pode ser nulo");
        }
        if (loteId == null || loteId.isBlank()) {
            throw new IllegalArgumentException("loteId não pode ser nulo ou vazio");
        }
        if (sequencia < 1) {
            throw new IllegalArgumentException("sequencia deve ser >= 1");
        }
        if (totalBlocos < 1) {
            throw new IllegalArgumentException("totalBlocos deve ser >= 1");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload não pode ser nulo");
        }
        if (source == EventSource.MANUAL) {
            if (submittedBy == null || submittedBy.isBlank()) {
                throw new IllegalArgumentException("submittedBy não pode ser nulo ou vazio quando source é MANUAL");
            }
            if (justification == null || justification.isBlank()) {
                throw new IllegalArgumentException("justification não pode ser nulo ou vazio quando source é MANUAL");
            }
        }
    }
}
