package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.ConfiguracaoCurva;
import br.com.poc.domain.cadastro.ParametrosCalculo;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record ConfiguracaoCurvaResponse(
    Long id,
    Integer versao,
    String modeloConstrucao,
    String interpolador,
    ParametrosCalculo parametros,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate inicioVigencia,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate fimVigencia
) {
    public static ConfiguracaoCurvaResponse fromDomain(ConfiguracaoCurva c) {
        if (c == null) return null;
        return new ConfiguracaoCurvaResponse(
            c.id(),
            c.versao(),
            c.modeloConstrucao(),
            c.interpolador(),
            c.parametros(),
            c.inicioVigencia(),
            c.fimVigencia()
        );
    }
}
