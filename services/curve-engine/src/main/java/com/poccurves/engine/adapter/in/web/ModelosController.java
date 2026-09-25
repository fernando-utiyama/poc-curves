package com.poccurves.engine.adapter.in.web;
import com.poccurves.engine.application.usecase.ImportarModeloGroovyService;
import com.poccurves.engine.application.usecase.ListarModelosService;

import com.poccurves.engine.dto.EngineDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/modelos")
public class ModelosController {

    private final ListarModelosService listarModelosService;
    private final ImportarModeloGroovyService importarModeloGroovyService;

    public ModelosController(
            ListarModelosService listarModelosService,
            ImportarModeloGroovyService importarModeloGroovyService) {
        this.listarModelosService = listarModelosService;
        this.importarModeloGroovyService = importarModeloGroovyService;
    }

    @GetMapping
    public ResponseEntity<ModelosResponse> listar() {
        return ResponseEntity.ok(listarModelosService.listarModelos());
    }

    @PostMapping("/validar-groovy")
    public ResponseEntity<ImportarModeloResponse> validarGroovy(
            @RequestBody ImportarModeloGroovyRequest request,
            @RequestHeader(value = "X-User", defaultValue = "sistema") String usuario) {
        return ResponseEntity.ok(importarModeloGroovyService.importar(request, usuario));
    }
}
