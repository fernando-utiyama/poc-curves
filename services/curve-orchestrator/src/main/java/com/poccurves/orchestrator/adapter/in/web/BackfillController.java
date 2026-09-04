package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.BackfillService;
import com.poccurves.orchestrator.domain.ExecucaoCurva;
import com.poccurves.orchestrator.domain.ProgressoBackfill;
import com.poccurves.orchestrator.dto.OrchestratorDtos.IniciarBackfillRequest;
import com.poccurves.orchestrator.dto.OrchestratorDtos.IniciarBackfillResponse;
import com.poccurves.orchestrator.dto.OrchestratorDtos.ProgressoBackfillDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backfills")
public class BackfillController {

    private final BackfillService service;

    public BackfillController(BackfillService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IniciarBackfillResponse> iniciarBackfill(
            @RequestBody IniciarBackfillRequest request,
            @RequestHeader(value = "X-User", required = false) String usuario
    ) {
        ExecucaoCurva mae = service.iniciar(
                request.conjuntoDados(),
                request.dataInicial(),
                request.dataFinal(),
                request.concorrenciaMaxima(),
                usuario != null ? usuario : "operador"
        );
        return ResponseEntity.ok(new IniciarBackfillResponse(mae.id(), mae.correlacaoId()));
    }

    @PostMapping("/{id}/interromper")
    public ResponseEntity<Void> interromperBackfill(@PathVariable UUID id) {
        service.interromper(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/progresso")
    public ResponseEntity<ProgressoBackfillDTO> progressoBackfill(@PathVariable UUID id) {
        ProgressoBackfill progresso = service.progresso(id);
        return ResponseEntity.ok(new ProgressoBackfillDTO(
                progresso.total(),
                progresso.concluidas(),
                progresso.semDado(),
                progresso.falhas(),
                progresso.pendentes()
        ));
    }
}
