package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.CurvaViewerResponse;
import com.poccurves.bff.dto.BffDtos.InterpolacaoRequest;
import com.poccurves.bff.dto.BffDtos.InterpolacaoResponse;
import com.poccurves.bff.application.CurvaViewerService;
import com.poccurves.bff.application.InterpolacaoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/curvas")
public class CurvaViewerController {

    private final CurvaViewerService viewerService;

    public CurvaViewerController(CurvaViewerService viewerService) {
        this.viewerService = viewerService;
    }

    @GetMapping("/{codigo}/viewer")
    public ResponseEntity<CurvaViewerResponse> getCurvaViewer(
            @PathVariable String codigo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam(defaultValue = "FECHAMENTO") String momento,
            @RequestParam(required = false) Integer versao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf
    ) {
        return ResponseEntity.ok(viewerService.obterCurvaViewer(codigo, dataReferencia, momento, versao, asOf));
    }
}
