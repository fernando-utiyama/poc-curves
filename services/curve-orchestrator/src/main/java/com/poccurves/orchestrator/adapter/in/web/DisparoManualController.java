package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.DisparoManualService;
import com.poccurves.orchestrator.dto.OrchestratorDtos.DisparoManualRequest;
import com.poccurves.orchestrator.dto.OrchestratorDtos.DisparoManualResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para disparo manual de conjuntos de insumo.
 * <p>
 * Expõe o endpoint {@code POST /api/v1/disparos/manual} consumido pelo {@code curve-bff}.
 */
@RestController
@RequestMapping("/api/v1/disparos")
public class DisparoManualController {

    private final DisparoManualService service;

    public DisparoManualController(DisparoManualService service) {
        this.service = service;
    }

    @PostMapping("/manual")
    public ResponseEntity<DisparoManualResponse> disparoManual(
            @RequestBody DisparoManualRequest request,
            @RequestHeader(value = "X-User", required = false) String usuario
    ) {
        return ResponseEntity.ok(service.disparoManual(request, usuario != null ? usuario : "operador"));
    }
}
