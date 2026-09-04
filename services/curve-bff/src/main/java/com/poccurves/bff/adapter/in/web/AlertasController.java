package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.AlertasSumarioResponse;
import com.poccurves.bff.application.AlertasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AlertasController {

    private final AlertasService alertasService;

    public AlertasController(AlertasService alertasService) {
        this.alertasService = alertasService;
    }

    @GetMapping("/alertas/sumario")
    public ResponseEntity<AlertasSumarioResponse> getAlertasSumario() {
        return ResponseEntity.ok(alertasService.obterAlertasSumario());
    }
}
