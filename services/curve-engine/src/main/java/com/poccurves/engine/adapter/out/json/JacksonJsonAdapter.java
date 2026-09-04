package com.poccurves.engine.adapter.out.json;

import tools.jackson.databind.ObjectMapper;
import com.poccurves.engine.application.JsonPort;
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
