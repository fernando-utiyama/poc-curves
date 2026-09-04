package com.poccurves.engine.adapter.in.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

import com.poccurves.engine.application.PublicacaoCurvaService;

@Component
public class ConstrucaoRequestListener {

    private static final Logger log = LoggerFactory.getLogger(ConstrucaoRequestListener.class);

    private final PublicacaoCurvaService publicacaoCurvaService;
    private final ObjectMapper objectMapper;

    public ConstrucaoRequestListener(PublicacaoCurvaService publicacaoCurvaService, ObjectMapper objectMapper) {
        this.publicacaoCurvaService = publicacaoCurvaService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "curve.build.requested.v1", containerFactory = "kafkaListenerContainerFactory")
    public void ouvir(ConsumerRecord<String, String> record, Acknowledgment ack) throws Exception {
        log.info("Recebida requisição de construção de curva");

        JsonNode payload = objectMapper.readTree(record.value());

        String curveCode = payload.get("curveCode").asText();
        String referenceDateStr = payload.get("referenceDate").asText();
        String curveMoment = payload.get("curveMoment").asText();
        String runIdStr = payload.get("runId").asText();
        String executionIdStr = payload.get("executionId").asText();

        LocalDate referenceDate = LocalDate.parse(referenceDateStr);
        UUID runId = UUID.fromString(runIdStr);
        UUID executionId = UUID.fromString(executionIdStr);

        // DIAGNÓSTICO 12.1 (temporário): try/catch(Throwable) só para forçar log visível de
        // QUALQUER falha, incluindo Error (que o DefaultErrorHandler do Spring Kafka NÃO captura,
        // só Exception) -- duas mensagens reais já foram recebidas sem nenhum log de sucesso nem
        // de erro aparecendo, então a causa pode ser algo que o handler padrão não vê.
        try {
            publicacaoCurvaService.processarPedidoConstrucao(curveCode, referenceDate, curveMoment, runId, executionId);
            log.info("Requisição processada com sucesso: curveCode={}, executionId={}", curveCode, executionId);
        } catch (Throwable t) {
            log.error("DIAGNÓSTICO 12.1: falha ao processar requisição curveCode={}, executionId={}", curveCode, executionId, t);
            if (t instanceof Exception e) {
                throw e;
            }
            throw new RuntimeException(t);
        }

        ack.acknowledge();
    }
}
