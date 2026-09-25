package com.poccurves.api.adapter.in.web;

import com.poccurves.api.application.CurvaDadosService;
import com.poccurves.api.application.CurvaMercadoService;
import com.poccurves.api.dto.ApiDtos.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/curvas")
public class CurvaMercadoController {

    private final CurvaMercadoService curvaMercadoService;
    private final CurvaDadosService curvaDadosService;

    public CurvaMercadoController(CurvaMercadoService curvaMercadoService, CurvaDadosService curvaDadosService) {
        this.curvaMercadoService = curvaMercadoService;
        this.curvaDadosService = curvaDadosService;
    }

    @GetMapping
    public ResponseEntity<CatalogoCurvasResponse> listarCurvas() {
        return ResponseEntity.ok(curvaMercadoService.listarTodas());
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<CurvaMercadoResponse> obterCurva(@PathVariable String ticker) {
        return ResponseEntity.ok(curvaMercadoService.obterPorTicker(ticker));
    }

    @GetMapping("/{ticker}/vertices")
    public ResponseEntity<CurvaDadosResponse> obterVertices(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia
    ) {
        return ResponseEntity.ok(curvaDadosService.consultarVertices(ticker, dataReferencia));
    }

    @GetMapping("/{ticker}/curva")
    public ResponseEntity<CurvaDadosResponse> obterCurvaConstruida(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia
    ) {
        return ResponseEntity.ok(curvaDadosService.consultarCurvaConstruida(ticker, dataReferencia));
    }

    @PostMapping("/comparacao")
    public ResponseEntity<ComparacaoCurvasResponse> compararCurvas(@RequestBody ComparacaoCurvasRequest request) {
        return ResponseEntity.ok(curvaDadosService.compararCurvas(request));
    }
}
