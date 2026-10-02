package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.time.LocalDateTime;

public record SchedulerTaskStatusDto(
    Long tarefaId,
    String status,
    Boolean teveErro,
    String ultimaMensagem,
    LocalDateTime ultimaExecucao,
    LocalDateTime proximaExecucao
) {
}
