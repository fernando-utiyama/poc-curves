package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.CriarConfiguracaoCurvaInput;
import br.com.poc.domain.cadastro.ParametrosCalculo;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Requisição para criar ou validar uma versão da configuração de cálculo da curva")
public record CriarConfiguracaoCurvaRequest(
    @Schema(description = "Modelo de construção (até 100 caracteres)", example = "TAXA_SWAP_B3") String modeloConstrucao,
    @Schema(description = "Interpolador (até 100 caracteres)", example = "FlatForward") String interpolador,
    @Schema(description = "Parâmetros de cálculo da versão") ParametrosCalculo parametros,
    @Schema(description = "Início da vigência da versão (AAAA-MM-DD); a anterior é fechada no dia anterior", example = "2026-01-01")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate inicioVigencia
) {

    public CriarConfiguracaoCurvaInput toDomain() {
        return new CriarConfiguracaoCurvaInput(modeloConstrucao, interpolador, parametros, inicioVigencia);
    }
}
