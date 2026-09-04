package com.poccurves.orchestrator.adapter.in.web;

import com.poccurves.orchestrator.application.CargaManualService;
import com.poccurves.orchestrator.dto.OrchestratorDtos.CargaManualResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Controller REST para carga manual de arquivos de curva.
 * <p>
 * Implementa os endpoints HTTP consumidos pelo {@code curve-bff} (tarefas 5.1, 5.2, 5.3, 5.6).
 * Expõe {@code POST /api/v1/curvas/carga-manual} via multipart form-data.
 */
@RestController
@RequestMapping("/api/v1/curvas/carga-manual")
public class CargaManualController {

    private final CargaManualService service;

    public CargaManualController(CargaManualService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaManualResponse> carregarCurva(
            @RequestParam String codigo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam String momento,
            @RequestParam String justificativa,
            @RequestParam MultipartFile arquivo,
            @RequestHeader(value = "X-User", required = false) String usuario
    ) throws IOException {
        return ResponseEntity.ok(service.carregarCurva(
                codigo, dataReferencia, momento, justificativa,
                arquivo.getBytes(), arquivo.getOriginalFilename(),
                usuario != null ? usuario : "operador"));
    }
}
