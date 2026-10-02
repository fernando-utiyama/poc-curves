package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.util.List;

public record CriarTarefaResponseDto(
    Long id,
    String nome,
    String descricao,
    String action,
    String regraCron,
    String regraIntervalo,
    String status,
    List<ParametroResponseDto> parametros,
    List<LogTarefaResponseDto> logs
) {
    public CriarTarefaResponseDto {
        parametros = parametros == null ? List.of() : List.copyOf(parametros);
        logs = logs == null ? List.of() : List.copyOf(logs);
    }
}
