package com.poccurves.api.application;

import com.poccurves.api.dto.ApiDtos.LimiteValidacaoDTO;

import java.util.List;

/**
 * Fronteira mínima de (de)serialização JSON que o application precisa — só os três formatos
 * que hoje são persistidos como coluna JSON (vínculos de fonte, dependências, limites de
 * validação). Não expõe Jackson (TypeReference/ObjectMapper) na assinatura para manter
 * `application` livre de import de framework.
 */
public interface JsonPort {

    String toJson(Object valor);

    List<String> paraListaDeString(String json);

    List<LimiteValidacaoDTO> paraListaDeLimites(String json);
}
