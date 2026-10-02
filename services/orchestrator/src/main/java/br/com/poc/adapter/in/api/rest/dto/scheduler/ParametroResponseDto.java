package br.com.poc.adapter.in.api.rest.dto.scheduler;

public record ParametroResponseDto(
    Long id,
    String nome,
    String valor,
    String tipo
) {
}
