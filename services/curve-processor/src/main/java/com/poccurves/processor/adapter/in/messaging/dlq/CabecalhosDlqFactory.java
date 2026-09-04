package com.poccurves.processor.adapter.in.messaging.dlq;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Monta os 9 cabeçalhos obrigatórios de toda mensagem encaminhada à
 * dead-letter, exatamente como nomeados em
 * contracts/events/topics.yaml ("CABEÇALHOS OBRIGATÓRIOS EM MENSAGENS
 * ENCAMINHADAS À DLQ"): x-dlq-reason, x-dlq-detail, x-correlation-id,
 * x-event-id, x-original-topic, x-original-partition, x-original-offset,
 * x-failed-at, x-attempts.
 */
public final class CabecalhosDlqFactory {

    private final ObjectMapper objectMapper;

    public CabecalhosDlqFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Headers criar(ConsumerRecord<?, ?> record, Exception exception) {
        Headers headers = new RecordHeaders();

        adicionar(headers, "x-dlq-reason", MotivoDlqClassificador.classificar(exception));
        adicionar(headers, "x-dlq-detail", detalhe(exception));
        adicionar(headers, "x-correlation-id", extrairCampo(record, "correlationId"));
        adicionar(headers, "x-event-id", extrairCampo(record, "eventId"));
        adicionar(headers, "x-original-topic", record.topic());
        adicionar(headers, "x-original-partition", String.valueOf(record.partition()));
        adicionar(headers, "x-original-offset", String.valueOf(record.offset()));
        adicionar(headers, "x-failed-at", Instant.now().toString());
        adicionar(headers, "x-attempts", String.valueOf(tentativas(record)));

        return headers;
    }

    private String detalhe(Exception exception) {
        Throwable causa = exception.getCause() != null ? exception.getCause() : exception;
        return causa.getMessage() != null ? causa.getMessage() : causa.toString();
    }

    private String extrairCampo(ConsumerRecord<?, ?> record, String campo) {
        Object valor = record.value();
        if (!(valor instanceof String texto) || texto.isBlank()) {
            return "unknown";
        }
        try {
            JsonNode node = objectMapper.readTree(texto);
            JsonNode campoNode = node.get(campo);
            return (campoNode != null && !campoNode.isNull()) ? campoNode.asText() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Número da tentativa, do cabeçalho KafkaHeaders.DELIVERY_ATTEMPT (exige ContainerProperties.setDeliveryAttemptHeader(true)). */
    private int tentativas(ConsumerRecord<?, ?> record) {
        var header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
        if (header == null) {
            return 1;
        }
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private void adicionar(Headers headers, String nome, String valor) {
        headers.add(new RecordHeader(nome, valor.getBytes(StandardCharsets.UTF_8)));
    }
}
