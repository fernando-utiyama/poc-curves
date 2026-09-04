package com.poccurves.orchestrator.adapter.out.messaging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração real: exige o Kafka local de pé (porta externa 19092).
 * Publica um pedido real e lê de volta diretamente do tópico com um consumidor
 * dedicado, para confirmar o formato exato do payload publicado — mesmo padrão
 * de verificação já usado para o feeder-marketdata nesta sessão.
 */
class BuildRequestPublisherIT {

    private static KafkaTemplate<String, String> kafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    @Test
    void publicarEnviaPayloadRealComTodosOsCamposDoSchemaENenhumOutro() throws Exception {
        BuildRequestPublisher publisher = new BuildRequestPublisher(kafkaTemplate(), new ObjectMapper());

        UUID runId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        LocalDate referenceDate = LocalDate.of(2030, 7, 4);
        LocalTime horarioLimite = LocalTime.of(18, 45);
        String curveCode = "IT_BUILD_REQUEST_" + UUID.randomUUID().toString().substring(0, 8);

        publisher.publicar(curveCode, referenceDate, "FECHAMENTO", runId, executionId, horarioLimite);

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "it-build-request-verify-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonNode encontrado = null;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("curve.build.requested.v1"));
            ObjectMapper mapper = new ObjectMapper();

            long deadline = System.currentTimeMillis() + 30_000;
            while (encontrado == null && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode node = mapper.readTree(record.value());
                    if (curveCode.equals(node.path("curveCode").asText())) {
                        encontrado = node;
                        assertThat(record.key()).isEqualTo(curveCode + "|" + referenceDate);
                    }
                }
            }
        }

        assertThat(encontrado).as("mensagem publicada deve ter sido lida de volta do tópico real").isNotNull();
        assertThat(encontrado.get("curveCode").asText()).isEqualTo(curveCode);
        assertThat(encontrado.get("referenceDate").asText()).isEqualTo("2030-07-04");
        assertThat(encontrado.get("curveMoment").asText()).isEqualTo("FECHAMENTO");
        assertThat(encontrado.get("runId").asText()).isEqualTo(runId.toString());
        assertThat(encontrado.get("executionId").asText()).isEqualTo(executionId.toString());
        assertThat(encontrado.get("publishDeadline").asText()).isEqualTo("2030-07-04T18:45:00Z");

        // additionalProperties: false no schema real — confirma que não há nenhum campo extra
        List<String> camposReais = new java.util.ArrayList<>(encontrado.propertyNames());
        assertThat(camposReais).containsExactlyInAnyOrder(
                "curveCode", "referenceDate", "curveMoment", "runId", "executionId", "publishDeadline");
    }
}
