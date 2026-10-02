package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.time.LocalDateTime;

public record LogRecuperarStatusResponseDto(
    LocalDateTime dataCriacao,
    String texto
) {
}
