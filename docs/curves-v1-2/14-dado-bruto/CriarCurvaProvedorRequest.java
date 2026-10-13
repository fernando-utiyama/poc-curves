package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.CriarCurvaProvedorInput;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Requisição para incluir um provedor na curva")
public record CriarCurvaProvedorRequest(
    @Schema(description = "Provedor já cadastrado", example = "B3") String provedor,
    @Schema(description = "Produto do provedor (até 50 caracteres)", example = "TS") String produto,
    @Schema(description = "Código da curva no provedor", example = "PRE") String tickerProvedor,
    @Schema(description = "Prioridade na curva, 1 é a principal; não repete na mesma curva", example = "1") Integer prioridade
) {

    public CriarCurvaProvedorInput toDomain() {
        return new CriarCurvaProvedorInput(provedor, produto, tickerProvedor, prioridade);
    }
}
