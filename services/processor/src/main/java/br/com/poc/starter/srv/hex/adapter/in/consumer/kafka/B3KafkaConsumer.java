package br.com.poc.starter.srv.hex.adapter.in.consumer.kafka;

import br.com.poc.starter.srv.hex.application.model.B3CurveRaw;
//import br.com.poc.starter.srv.hex.application.port.in.CurveUseCase;
import br.com.poc.starter.srv.hex.application.port.in.ProcessB3CurveUseCase;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class B3KafkaConsumer {

    //private final CurveUseCase curveUseCase;
    private final ProcessB3CurveUseCase processB3CurveUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "tp-event-b3-curve",
        //topics = "${spring.kafka.topics.b3.name}",
        groupId = "tp-event-extraction-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) throws Exception {
        try {
            log.info("Recebendo mensagem B3");
            B3CurveRaw curveRaw = objectMapper.readValue(message, B3CurveRaw.class);
            processB3CurveUseCase.execute(curveRaw);

            // CONFIRMA PROCESSAMENTO
            ack.acknowledge();
            log.info("Mensagem B3 processada com sucesso");
        } catch (Exception ex) {
            log.error("Erro ao processar mensagem B3", ex);
            throw new RuntimeException(ex);
        }
    }
}
