package com.poccurves.processor.domain;

/**
 * Envelope recebido não é conforme a contracts/events/envelope.schema.json,
 * ou o payload não é conforme ao schema esperado para o payloadKind
 * declarado. Falha permanente — nunca vale retentar, vai direto para a
 * dead-letter (classificada como não retentável em KafkaConsumerConfig).
 */
public final class EnvelopeInvalidoException extends RuntimeException {

    public EnvelopeInvalidoException(String motivo) {
        super(motivo);
    }
}
