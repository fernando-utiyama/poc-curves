package com.poccurves.api.adapter.in.web;

import com.poccurves.api.application.CurvaConsultaService;
import com.poccurves.api.dto.ApiDtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/curvas")
public class CurvaConsultaController {

    private final CurvaConsultaService service;

    public CurvaConsultaController(CurvaConsultaService service) {
        this.service = service;
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<CurvaPublicadaResponse> consultarCurva(
            @PathVariable String codigo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam(defaultValue = "FECHAMENTO") String momento,
            @RequestParam(required = false) Integer versao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf
    ) {
        return ResponseEntity.ok(service.consultarCurvaPublicada(codigo, dataReferencia, momento, versao, asOf));
    }

    @GetMapping("/{codigo}/vertices")
    public ResponseEntity<VerticesPaginadosResponse> listarVertices(
            @PathVariable String codigo,
            @RequestParam UUID versaoCurvaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanhoPagina
    ) {
        return ResponseEntity.ok(service.listarVerticesPaginados(codigo, versaoCurvaId, pagina, tamanhoPagina));
    }

    @GetMapping("/{codigo}/versoes")
    public ResponseEntity<HistoricoVersoesCurvaResponse> listarHistoricoVersoes(
            @PathVariable String codigo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam(defaultValue = "FECHAMENTO") String momento
    ) {
        return ResponseEntity.ok(service.listarHistoricoVersoes(codigo, dataReferencia, momento));
    }

    @GetMapping("/{codigo}/versoes/{versaoId}/procedencia")
    public ResponseEntity<ProcedenciaCurvaResponse> obterProcedencia(
            @PathVariable String codigo,
            @PathVariable UUID versaoId
    ) {
        return ResponseEntity.ok(service.obterProcedencia(codigo, versaoId));
    }

    @PostMapping("/{codigo}/interpolacao")
    public ResponseEntity<InterpolacaoResponse> interpolarCurva(
            @PathVariable String codigo,
            @RequestBody InterpolacaoRequest request
    ) {
        return ResponseEntity.ok(service.interpolarCurva(codigo, request));
    }

    @PostMapping("/comparacao")
    public ResponseEntity<ComparacaoCurvasResponse> compararCurvas(
            @RequestBody ComparacaoCurvasRequest request
    ) {
        return ResponseEntity.ok(service.compararCurvas(request));
    }
}
