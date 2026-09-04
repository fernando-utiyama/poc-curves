package com.poccurves.processor.adapter.out.messaging;
import com.poccurves.processor.domain.curva.MomentoCurva;
import com.poccurves.processor.domain.curva.OrigemVersao;
import com.poccurves.processor.domain.curva.VersaoCurva;

import com.poccurves.processor.application.MetricasIngestao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CurvaPublicadaEventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetricasIngestao metricasIngestao = new MetricasIngestao(new SimpleMeterRegistry());

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplateFake() {
        return mock(KafkaTemplate.class);
    }

    private VersaoCurva versaoPublicada() {
        VersaoCurva versao = VersaoCurva.criar(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 8, 21),
                MomentoCurva.FECHAMENTO, 3, OrigemVersao.IMPORTADA, UUID.randomUUID());
        versao.publicar();
        return versao;
    }

    @Test
    void publicaEventoComTodosOsCamposObrigatoriosDoContrato() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateFake();
        CurvaPublicadaEventPublisher publisher = new CurvaPublicadaEventPublisher(objectMapper, kafkaTemplate, metricasIngestao);
        VersaoCurva versao = versaoPublicada();

        publisher.publicar("PRE_DI1_B3", versao, 15);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("curve.published.v1"), any(), captor.capture());

        JsonNode evento = objectMapper.readTree(captor.getValue());
        assertThat(evento.get("curveCode").asText()).isEqualTo("PRE_DI1_B3");
        assertThat(evento.get("referenceDate").asText()).isEqualTo("2026-08-21");
        assertThat(evento.get("curveMoment").asText()).isEqualTo("FECHAMENTO");
        assertThat(evento.get("versionId").asText()).isEqualTo(versao.id().toString());
        assertThat(evento.get("versionNumber").asInt()).isEqualTo(3);
        assertThat(evento.get("versionOrigin").asText()).isEqualTo("IMPORTADA");
        assertThat(evento.get("vertexCount").asInt()).isEqualTo(15);
        assertThat(evento.has("publishedAt")).isTrue();
    }

    @Test
    void usaCurveCodePipeDataComoChaveDeParticao() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateFake();
        CurvaPublicadaEventPublisher publisher = new CurvaPublicadaEventPublisher(objectMapper, kafkaTemplate, metricasIngestao);
        VersaoCurva versao = versaoPublicada();

        publisher.publicar("PRE_DI1_B3", versao, 1);

        verify(kafkaTemplate).send(eq("curve.published.v1"), eq("PRE_DI1_B3|2026-08-21"), any());
    }

    @Test
    void recusaVersaoQueNaoEstaPublicada() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateFake();
        CurvaPublicadaEventPublisher publisher = new CurvaPublicadaEventPublisher(objectMapper, kafkaTemplate, metricasIngestao);
        VersaoCurva versao = VersaoCurva.criar(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                MomentoCurva.FECHAMENTO, 1, OrigemVersao.IMPORTADA, UUID.randomUUID());

        assertThatThrownBy(() -> publisher.publicar("X", versao, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
