package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.CriarConfiguracaoCurvaInput;
import br.com.poc.domain.cadastro.ParametrosCalculo;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record CriarConfiguracaoCurvaRequest(
    String modeloConstrucao,
    String interpolador,
    ParametrosCalculo parametros,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate inicioVigencia
) {
    public CriarConfiguracaoCurvaInput toDomain() {
        return new CriarConfiguracaoCurvaInput(modeloConstrucao, interpolador, parametros, inicioVigencia);
    }
}
