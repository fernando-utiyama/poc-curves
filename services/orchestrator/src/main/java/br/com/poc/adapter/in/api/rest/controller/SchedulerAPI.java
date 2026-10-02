package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.scheduler.ApiMessageDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.RecuperarStatusResponseDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.SchedulerStatusDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.SchedulerTaskStatusDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <summary>
 * Interface responsável por definir o contrato OpenAPI do Scheduler.
 *
 * Esta interface contém apenas anotações Swagger e contratos HTTP,
 * sem conter lógica de negócio.
 *
 * Segue o padrão:
 * Controller (Swagger) -> Controller Impl -> UseCase
 * </summary>
 */
@Tag(name = "Agendador - V1", description = "Gerenciamento de tarefas agendadas")
@RequestMapping("/api/v1/agendador")
public interface SchedulerAPI {
    @Operation(summary = "Inicia o agendador global")
    @PostMapping("/iniciar")
    ResponseEntity<Void> start();

    @Operation(summary = "Para o agendador global")
    @PostMapping("/parar")
    ResponseEntity<Void> stop();

    @Operation(summary = "Consulta status global do agendador")
    @GetMapping("/status")
    ResponseEntity<SchedulerStatusDto> status(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String nomeTarefa
    );

    @Operation(summary = "Agenda uma tarefa especifica")
    @PostMapping("/tarefas/{id}/agendar")
    ResponseEntity<ApiMessageDto> agendar(@PathVariable Long id);

    @Operation(
        summary = "Remove o agendamento técnico de uma tarefa",
        description = "Endpoint operacional do scheduler. A chamada so deve ser usada quando o estado atual da tarefa tambem permite transicao para PRONTA. " +
            "Na implementacao atual, chamadas sobre tarefas em EXECUTANDO ou AGENDADA sao rejeitadas; para tarefa em EXECUTANDO use cancelar."
    )
    @DeleteMapping("/tarefas/{id}/agendamento")
    ResponseEntity<ApiMessageDto> desagendar(@PathVariable Long id);

    @Operation(summary = "Executa uma tarefa específica")
    @PostMapping("/tarefas/{id}/executar")
    ResponseEntity<ApiMessageDto> executar(@PathVariable Long id);

    @Operation(
        summary = "Cancela uma tarefa em execução",
        description = "Operacao valida apenas para tarefas no estado EXECUTANDO. " +
            "Chamadas sobre tarefas em PRONTA, AGENDADA, FINALIZADA, ERRO, CANCELADA, DESABILITADA ou REMOVIDA devem ser rejeitadas com erro de transicao invalida."
    )
    @PostMapping("/tarefas/{id}/cancelar")
    ResponseEntity<ApiMessageDto> cancelar(@PathVariable Long id);

    @Operation(summary = "Reseta uma tarefa para o estado PRONTA")
    @PostMapping("/tarefas/{id}/resetar")
    ResponseEntity<ApiMessageDto> resetar(@PathVariable Long id);

    @Operation(summary = "Consulta status de uma tarefa específica")
    @GetMapping("/tarefas/{id}/status")
    ResponseEntity<SchedulerTaskStatusDto> statusTarefa(@PathVariable Long id);

    @Operation(summary = "Recupera o status detalhado de todas as tarefas")
    @GetMapping("/agendador-status")
    ResponseEntity<List<RecuperarStatusResponseDto>> recuperarStatusDetalhado();

    @Operation(summary = "Recupera o status detalhado de uma tarefa")
    @GetMapping("/{id}/agendador-status")
    ResponseEntity<RecuperarStatusResponseDto> recuperarStatusDetalhado(
        @PathVariable @Positive Long id
    );
}
