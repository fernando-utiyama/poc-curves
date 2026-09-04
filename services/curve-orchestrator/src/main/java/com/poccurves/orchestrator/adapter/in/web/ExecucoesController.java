package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.ExecucoesService;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ExecucoesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Controller REST para consulta de execuções com filtros e paginação.
 * <p>
 * Implementa as tarefas 11.1 e 11.2 do backlog curve-orchestrator,
 * expondo o endpoint {@code GET /api/v1/execucoes} consumido pelo {@code curve-bff}.
 */
@RestController
@RequestMapping("/api/v1/execucoes")
public class ExecucoesController {

    private final ExecucoesService service;

    public ExecucoesController(ExecucoesService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ExecucoesResponse> listarExecucoes(
            @RequestParam(required = false) String codigoCurva,
            @RequestParam(required = false) LocalDate dataReferencia,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return ResponseEntity.ok(service.listarExecucoes(codigoCurva, dataReferencia, estado, pagina, tamanho));
    }
}
