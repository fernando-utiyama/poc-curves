package com.poccurves.orchestrator.adapter.in.messaging;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.orchestrator.adapter.out.persistence.PendenciaDlqRepository;
import com.poccurves.orchestrator.application.MaterializarPendenciaDlqUseCase;
import com.poccurves.orchestrator.domain.EstadoPendenciaDlq;
import com.poccurves.orchestrator.domain.PendenciaDlq;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o SQL Server local de pé, migrado até V15.
 * <p>
 * Verifica {@link DlqMaterializacaoListener} chamando {@code consumir} diretamente com um
 * {@link ConsumerRecord} construído com os 9 cabeçalhos reais (mesmo formato exato confirmado
 * ao vivo nesta sessão lendo uma mensagem real da dead-letter de curve-processor via
 * kafka-console-consumer: {@code x-dlq-reason}, {@code x-dlq-detail}, {@code x-correlation-id},
 * {@code x-event-id}, {@code x-original-topic}, {@code x-original-partition},
 * {@code x-original-offset}, {@code x-failed-at}, {@code x-attempts}) — a camada de transporte
 * Kafka em si já foi verificada de ponta a ponta nesta sessão (BuildRequestPublisherIT), então
 * este teste foca na lógica de materialização, que é o que esta tarefa realmente implementa.
 */
class DlqMaterializacaoListenerIT {

    private static JdbcTemplate jdbcTemplateSa() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:sqlserver://localhost:1433;databaseName=curvasdb;trustServerCertificate=true;encrypt=true",
                "sa", "CurvasP0c!Local");
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }

    private final PendenciaDlqRepository repository = new PendenciaDlqRepository(jdbcTemplateSa());
    private final DlqMaterializacaoListener listener =
            new DlqMaterializacaoListener(new MaterializarPendenciaDlqUseCase(repository), new ObjectMapper());

    @AfterEach
    void limpar() {
        jdbcTemplateSa().update("DELETE FROM pendencia_dlq WHERE id_evento LIKE 'it-dlq-%'");
    }

    private RecordHeaders cabecalhosReais(String eventId, String correlationId, String reason, String detail,
                                            String originalTopic, int originalPartition, long originalOffset, String failedAt) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("x-dlq-reason", reason.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-dlq-detail", detail.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-correlation-id", correlationId.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-event-id", eventId.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-original-topic", originalTopic.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-original-partition", String.valueOf(originalPartition).getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-original-offset", String.valueOf(originalOffset).getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-failed-at", failedAt.getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("x-attempts", "1".getBytes(StandardCharsets.UTF_8)));
        return headers;
    }

    private static class AckFalso implements Acknowledgment {
        final AtomicBoolean confirmado = new AtomicBoolean(false);
        @Override public void acknowledge() { confirmado.set(true); }
    }

    @Test
    void materializaPendenciaComEnvelopeDeMarketDataRealEConfirmaOOffset() {
        String eventId = "it-dlq-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String corpo = "{\"eventId\":\"" + eventId + "\",\"correlationId\":\"" + correlationId
                + "\",\"source\":\"B3\",\"dataset\":\"IT_DLQ_MATERIALIZACAO\",\"referenceDate\":\"2026-08-24\"}";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "marketdata.rotina.v1.curve-processor-rotina.dlq", 2, 42L, null, corpo);
        cabecalhosReais(eventId, correlationId, "VALIDATION_ERROR", "detalhe de teste real",
                "marketdata.rotina.v1", 2, 41L, "2026-08-22T18:00:00Z")
                .forEach(h -> record.headers().add(h));

        AckFalso ack = new AckFalso();
        listener.consumir(record, ack);

        assertThat(ack.confirmado).isTrue();

        Optional<PendenciaDlq> materializada = repository.buscarPorIdEvento(eventId);
        assertThat(materializada).isPresent();
        PendenciaDlq pendencia = materializada.get();
        assertThat(pendencia.correlacaoId()).isEqualTo(UUID.fromString(correlationId));
        assertThat(pendencia.motivo()).isEqualTo("VALIDATION_ERROR");
        assertThat(pendencia.detalhe()).isEqualTo("detalhe de teste real");
        assertThat(pendencia.fonte()).isEqualTo("B3");
        assertThat(pendencia.conjuntoDados()).isEqualTo("IT_DLQ_MATERIALIZACAO");
        assertThat(pendencia.dataReferencia()).isEqualTo(java.time.LocalDate.of(2026, 8, 24));
        assertThat(pendencia.topicoOrigem()).isEqualTo("marketdata.rotina.v1");
        assertThat(pendencia.particaoOrigem()).isEqualTo(2);
        assertThat(pendencia.offsetOrigem()).isEqualTo(41L);
        assertThat(pendencia.topicoDlq()).isEqualTo("marketdata.rotina.v1.curve-processor-rotina.dlq");
        assertThat(pendencia.particaoDlq()).isEqualTo(2);
        assertThat(pendencia.offsetDlq()).isEqualTo(42L);
        assertThat(pendencia.estado()).isEqualTo(EstadoPendenciaDlq.ABERTA);
    }

    @Test
    void mensagemComEventIdDesconhecidoNaoEhMaterializadaMasOffsetEhConfirmado() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "marketdata.rotina.v1.curve-processor-rotina.dlq", 0, 99L, null, "isto nao eh JSON valido de jeito nenhum");
        cabecalhosReais("unknown", "unknown", "VALIDATION_ERROR", "payload inválido",
                "marketdata.rotina.v1", 0, 98L, "2026-08-22T18:00:00Z")
                .forEach(h -> record.headers().add(h));

        AckFalso ack = new AckFalso();
        listener.consumir(record, ack);

        assertThat(ack.confirmado).isTrue();
        // Nenhuma pendência com id_evento "unknown" deve ter sido criada
        Integer count = jdbcTemplateSa().queryForObject(
                "SELECT COUNT(*) FROM pendencia_dlq WHERE id_evento = 'unknown'", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void segundaMensagemComMesmoEventIdEhIdempotenteENaoDuplica() {
        String eventId = "it-dlq-" + UUID.randomUUID();
        String corpo = "{\"eventId\":\"" + eventId + "\",\"source\":\"B3\",\"dataset\":\"IT_DLQ_DUP\",\"referenceDate\":\"2026-08-24\"}";

        ConsumerRecord<String, String> record1 = new ConsumerRecord<>(
                "marketdata.rotina.v1.curve-processor-rotina.dlq", 1, 10L, null, corpo);
        cabecalhosReais(eventId, "unknown", "VALIDATION_ERROR", "primeira tentativa",
                "marketdata.rotina.v1", 1, 9L, "2026-08-22T18:00:00Z")
                .forEach(h -> record1.headers().add(h));
        listener.consumir(record1, new AckFalso());

        ConsumerRecord<String, String> record2 = new ConsumerRecord<>(
                "marketdata.rotina.v1.curve-processor-rotina.dlq", 1, 11L, null, corpo);
        cabecalhosReais(eventId, "unknown", "VALIDATION_ERROR", "segunda tentativa (redelivery)",
                "marketdata.rotina.v1", 1, 9L, "2026-08-22T18:00:01Z")
                .forEach(h -> record2.headers().add(h));
        AckFalso ack2 = new AckFalso();
        listener.consumir(record2, ack2);

        assertThat(ack2.confirmado).isTrue();
        Integer count = jdbcTemplateSa().queryForObject(
                "SELECT COUNT(*) FROM pendencia_dlq WHERE id_evento = ?", Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }
}
