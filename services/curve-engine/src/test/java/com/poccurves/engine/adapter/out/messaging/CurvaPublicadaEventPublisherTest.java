package com.poccurves.engine.adapter.out.messaging;
import com.poccurves.engine.domain.validacao.Classificacao;
import com.poccurves.engine.domain.validacao.ResultadoTeste;
import com.poccurves.engine.domain.validacao.ResultadoValidacao;
import com.poccurves.engine.domain.versao.MomentoCurva;
import com.poccurves.engine.domain.versao.OrigemVersao;
import com.poccurves.engine.domain.versao.VersaoCurva;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.engine.config.KafkaProducerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CurvaPublicadaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private CurvaPublicadaEventPublisher publisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        publisher = new CurvaPublicadaEventPublisher(objectMapper, kafkaTemplate);
    }

    @Test
    void publicaComSucesso() throws Exception {
        UUID definicaoId = UUID.randomUUID();
        UUID versaoDefinicaoId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 10, 10);

        VersaoCurva versao = VersaoCurva.criar(definicaoId, versaoDefinicaoId, dataRef, MomentoCurva.FECHAMENTO, 2, OrigemVersao.CALCULADA, execucaoId);
        versao.publicar();

        ResultadoTeste resultadoOk = new ResultadoTeste("T1", Classificacao.BLOQUEANTE, ResultadoValidacao.APROVADO, null, null, "ok");

        publisher.publicar("PRE_DI", versao, 5, List.of(resultadoOk));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaProducerConfig.TOPICO_CURVE_PUBLISHED), eq("PRE_DI|2026-10-10"), payloadCaptor.capture());

        String json = payloadCaptor.getValue();
        assertThat(json).contains("\"curveCode\":\"PRE_DI\"");
        assertThat(json).contains("\"referenceDate\":\"2026-10-10\"");
        assertThat(json).contains("\"curveMoment\":\"FECHAMENTO\"");
        assertThat(json).contains("\"versionOrigin\":\"CALCULADA\"");
        assertThat(json).contains("\"versionNumber\":2");
        assertThat(json).contains("\"vertexCount\":5");
        assertThat(json).doesNotContain("warnings");
    }

    @Test
    void lancaErroSeVersaoNaoPublicada() {
        UUID definicaoId = UUID.randomUUID();
        UUID versaoDefinicaoId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 10, 10);

        VersaoCurva versao = VersaoCurva.criar(definicaoId, versaoDefinicaoId, dataRef, MomentoCurva.FECHAMENTO, 2, OrigemVersao.CALCULADA, execucaoId);

        assertThatThrownBy(() -> publisher.publicar("PRE_DI", versao, 5, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void incluiWarningsSeHouverAvisoReprovado() throws Exception {
        UUID definicaoId = UUID.randomUUID();
        UUID versaoDefinicaoId = UUID.randomUUID();
        UUID execucaoId = UUID.randomUUID();
        LocalDate dataRef = LocalDate.of(2026, 10, 10);

        VersaoCurva versao = VersaoCurva.criar(definicaoId, versaoDefinicaoId, dataRef, MomentoCurva.FECHAMENTO, 2, OrigemVersao.CALCULADA, execucaoId);
        versao.publicar();

        ResultadoTeste resultadoAviso = new ResultadoTeste("T2", Classificacao.AVISO, ResultadoValidacao.REPROVADO, new BigDecimal("1.5"), new BigDecimal("1.0"), "falhou");

        publisher.publicar("PRE_DI", versao, 5, List.of(resultadoAviso));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaProducerConfig.TOPICO_CURVE_PUBLISHED), eq("PRE_DI|2026-10-10"), payloadCaptor.capture());

        String json = payloadCaptor.getValue();
        assertThat(json).contains("\"warnings\":[{\"testId\":\"T2\",\"observed\":\"1.5\",\"threshold\":\"1.0\"}]");
    }
}
