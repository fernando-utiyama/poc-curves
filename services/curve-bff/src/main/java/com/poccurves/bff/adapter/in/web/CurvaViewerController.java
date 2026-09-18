package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.CurvaViewerResponse;
import com.poccurves.bff.application.CurvaViewerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/curvas")
public class CurvaViewerController {

    private final CurvaViewerService viewerService;

    public CurvaViewerController(CurvaViewerService viewerService) {
        this.viewerService = viewerService;
    }

    @GetMapping("/{ticker}/viewer")
    public ResponseEntity<CurvaViewerResponse> getCurvaViewer(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam(defaultValue = "FECHAMENTO") String momento
    ) {
        return ResponseEntity.ok(viewerService.obterCurvaViewer(ticker, dataReferencia, momento));
    }
}
