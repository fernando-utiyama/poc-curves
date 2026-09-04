package com.poccurves.api.adapter.out.json;

import com.poccurves.api.application.JsonPort;
import com.poccurves.api.dto.ApiDtos.LimiteValidacaoDTO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class JacksonJsonAdapter implements JsonPort {

    private final ObjectMapper objectMapper;

    public JacksonJsonAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toJson(Object valor) {
        if (valor == null) return null;
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> paraListaDeString(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<LimiteValidacaoDTO> paraListaDeLimites(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<LimiteValidacaoDTO>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
