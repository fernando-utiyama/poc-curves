package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.CargaManualResponse;
import com.poccurves.bff.application.CargaManualService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class CargaManualController {

    private final CargaManualService cargaService;

    public CargaManualController(CargaManualService cargaService) {
        this.cargaService = cargaService;
    }

    @PostMapping(value = {"/acoes/carga-manual", "/curvas/{codigo}/carga-manual"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CURVE_OPERATOR', 'CURVE_ADMIN')")
    public ResponseEntity<CargaManualResponse> carregarCurva(
            @PathVariable(required = false) String codigo,
            @RequestParam(required = false) String codigoParam,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam(defaultValue = "FECHAMENTO") String momento,
            @RequestParam String justificativa,
            @RequestParam("arquivo") MultipartFile arquivo
    ) throws IOException {
        String cod = codigo != null ? codigo : codigoParam;
        return ResponseEntity.ok(cargaService.carregarCurva(
                cod, dataReferencia, momento, justificativa, arquivo.getBytes(), arquivo.getOriginalFilename()));
    }
}

