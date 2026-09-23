package br.com.poc.application.service;

import br.com.poc.adapter.in.api.rest.dto.scheduler.RecuperarStatusResponseDto;
import br.com.poc.adapter.out.persistence.jpa.mapper.TarefaJpaResponseMapper;
import br.com.poc.application.exception.NotFoundException;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.port.in.RecuperarStatusUseCase;

import br.com.poc.application.port.out.LogTaskRepositoryPort;
import br.com.poc.application.port.out.TaskRepositoryPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class RecuperarStatusService implements RecuperarStatusUseCase {

    private final TaskRepositoryPort repository;
    private final LogTaskRepositoryPort logTarefaRepositoryPort;
    private final TarefaJpaResponseMapper tarefaJpaMapper;

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
