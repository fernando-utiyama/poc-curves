package com.poccurves.processor.adapter.out.messaging;

import com.poccurves.processor.application.NormalizedEventPort;
import com.poccurves.processor.domain.LoteIngestao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Publica marketdata.normalized.v1 quando um lote de ingestão fica COMPLETO. */
@Component
public class NormalizedEventPublisher implements NormalizedEventPort {

    private static final Logger log = LoggerFactory.getLogger(NormalizedEventPublisher.class);
    private static final String TOPICO_NORMALIZED = "marketdata.normalized.v1";

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public NormalizedEventPublisher(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publicar(LoteIngestao lote) {
        var evento = objectMapper.createObjectNode();
        evento.put("loteId", lote.loteExternoId());
        evento.put("source", lote.fonte());
        evento.put("dataset", lote.conjuntoDados());
        evento.put("referenceDate", lote.dataReferencia().toString());
        evento.put("pointsPersisted", lote.pontosGravados());
        evento.put("divergences", lote.pontosDivergentes());

        String chave = lote.fonte() + "|" + lote.conjuntoDados() + "|" + lote.dataReferencia();
        kafkaTemplate.send(TOPICO_NORMALIZED, chave, evento.toString());
        log.info("marketdata.normalized.v1 publicado: loteId={} dataset={} pointsPersisted={} divergences={}",
                lote.loteExternoId(), lote.conjuntoDados(), lote.pontosGravados(), lote.pontosDivergentes());
    }
}
