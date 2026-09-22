package br.com.poc.starter.srv.hex.adapter.in.consumer.kafka;

//import br.com.poc.starter.srv.hex.application.port.in.CurveUseCase;
import br.com.poc.starter.srv.hex.application.model.BloombergCurveRaw;
import br.com.poc.starter.srv.hex.application.port.in.ProcessBloombergCurveUseCase;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BloombergKafkaConsumer {

    //private final CurveUseCase curveUseCase;
    private final ProcessBloombergCurveUseCase processBloombergCurveUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "tp-event-bloomberg-curve",
        //topics = "${spring.kafka.topics.bloomberg.name}",
        groupId = "tp-event-extraction-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) throws Exception {
        try {
            log.info("Recebendo mensagem Bloomberg");
            BloombergCurveRaw curveRaw = objectMapper.readValue(message, BloombergCurveRaw.class);
            processBloombergCurveUseCase.execute(curveRaw);

            // CONFIRMA PROCESSAMENTO
            ack.acknowledge();
            log.info("Mensagem Bloomberg processada com sucesso");
        } catch (Exception ex) {
            log.error("Erro ao processar mensagem Bloomberg", ex);
            throw new RuntimeException(ex);
        }
    }
}
