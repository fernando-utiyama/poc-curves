package com.poccurves.processor.adapter.out.messaging;

import com.poccurves.processor.application.CurvaPublicadaEventPort;
import com.poccurves.processor.application.MetricasIngestao;
import com.poccurves.processor.domain.EstadoVersaoCurva;
import com.poccurves.processor.domain.VersaoCurva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Publica curve.published.v1 depois do commit de uma versão de curva —
 * contrato idêntico ao usado pelo curve-engine para curvas CALCULADA
 * (contracts/events/curve-published.schema.json: o formato é o mesmo
 * independente da origem, quem distingue é o campo versionOrigin).
 * <p>
 * Chamado explicitamente por quem orquestra a publicação (nunca de dentro
 * de {@link com.poccurves.processor.application.PublicacaoCurvaService#publicarCurvaImportada},
 * que é {@code @Transactional} — publicar no Kafka só depois que a transação já
 * commitou, nunca antes).
 */
@Component
public class CurvaPublicadaEventPublisher implements CurvaPublicadaEventPort {

    private static final Logger log = LoggerFactory.getLogger(CurvaPublicadaEventPublisher.class);
    private static final String TOPICO_CURVE_PUBLISHED = "curve.published.v1";

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MetricasIngestao metricasIngestao;

    public CurvaPublicadaEventPublisher(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate, MetricasIngestao metricasIngestao) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.metricasIngestao = metricasIngestao;
    }

    @Override
    public void publicar(String curveCode, VersaoCurva versao, int vertexCount) {
        if (versao.estado() != EstadoVersaoCurva.PUBLICADA) {
            throw new IllegalArgumentException("versao deve estar PUBLICADA para emitir curve.published.v1, estado atual: " + versao.estado());
        }

        ObjectNode evento = objectMapper.createObjectNode();
        evento.put("curveCode", curveCode);
        evento.put("referenceDate", versao.dataReferencia().toString());
        evento.put("curveMoment", versao.momentoCurva().name());
        evento.put("versionId", versao.id().toString());
        evento.put("versionNumber", versao.numeroVersao());
        evento.put("versionOrigin", versao.origemVersao().name());
        evento.put("publishedAt", Instant.now().toString());
        evento.put("vertexCount", vertexCount);

        String chave = curveCode + "|" + versao.dataReferencia();
        kafkaTemplate.send(TOPICO_CURVE_PUBLISHED, chave, evento.toString());
        metricasIngestao.curvasPublicadas().increment();
        log.info("curve.published.v1 publicado: curveCode={} versionId={} versionNumber={} versionOrigin={} vertexCount={}",
                curveCode, versao.id(), versao.numeroVersao(), versao.origemVersao(), vertexCount);
    }
}
