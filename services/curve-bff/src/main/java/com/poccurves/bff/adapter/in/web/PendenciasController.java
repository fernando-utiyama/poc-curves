package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.AcaoPendenciaResponse;
import com.poccurves.bff.dto.BffDtos.DescartarPendenciaRequest;
import com.poccurves.bff.dto.BffDtos.PendenciasDetalheResponse;
import com.poccurves.bff.dto.BffDtos.PendenciasSumarioResponse;
import com.poccurves.bff.application.PendenciasService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pendencias/dlq")
public class PendenciasController {

    private final PendenciasService pendenciasService;

    public PendenciasController(PendenciasService pendenciasService) {
        this.pendenciasService = pendenciasService;
    }

    @GetMapping
    public ResponseEntity<PendenciasSumarioResponse> getSumario() {
        return ResponseEntity.ok(pendenciasService.getSumario());
    }

    @GetMapping("/grupos/{grupoId}")
    public ResponseEntity<PendenciasDetalheResponse> getDetalhe(
            @PathVariable UUID grupoId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return ResponseEntity.ok(pendenciasService.getDetalhe(grupoId, pagina, tamanho));
    }

    @PostMapping("/reprocessar")
    @PreAuthorize("hasAnyRole('CURVE_OPERATOR', 'CURVE_ADMIN')")
    public ResponseEntity<AcaoPendenciaResponse> reprocessar(
            @RequestParam(required = false) UUID grupoId,
            @RequestParam(required = false) UUID pendenciaId
    ) {
        return ResponseEntity.ok(pendenciasService.reprocessar(grupoId, pendenciaId));
    }

    @PostMapping("/descartar")
    @PreAuthorize("hasAnyRole('CURVE_OPERATOR', 'CURVE_ADMIN')")
    public ResponseEntity<AcaoPendenciaResponse> descartar(
            @RequestParam(required = false) UUID grupoId,
            @RequestParam(required = false) UUID pendenciaId,
            @RequestBody DescartarPendenciaRequest request
    ) {
        return ResponseEntity.ok(pendenciasService.descartar(grupoId, pendenciaId, request.justificativa()));
    }
}
