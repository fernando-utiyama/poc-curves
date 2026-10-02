package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.scheduler.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

    @Tag(name = "Tarefas - V1", description = "CRUD de tarefas")
    @RequestMapping("/api/v1/tarefas")
    public interface TarefaAPI {

        @Operation(summary = "Cria uma nova tarefa")
        @PostMapping
        ResponseEntity<CriarTarefaResponseDto> salvar(@Valid @RequestBody TarefaRequestDto request);

        @Operation(summary = "Lista tarefas")
        @GetMapping
        ResponseEntity<List<TarefaResponseDto>> listar();

        @Operation(summary = "Busca uma tarefa por id")
        @GetMapping("/{id}")
        ResponseEntity<TarefaResponseDto> buscar(@PathVariable Long id);

        @Operation(summary = "Atualiza parcialmente uma tarefa")
        @PatchMapping("/{id}")
        ResponseEntity<TarefaResponseDto> atualizarParcialmente(@PathVariable Long id,
                                                                @RequestBody TarefaPatchRequestDto request);

        @Operation(summary = "Desabilita uma tarefa")
        @PostMapping("/{id}/desabilitar")
        ResponseEntity<ApiMessageDto> desabilitar(@PathVariable Long id);

        @Operation(summary = "Habilita uma tarefa")
        @PostMapping("/{id}/habilitar")
        ResponseEntity<ApiMessageDto> habilitar(@PathVariable Long id);

        @Operation(summary = "Remove logicamente uma tarefa desabilitada")
        @DeleteMapping("/{id}")
        ResponseEntity<ApiMessageDto> remover(@PathVariable Long id);
    }
