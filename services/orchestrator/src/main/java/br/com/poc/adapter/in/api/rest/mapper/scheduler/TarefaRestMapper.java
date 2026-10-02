package br.com.poc.adapter.in.api.rest.mapper.scheduler;

import br.com.poc.adapter.in.api.rest.dto.scheduler.*;
import br.com.poc.application.model.scheduler.LogTarefa;
import br.com.poc.application.model.scheduler.ParametroTarefa;
import br.com.poc.application.model.scheduler.Tarefa;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper MapStruct para converter entre DTOs REST de tarefa e o modelo de domínio.
 *
 * Responsabilidades:
 * - Converter `TarefaRequestDto` para `Tarefa` ao receber requisições.
 * - Converter `Tarefa` para `TarefaResponseDto` ao retornar respostas.
 */
@Mapper(componentModel = "spring")
public interface TarefaRestMapper {
    Tarefa toDomain(TarefaRequestDto dto);
    Tarefa toDomainPatch(TarefaPatchRequestDto dto);
    ParametroTarefa toDomain(ParametroRequestDto dto);
    TarefaResponseDto toResponse(Tarefa tarefa);
    List<TarefaResponseDto> toResponseList(List<Tarefa> tarefas);
    LogTarefaResponseDto toLogResponse(LogTarefa log);
    ParametroTarefaDto toDto(ParametroTarefa parametro);
    LogTarefaDto toDto(LogTarefa log);

    ParametroResponseDto toParametroResponse(ParametroTarefa parametro);
    CriarTarefaResponseDto toCriarResponse(Tarefa tarefa);
}
