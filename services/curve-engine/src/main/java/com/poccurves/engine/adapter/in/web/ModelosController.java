package com.poccurves.engine.adapter.in.web;

import com.poccurves.engine.application.CompararModelosService;
import com.poccurves.engine.application.ImportarModeloGroovyService;
import com.poccurves.engine.application.ListarModelosService;
import com.poccurves.engine.dto.EngineDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/modelos")
public class ModelosController {

    private final ListarModelosService listarModelosService;
    private final ImportarModeloGroovyService importarModeloGroovyService;
    private final CompararModelosService compararModelosService;

    public ModelosController(
            ListarModelosService listarModelosService,
            ImportarModeloGroovyService importarModeloGroovyService,
            CompararModelosService compararModelosService) {
        this.listarModelosService = listarModelosService;
        this.importarModeloGroovyService = importarModeloGroovyService;
        this.compararModelosService = compararModelosService;
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

    @PostMapping("/comparar")
    public ResponseEntity<ComparacaoResponse> comparar(@RequestBody ComparacaoModelosRequest request) {
        return ResponseEntity.ok(compararModelosService.comparar(request));
    }
}
