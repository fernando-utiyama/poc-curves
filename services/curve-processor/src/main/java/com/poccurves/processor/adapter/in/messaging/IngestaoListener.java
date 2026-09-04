package com.poccurves.processor.adapter.in.messaging;
import com.poccurves.processor.domain.parsing.EnvelopeInvalidoException;

import com.poccurves.common.event.EventEnvelope;
import com.poccurves.common.event.EventEnvelopeSchemaValidator;
import com.poccurves.processor.application.ProcessarEnvelopeIngestaoUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consome as três faixas de ingestão bruta, valida o envelope contra o
 * contrato e desserializa a mensagem crua para {@link EventEnvelope} — toda
 * decisão de negócio (roteamento por dataset, parsing, persistência,
 * publicação de eventos derivados) mora em
 * {@link ProcessarEnvelopeIngestaoUseCase} (application), extraída daqui na
 * migração para arquitetura hexagonal. Falha de negócio (envelope inválido,
 * dataset desconhecido, parsing malformado) lança uma exceção nomeada — o
 * {@code DefaultErrorHandler} (KafkaConsumerConfig) as classifica como não
 * retentáveis e manda direto para a dead-letter; nunca chama
 * {@link Acknowledgment#acknowledge()} nesse caminho, e o próprio container
 * avança o offset via o recuperador (D11c: nunca retentativa infinita).
 */
@Component
public class IngestaoListener {

    private static final Logger log = LoggerFactory.getLogger(IngestaoListener.class);

    private final ObjectMapper objectMapper;
    private final EventEnvelopeSchemaValidator envelopeValidator;
    private final ProcessarEnvelopeIngestaoUseCase processarEnvelopeIngestaoUseCase;

    public IngestaoListener(
            ObjectMapper objectMapper,
            EventEnvelopeSchemaValidator envelopeValidator,
            ProcessarEnvelopeIngestaoUseCase processarEnvelopeIngestaoUseCase
    ) {
        this.objectMapper = objectMapper;
        this.envelopeValidator = envelopeValidator;
        this.processarEnvelopeIngestaoUseCase = processarEnvelopeIngestaoUseCase;
    }

    @KafkaListener(
            topics = "marketdata.rotina.v1",
            groupId = "curve-processor-rotina",
            containerFactory = "kafkaListenerContainerFactoryRotina")
    public void ouvirRotina(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processarRecord(record);
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "marketdata.prioritaria.v1",
            groupId = "curve-processor-prioritaria",
            containerFactory = "kafkaListenerContainerFactoryPrioritaria")
    public void ouvirPrioritaria(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processarRecord(record);
        ack.acknowledge();
    }

    @KafkaListener(
            topics = "marketdata.massa.v1",
            groupId = "curve-processor-massa",
            containerFactory = "kafkaListenerContainerFactoryMassa")
    public void ouvirMassa(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processarRecord(record);
        ack.acknowledge();
    }

    private void processarRecord(ConsumerRecord<String, String> record) {
        JsonNode envelopeJson = lerJson(record.value());

        if (!envelopeValidator.isValid(envelopeJson)) {
            var erros = envelopeValidator.validate(envelopeJson);
            throw new EnvelopeInvalidoException("envelope não conforme ao schema: " + erros);
        }

        EventEnvelope envelope = converterEnvelope(envelopeJson);

        // Propagação de correlationId (tarefa 7.1) e log estruturado (7.2):
        // MDC fica visível em todo log emitido daqui até o finally, via
        // logging.structured.format.console=logstash (application.yml).
        MDC.put("correlationId", envelope.correlationId().toString());
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("dataset", envelope.dataset());
        MDC.put("payloadKind", envelope.payloadKind().name());
        MDC.put("referenceDate", envelope.referenceDate().toString());
        try {
            processarEnvelopeIngestaoUseCase.processar(envelope);
        } finally {
            MDC.clear();
        }
    }

    private JsonNode lerJson(String valor) {
        try {
            return objectMapper.readTree(valor);
        } catch (Exception e) {
            throw new EnvelopeInvalidoException("payload da mensagem não é um JSON válido: " + e.getMessage());
        }
    }

    private EventEnvelope converterEnvelope(JsonNode envelopeJson) {
        try {
            return objectMapper.treeToValue(envelopeJson, EventEnvelope.class);
        } catch (Exception e) {
            throw new EnvelopeInvalidoException("envelope válido contra o schema, mas não mapeável para EventEnvelope: " + e.getMessage());
        }
    }
}
