package br.com.poc.adapter.out.log;

import br.com.poc.application.port.out.EventosPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EventosLogAdapter implements EventosPort {

    private static final Logger log = LoggerFactory.getLogger(EventosLogAdapter.class);

    private final ObjectMapper objectMapper;

    public EventosLogAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void publicar(String evento, Map<String, Object> dados) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("evento", evento);
        payload.put("correlationId", MDC.get("correlationId"));
        if (dados != null) {
            payload.putAll(dados);
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            switch (evento) {
                case "CONSTRUCAO_FALHOU", "DEPENDENCIA_FALHOU" -> log.error("{}", json);
                case "INSUMO_DESCARTADO", "CURVA_GRAVADA", "PONTOS_DIFERENTES_DA_FONTE", "DEPENDENCIA_LENTA" -> log.warn("{}", json);
                default -> log.info("{}", json);
            }
        } catch (JacksonException e) {
            log.info("evento={}, dados={}", evento, dados);
        }
    }
}
