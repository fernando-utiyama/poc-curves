package br.com.poc.adapter.in.api.rest.dto.scheduler;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TarefaRequestDto(
    @NotBlank @Size(max = 255) String nome,
    @NotBlank @Size(max = 1024) String descricao,
    @NotBlank @Size(max = 100) String action,
    @Size(max = 15) String regraCron,
    @Size(max = 20) String regraIntervalo,
    @Valid List<ParametroRequestDto> parametros
) {
    public TarefaRequestDto {
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
    }
}
