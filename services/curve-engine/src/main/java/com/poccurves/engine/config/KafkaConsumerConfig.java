package com.poccurves.engine.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuração do listener container factory do pedido de construção
 * (curve.build.requested.v1): confirmação manual de offset, desserializador
 * que encapsula falha em vez de lançá-la (ErrorHandlingDeserializer), e
 * recuperador terminal que publica na dead-letter e avança o offset — nunca
 * retentativa infinita (invariante D11c do design.md da mudança guarda-chuva).
 * <p>
 * Só a infraestrutura: nenhum {@code @KafkaListener} está registrado aqui.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final String GRUPO_CONSUMO = "curve-engine-build";
    private static final String TOPICO_DLQ = "curve.build.requested.v1.curve-engine-build.dlq";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaTemplate<String, String> kafkaTemplate) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GRUPO_CONSUMO);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // Achado real da tarefa 12.1 (mesmo gap encontrado e corrigido em curve-processor):
        // (1) @EnableKafka precisa ser explícito nesta versão de Spring Boot/spring-kafka — sem
        // isto o listener container nunca inicia (confirmado com dump de thread real: zero
        // threads de Kafka, nenhum grupo de consumo jamais se formava, mesmo com dado real
        // publicado no tópico). (2) auto.offset.reset default "latest" pula silenciosamente
        // qualquer mensagem publicada antes do primeiro poll do consumidor.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put("group.protocol", "classic");

        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(props);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(TOPICO_DLQ, record.partition()));

        // FixedBackOff(0, 0): zero retentativas — vai direto para a dead-letter.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 0L));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
