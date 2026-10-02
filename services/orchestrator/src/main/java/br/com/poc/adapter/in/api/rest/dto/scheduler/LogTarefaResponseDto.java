package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.time.LocalDateTime;

public record LogTarefaResponseDto(
    Long id,
    LocalDateTime dataCriacao,
    Integer codigo,
    String texto
) {
}
