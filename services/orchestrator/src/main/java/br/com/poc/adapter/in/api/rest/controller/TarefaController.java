package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.scheduler.*;
import br.com.poc.adapter.in.api.rest.mapper.scheduler.TarefaRestMapper;
import br.com.poc.application.port.in.TarefaCrudUseCase;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Controller REST responsável pelos endpoints de CRUD e execução de tarefas.
 *
 * Propósito e responsabilidades:
 * - Receber requisições HTTP relacionadas a tarefas (criar, listar, buscar, atualizar,
 *   executar e deletar).
 * - Delegar a lógica de negócio para o `TarefaUseCase` (camada de aplicação).
 * - Converter DTOs para domínio e vice-versa utilizando o `TarefaRestMapper`.
 *
 * Observações para desenvolvedores:
 * - Este controller NÃO deve conter regra de negócio; mantenha regras na camada de
 *   serviço/use case para facilitar testes e reutilização.
 */

@RestController
@AllArgsConstructor
public class TarefaController implements TarefaAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(TarefaController.class);

    private final TarefaCrudUseCase tarefaCrudUseCase;
    private final TarefaRestMapper tarefaRestMapper;

    @Override
    public ResponseEntity<CriarTarefaResponseDto> salvar(TarefaRequestDto request) {
        LOGGER.info("Recebida requisicao para criar tarefa {}", request.nome());
        var tarefa = tarefaCrudUseCase.criarTarefa(tarefaRestMapper.toDomain(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(tarefa.getId())
            .toUri();
        return ResponseEntity.created(location).body(tarefaRestMapper.toCriarResponse(tarefa));
    }

    @Override
    public ResponseEntity<List<TarefaResponseDto>> listar() {
        LOGGER.info("Recebida requisicao para listar tarefas");
        return ResponseEntity.ok(tarefaRestMapper.toResponseList(tarefaCrudUseCase.listarTarefas()));
    }

    @Override
    public ResponseEntity<TarefaResponseDto> buscar(Long id) {
        LOGGER.info("Recebida requisicao para buscar tarefa {}", id);
        return ResponseEntity.ok(tarefaRestMapper.toResponse(tarefaCrudUseCase.buscarTarefa(id)));
    }

    @Override
    public ResponseEntity<TarefaResponseDto> atualizarParcialmente(Long id, TarefaPatchRequestDto request) {
        LOGGER.info("Recebida requisicao para atualizar parcialmente tarefa {}", id);
        var tarefaPatch = tarefaRestMapper.toDomainPatch(request);
        var tarefaAtualizada = tarefaCrudUseCase.atualizarParcialmente(id, tarefaPatch);
        return ResponseEntity.ok(tarefaRestMapper.toResponse(tarefaAtualizada));
    }

    @Override
    public ResponseEntity<ApiMessageDto> desabilitar(Long id) {
        LOGGER.info("Recebida requisicao para desabilitar tarefa {}", id);
        tarefaCrudUseCase.desabilitarTarefa(id);
        return ResponseEntity.ok(new ApiMessageDto("Tarefa desabilitada com sucesso"));
    }

    @Override
    public ResponseEntity<ApiMessageDto> habilitar(Long id) {
        LOGGER.info("Recebida requisicao para habilitar tarefa {}", id);
        tarefaCrudUseCase.habilitarTarefa(id);
        return ResponseEntity.ok(new ApiMessageDto("Tarefa habilitada com sucesso"));
    }

    @Override
    public ResponseEntity<ApiMessageDto> remover(Long id) {
        LOGGER.info("Recebida requisicao para remover tarefa {}", id);
        tarefaCrudUseCase.removerTarefa(id);
        return ResponseEntity.ok(new ApiMessageDto("Tarefa removida com sucesso"));
    }
}
