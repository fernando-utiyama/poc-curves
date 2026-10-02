package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record RecuperarStatusResponseDto(
    Long id,
    String nome,
    String status,
    LocalDateTime dataCriacao,
    LocalDateTime dataAtualizacao,
    Instant ultimaExecucao,
    Instant proximaExecucao,
    String ultimaMensagem,
    List<LogRecuperarStatusResponseDto> logs
) {
}
