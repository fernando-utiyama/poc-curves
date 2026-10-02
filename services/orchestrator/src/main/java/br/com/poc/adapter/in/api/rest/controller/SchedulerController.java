package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.scheduler.ApiMessageDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.RecuperarStatusResponseDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.SchedulerStatusDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.SchedulerTaskStatusDto;
import br.com.poc.adapter.in.api.rest.mapper.scheduler.SchedulerMapper;
import br.com.poc.application.port.in.RecuperarStatusUseCase;
import br.com.poc.application.port.in.SchedulerUseCase;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <summary>
 * Controller REST responsável por expor os endpoints do Scheduler.
 * <p>
 * Fluxo arquitetural:
 * Controller -> UseCase -> Service -> Repository
 * <p>
 * O Controller NÃO contém regra de negócio.
 * Ele apenas delega ao UseCase.
 * </summary>
 */
@RestController
@AllArgsConstructor
public class SchedulerController implements SchedulerAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerController.class);

    private final SchedulerMapper schedulerMapper;
    private final SchedulerUseCase schedulerUseCase;
    private final RecuperarStatusUseCase recuperarStatusUseCase;


    @Override
    public ResponseEntity<Void> start() {
        LOGGER.debug("Recebida requisição para iniciar tarefas");
        schedulerUseCase.startAll();
        LOGGER.info("Tarefas iniciadas com sucesso");
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> stop() {
        LOGGER.debug("Recebida requisição para parar tarefas");
        schedulerUseCase.stopAll();
        LOGGER.info("Tarefas paradas com sucesso");
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<SchedulerStatusDto> status(String status, String nomeTarefa) {
        LOGGER.debug("Recebida requisição para consultar status global");
        var domainStatus = schedulerUseCase.status(status, nomeTarefa);
        var dto = schedulerMapper.toDto(domainStatus);
        LOGGER.info("Status global retornado com sucesso");
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<ApiMessageDto> agendar(Long id) {
        LOGGER.debug("Recebida requisição para agendar tarefa {}", id);
        schedulerUseCase.scheduleTask(id);
        LOGGER.info("Tarefa {} agendada com sucesso", id);
        return ResponseEntity.ok(new ApiMessageDto("Tarefa agendada com sucesso"));
    }

    @Override
    public ResponseEntity<ApiMessageDto> desagendar(Long id) {
        LOGGER.debug("Recebida requisição para desagendar tarefa {}", id);
        schedulerUseCase.stopTask(id);
        LOGGER.info("Tarefa {} desagendada com sucesso", id);
        return ResponseEntity.ok(new ApiMessageDto("Tarefa desagendada com sucesso"));
    }

    @Override
    public ResponseEntity<ApiMessageDto> executar(Long id) {
        LOGGER.debug("Recebida requisição para executar tarefa {}", id);
        schedulerUseCase.executeTask(id);
        LOGGER.info("Tarefa {} executada com sucesso", id);
        return ResponseEntity.ok(new ApiMessageDto("Execução solicitada com sucesso"));
    }

    @Override
    public ResponseEntity<ApiMessageDto> cancelar(Long id) {
        LOGGER.debug("Recebida requisição para cancelar tarefa {}", id);
        schedulerUseCase.cancelTask(id);
        LOGGER.info("Tarefa {} cancelada com sucesso", id);
        return ResponseEntity.ok(new ApiMessageDto("Tarefa cancelada com sucesso"));
    }

    @Override
    public ResponseEntity<ApiMessageDto> resetar(Long id) {
        LOGGER.debug("Recebida requisição para resetar tarefa {}", id);
        schedulerUseCase.resetTask(id);
        LOGGER.info("Tarefa {} resetada com sucesso", id);
        return ResponseEntity.ok(new ApiMessageDto("Tarefa resetada com sucesso"));
    }

    @Override
    public ResponseEntity<SchedulerTaskStatusDto> statusTarefa(Long id) {
        LOGGER.debug("Recebida requisição para consultar status da tarefa {}", id);
        var domainStatus = schedulerUseCase.getTaskStatus(id);
        var dto = schedulerMapper.toDto(domainStatus);
        LOGGER.info("Status da tarefa {} retornado com sucesso", id);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<List<RecuperarStatusResponseDto>> recuperarStatusDetalhado() {
        return ResponseEntity.ok(recuperarStatusUseCase.listarStatusDetalhado());
    }

    @Override
    public ResponseEntity<RecuperarStatusResponseDto> recuperarStatusDetalhado(Long id) {
        RecuperarStatusResponseDto response = recuperarStatusUseCase.recuperarStatusDetalhado(id);
        return ResponseEntity.ok(response);
    }
}
