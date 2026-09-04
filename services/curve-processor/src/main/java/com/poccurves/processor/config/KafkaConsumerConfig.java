package com.poccurves.processor.config;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.processor.adapter.in.messaging.dlq.CabecalhosDlqFactory;
import com.poccurves.processor.application.MetricasIngestao;
import com.poccurves.processor.domain.CurvaNaoMapeadaException;
import com.poccurves.processor.domain.CurvaVaziaException;
import com.poccurves.processor.domain.DatasetDesconhecidoException;
import com.poccurves.processor.domain.EnvelopeInvalidoException;
import com.poccurves.processor.domain.IncoerenciaModoOrigemException;
import com.poccurves.processor.domain.ParseFalhouException;
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
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuração dos três listener container factories de ingestão (rotina,
 * prioritária, massa), cada um com grupo de consumo próprio, confirmação
 * manual de offset, desserializador que encapsula falha em vez de lançá-la
 * (ErrorHandlingDeserializer — nunca trava a partição em erro de
 * desserialização), e recuperador terminal que publica na dead-letter da
 * faixa e avança o offset — nunca retentativa infinita (invariante D11c do
 * design.md da mudança guarda-chuva).
 * <p>
 * Retentativa (tarefa 2.3): {@link EnvelopeInvalidoException},
 * {@link DatasetDesconhecidoException} e {@link ParseFalhouException} são
 * falhas permanentes — o conteúdo da mensagem não muda numa nova tentativa
 * — e vão direto para a dead-letter, sem retentar. Qualquer outra exceção
 * (ex. falha transitória de conexão com o banco) é retentada com backoff
 * exponencial limitado antes de cair na dead-letter. O teto de tempo aqui é
 * um limite genérico (2 minutos) — a tarefa 8.22 (teto derivado do horário
 * limite de publicação por curva) não está implementada nesta versão, por
 * exigir informação por-mensagem que o BackOff da infraestrutura Kafka não
 * tem acesso; ver ressalva no tasks.md da mudança.
 * <p>
 * Só a infraestrutura: nenhum {@code @KafkaListener} está registrado aqui —
 * isso é peça de lógica de negócio (com.poccurves.processor.adapter.in.messaging.IngestaoListener).
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private Map<String, Object> consumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // DIAGNÓSTICO 12.1 (temporário): sem isto, o default do client ("latest") faz um grupo
        // de consumo novo começar do fim do tópico — qualquer mensagem publicada antes do
        // primeiro poll (ex.: o serviço nunca tinha rodado de verdade neste ambiente antes) é
        // pulada silenciosamente, sem erro, sem log. Achado real: confirmado com dado real da
        // B3 já publicado no tópico (511+1492 blocos) e `lote_ingestao` continuando com 0 linhas
        // mesmo depois do grupo de consumo se formar. "earliest" é o comportamento correto para
        // um pipeline de ingestão que não pode perder mensagem por causa de quando o consumidor
        // ligou pela primeira vez.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // DIAGNÓSTICO 12.1 (temporário): kafka-clients 4.1.2 (bundled) usa por padrão o novo
        // protocolo de grupo de consumo KIP-848 (group.protocol=consumer, API ConsumerGroupHeartbeat),
        // que o broker local (apache/kafka:3.8.0) só suporta em modo preview — nenhum grupo de
        // consumo jamais se formava (confirmado via kafka-consumer-groups.sh --list, vazio, enquanto
        // o console-consumer nativo do broker, protocolo clássico, funcionou instantaneamente).
        // Forçar o protocolo clássico (JoinGroup/SyncGroup/Heartbeat) evita a negociação do novo
        // protocolo. Ver nota na tarefa 12.1 de openspec/changes/curve-engine/tasks.md.
        props.put("group.protocol", "classic");
        return props;
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> criarFactory(
            String groupId, String topicoDlq, KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper, MetricasIngestao metricasIngestao) {
        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps(groupId));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(topicoDlq, record.partition()));
        // Enriquecimento da dead-letter (tarefa 2.2), com exatamente os 9
        // cabeçalhos x-* obrigatórios do catálogo (contracts/events/topics.yaml).
        CabecalhosDlqFactory cabecalhosDlqFactory = new CabecalhosDlqFactory(objectMapper);
        recoverer.setHeadersFunction(cabecalhosDlqFactory::criar);
        // Métrica de dead-letter (tarefa 7.3) — addHeadersFunction soma a este,
        // não substitui; roda uma vez por mensagem recuperada, mesmo ponto do envio real.
        recoverer.addHeadersFunction((record, exception) -> {
            metricasIngestao.mensagensDeadLetter().increment();
            return new org.apache.kafka.common.header.internals.RecordHeaders();
        });

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxElapsedTime(120000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                EnvelopeInvalidoException.class,
                DatasetDesconhecidoException.class,
                ParseFalhouException.class,
                CurvaNaoMapeadaException.class,
                CurvaVaziaException.class,
                IncoerenciaModoOrigemException.class);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // x-attempts (CabecalhosDlqFactory) lê este cabeçalho de tentativa de entrega.
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        return factory;
    }

    @Bean(name = "kafkaListenerContainerFactoryRotina")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactoryRotina(
            KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MetricasIngestao metricasIngestao) {
        return criarFactory(
                "curve-processor-rotina",
                "marketdata.rotina.v1.curve-processor-rotina.dlq",
                kafkaTemplate, objectMapper, metricasIngestao);
    }

    @Bean(name = "kafkaListenerContainerFactoryPrioritaria")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactoryPrioritaria(
            KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MetricasIngestao metricasIngestao) {
        return criarFactory(
                "curve-processor-prioritaria",
                "marketdata.prioritaria.v1.curve-processor-prioritaria.dlq",
                kafkaTemplate, objectMapper, metricasIngestao);
    }

    @Bean(name = "kafkaListenerContainerFactoryMassa")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactoryMassa(
            KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper, MetricasIngestao metricasIngestao) {
        return criarFactory(
                "curve-processor-massa",
                "marketdata.massa.v1.curve-processor-massa.dlq",
                kafkaTemplate, objectMapper, metricasIngestao);
    }
}
