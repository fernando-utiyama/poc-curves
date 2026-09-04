package com.poccurves.engine.adapter.out.json;
import com.poccurves.engine.application.port.JsonPort;

import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JacksonJsonAdapter implements JsonPort {

    private final ObjectMapper objectMapper;

    public JacksonJsonAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toJson(Object valor) {
        return objectMapper.writeValueAsString(valor);
    }
}
