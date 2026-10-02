package br.com.poc.adapter.in.api.rest.dto.scheduler;

import java.util.List;

public record TarefaPatchRequestDto(
    String nome,
    String descricao,
    String action,
    String regraCron,
    String regraIntervalo,
    List<ParametroRequestDto> parametros
) {
    public TarefaPatchRequestDto {
        parametros = parametros == null ? null : List.copyOf(parametros);
    }
}
