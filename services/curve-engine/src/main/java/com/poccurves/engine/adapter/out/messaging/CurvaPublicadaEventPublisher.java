package com.poccurves.engine.adapter.out.messaging;
import com.poccurves.engine.domain.validacao.Classificacao;
import com.poccurves.engine.domain.validacao.ResultadoTeste;
import com.poccurves.engine.domain.validacao.ResultadoValidacao;
import com.poccurves.engine.domain.versao.EstadoVersaoCurva;
import com.poccurves.engine.domain.versao.VersaoCurva;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.poccurves.engine.application.CurvaPublicadaEventPort;
import com.poccurves.engine.config.KafkaProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class CurvaPublicadaEventPublisher implements CurvaPublicadaEventPort {

    private static final Logger log = LoggerFactory.getLogger(CurvaPublicadaEventPublisher.class);

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CurvaPublicadaEventPublisher(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publicar(String curveCode, VersaoCurva versao, int vertexCount, List<ResultadoTeste> resultadosValidacao) {
        if (versao.estado() != EstadoVersaoCurva.PUBLICADA) {
            throw new IllegalArgumentException("versao deve estar PUBLICADA para emitir evento");
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

        List<ResultadoTeste> warnings = resultadosValidacao.stream()
                .filter(r -> r.classificacao() == Classificacao.AVISO && r.resultado() == ResultadoValidacao.REPROVADO)
                .toList();

        if (!warnings.isEmpty()) {
            ArrayNode warningsArray = evento.putArray("warnings");
            for (ResultadoTeste r : warnings) {
                ObjectNode warningNode = warningsArray.addObject();
                warningNode.put("testId", r.identificador());
                warningNode.put("observed", r.medidaObservada() == null ? "0" : r.medidaObservada().toPlainString());
                warningNode.put("threshold", r.limiteAplicado() == null ? "0" : r.limiteAplicado().toPlainString());
            }
        }

        String chave = curveCode + "|" + versao.dataReferencia();
        kafkaTemplate.send(KafkaProducerConfig.TOPICO_CURVE_PUBLISHED, chave, evento.toString());

        log.info("Evento de curva publicada enviado para curveCode={}, versionId={}", curveCode, versao.id());
    }
}
