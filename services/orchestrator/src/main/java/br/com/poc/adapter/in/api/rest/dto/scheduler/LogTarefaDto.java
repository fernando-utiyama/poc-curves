package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.time.LocalDateTime;

public record LogTarefaDto(
    Long id,
    LocalDateTime dataCriacao,
    Integer codigo,
    String texto
) {
}
