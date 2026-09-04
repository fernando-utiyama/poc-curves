package com.poccurves.orchestrator.adapter.out.messaging;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.orchestrator.application.BuildRequestPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica o evento curve.build.requested.v1 (contracts/events/curve-build-requested.schema.json)
 * — tarefa 8.2 do backlog curve-orchestrator. Payload publicado diretamente, sem o envelope
 * genérico EventEnvelope (esse é só para tópicos de ingestão de dado de mercado).
 */
@Component
public class BuildRequestPublisher implements BuildRequestPort {

    private static final String TOPICO = "curve.build.requested.v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public BuildRequestPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publica um pedido de construção de curva. O horário limite de publicação (publishDeadline)
     * é combinado a partir da data de referência e do horário limite (TIME) cadastrado na
     * definição de curva, interpretado em UTC (não há fuso horário separado cadastrado hoje —
     * mesma limitação já assumida em outras partes deste backlog para horários de corte).
     * <p>
     * {@code runId} e {@code executionId} são deliberadamente distintos (gap real encontrado ao
     * planejar a persistência do lado do curve-engine): {@code runId} é o {@code correlacao_id} do
     * lote de disparo, que pode ser compartilhado por várias linhas de {@code execucao_curva} do
     * mesmo lote; {@code executionId} é o {@code id} da linha específica que originou este pedido —
     * é o que {@code versao_curva.execucao_curva_id} (FK obrigatória e não-nula) realmente precisa.
     *
     * @throws org.springframework.kafka.KafkaException     (ou subclasse) se a publicação falhar de verdade —
     *         propositalmente não capturada aqui, quem chama decide o que fazer
     */
    @Override
    public void publicar(String curveCode, LocalDate referenceDate, String curveMoment, UUID runId, UUID executionId, LocalTime horarioLimitePublicacao) {
        Instant publishDeadline = referenceDate.atTime(horarioLimitePublicacao).toInstant(ZoneOffset.UTC);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("curveCode", curveCode);
        payload.put("referenceDate", referenceDate.toString());
        payload.put("curveMoment", curveMoment);
        payload.put("runId", runId.toString());
        payload.put("executionId", executionId.toString());
        payload.put("publishDeadline", publishDeadline.toString());

        String valorJson;
        try {
            valorJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar payload de curve.build.requested.v1", e);
        }

        String chave = curveCode + "|" + referenceDate;
        kafkaTemplate.send(TOPICO, chave, valorJson);
    }
}
