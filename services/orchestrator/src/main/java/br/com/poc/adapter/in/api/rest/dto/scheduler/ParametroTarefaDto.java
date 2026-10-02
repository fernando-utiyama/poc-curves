package br.com.poc.adapter.in.api.rest.dto.scheduler;

import jakarta.validation.constraints.NotBlank;

public record ParametroTarefaDto(
    Long id,
    @NotBlank String nome,
    @NotBlank String valor,
    @NotBlank String tipo
) {
}
