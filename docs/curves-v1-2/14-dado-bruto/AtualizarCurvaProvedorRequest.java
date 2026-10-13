package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.AtualizarCurvaProvedorInput;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Requisição para alterar um provedor da curva (o provedor em si não muda)")
public record AtualizarCurvaProvedorRequest(
    @Schema(description = "Produto do provedor (até 50 caracteres)", example = "TS") String produto,
    @Schema(description = "Código da curva no provedor", example = "PRE") String tickerProvedor,
    @Schema(description = "Prioridade na curva, 1 é a principal; não repete na mesma curva", example = "2") Integer prioridade
) {

    public AtualizarCurvaProvedorInput toDomain() {
        return new AtualizarCurvaProvedorInput(produto, tickerProvedor, prioridade);
    }
}
