package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.time.Instant;
import java.util.List;

public record TarefaResponseDto(
    Long id,
    String nome,
    String descricao,
    String action,
    String regraCron,
    String regraIntervalo,
    String status,
    Instant ultimaExecucao,
    Instant proximaExecucao,
    List<ParametroTarefaDto> parametros,
    List<LogTarefaDto> logs
) {
    public TarefaResponseDto {
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
        logs = logs == null ? List.of() : List.copyOf(logs);
    }
}
