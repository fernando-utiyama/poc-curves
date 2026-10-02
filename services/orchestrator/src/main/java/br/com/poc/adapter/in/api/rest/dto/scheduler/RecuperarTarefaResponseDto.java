package br.com.poc.adapter.in.api.rest.dto.scheduler;

import br.com.poc.application.model.scheduler.TarefaStatus;

import java.time.LocalDateTime;

public record RecuperarTarefaResponseDto(
    Integer id,
    String nome,
    TarefaStatus status,
    LocalDateTime dataCriacao,
    LocalDateTime dataAtualizacao
) {

}
