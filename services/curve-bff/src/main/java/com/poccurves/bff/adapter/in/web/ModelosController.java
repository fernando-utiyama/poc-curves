package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.*;
import com.poccurves.bff.application.ComparacaoService;
import com.poccurves.bff.application.ModelosService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ModelosController {

    private final ModelosService modelosService;
    private final ComparacaoService comparacaoService;

    public ModelosController(ModelosService modelosService, ComparacaoService comparacaoService) {
        this.modelosService = modelosService;
        this.comparacaoService = comparacaoService;
    }

    @GetMapping("/modelos")
    public ResponseEntity<ModelosResponse> listarModelos() {
        return ResponseEntity.ok(modelosService.listarModelos());
    }

    @PostMapping("/modelos/importar")
    @PreAuthorize("hasRole('CURVE_ADMIN')")
    public ResponseEntity<ImportarModeloResponse> importarModeloGroovy(@RequestBody ImportarModeloGroovyRequest request) {
        return ResponseEntity.ok(modelosService.importarModeloGroovy(request));
    }

    @PostMapping("/modelos/comparar")
    public ResponseEntity<ComparacaoResponse> compararModelos(@RequestBody ComparacaoModelosRequest request) {
        return ResponseEntity.ok(comparacaoService.compararModelos(request));
    }

    @PostMapping({"/curvas/comparacao", "/comparacao"})
    public ResponseEntity<ComparacaoResponse> compararCurvas(@RequestBody ComparacaoCurvasRequest request) {
        return ResponseEntity.ok(comparacaoService.compararCurvas(request));
    }
}

