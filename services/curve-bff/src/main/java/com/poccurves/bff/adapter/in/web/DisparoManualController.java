package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.BackfillRequest;
import com.poccurves.bff.dto.BffDtos.BackfillResponse;
import com.poccurves.bff.dto.BffDtos.DisparoManualRequest;
import com.poccurves.bff.dto.BffDtos.DisparoManualResponse;
import com.poccurves.bff.application.DisparoManualService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DisparoManualController {

    private final DisparoManualService disparoService;

    public DisparoManualController(DisparoManualService disparoService) {
        this.disparoService = disparoService;
    }

    @PostMapping({"/acoes/disparo-manual", "/ingestao/disparo"})
    @PreAuthorize("hasAnyRole('CURVE_OPERATOR', 'CURVE_ADMIN')")
    public ResponseEntity<DisparoManualResponse> disparoManual(@RequestBody DisparoManualRequest request) {
        return ResponseEntity.ok(disparoService.disparoManual(request));
    }

    @PostMapping({"/acoes/backfill", "/ingestao/backfill"})
    @PreAuthorize("hasAnyRole('CURVE_OPERATOR', 'CURVE_ADMIN')")
    public ResponseEntity<BackfillResponse> backfill(@RequestBody BackfillRequest request) {
        return ResponseEntity.ok(disparoService.backfill(request));
    }
}

