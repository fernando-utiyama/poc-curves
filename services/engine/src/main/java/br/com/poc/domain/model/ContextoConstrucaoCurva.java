package br.com.poc.domain.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

public record ContextoConstrucaoCurva(
    String ticker,
    LocalDate dataBase,
    Map<String, String> parametros
) {
    public ContextoConstrucaoCurva {
        parametros = parametros != null ? Collections.unmodifiableMap(parametros) : Collections.emptyMap();
    }

    public String getParametro(String chave, String padrao) { return parametros.getOrDefault(chave, padrao); }
}
