package br.com.poc.starter.srv.hex.adapter.in.consumer.kafka;

import br.com.poc.starter.srv.hex.application.model.AnbimaCurveRaw;
//import br.com.poc.starter.srv.hex.application.port.in.CurveUseCase;
import br.com.poc.starter.srv.hex.application.port.in.ProcessAnbimaCurveUseCase;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnbimaKafkaConsumer {

    //private final CurveUseCase curveUseCase;
    private final ProcessAnbimaCurveUseCase processAnbimaCurveUseCase;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "tp-event-anbima-curve",
        //topics = "${spring.kafka.topics.anbima.name}",
        groupId = "tp-event-extraction-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment ack) throws Exception {
        try {
            log.info("Recebendo mensagem Anbima");
            AnbimaCurveRaw curveRaw = objectMapper.readValue(message, AnbimaCurveRaw.class);
            processAnbimaCurveUseCase.execute(curveRaw);

            // CONFIRMA PROCESSAMENTO
            ack.acknowledge();
            log.info("Mensagem Anbima processada com sucesso");
        } catch (Exception ex) {
            log.error("Erro ao processar mensagem Anbima", ex);
            throw new RuntimeException(ex);
        }
    }
}
