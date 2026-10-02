package br.com.poc.application.service;

import br.com.poc.adapter.in.api.rest.dto.scheduler.RecuperarStatusResponseDto;
import br.com.poc.adapter.out.persistence.jpa.mapper.TarefaJpaResponseMapper;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.model.scheduler.LogTarefa;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.port.in.RecuperarStatusUseCase;
import br.com.poc.application.port.out.LogTaskRepositoryPort;
import br.com.poc.application.port.out.TaskRepositoryPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@AllArgsConstructor
public class RecuperarStatusService implements RecuperarStatusUseCase {

    private final TaskRepositoryPort repository;
    private final LogTaskRepositoryPort logTarefaRepositoryPort;
    private final TarefaJpaResponseMapper tarefaJpaMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RecuperarStatusResponseDto> listarStatusDetalhado() {
        List<Tarefa> tarefas = repository.findAllTarefasResumidas();
        if (tarefas.isEmpty()) {
            return List.of();
        }

        List<Long> tarefaIds = tarefas.stream()
            .map(Tarefa::getId)
            .filter(Objects::nonNull)
            .toList();
        Map<Long, List<LogTarefa>> logsPorTarefa =
            logTarefaRepositoryPort.buscarPorTarefaIds(tarefaIds);

        return tarefas.stream()
            .map(tarefa -> tarefaJpaMapper.toStatusResponse(
                tarefa,
                logsPorTarefa.getOrDefault(tarefa.getId(), List.of())
            ))
            .toList();
    }

    @Override
    public RecuperarStatusResponseDto recuperarStatusDetalhado(Long tarefaId) {
        Tarefa tarefa = repository.buscarPorId(tarefaId)
            .orElseThrow(() -> new NotFoundException(
                "TAREFA_NAO_ENCONTRADA",
                "Tarefa nao encontrada"
            ));

        var logs = logTarefaRepositoryPort.buscarPorTarefaId(tarefaId);

        return tarefaJpaMapper.toStatusResponse(tarefa, logs);
    }
}
