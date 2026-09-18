package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.*;
import com.poccurves.bff.application.CatalogoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/curvas")
    public ResponseEntity<CatalogoResponse> getCatalogo() {
        return ResponseEntity.ok(catalogoService.listarCurvas());
    }

    @GetMapping("/curvas/{ticker}")
    public ResponseEntity<CurvaMercadoDTO> getCurva(@PathVariable String ticker) {
        return ResponseEntity.ok(catalogoService.obterCurva(ticker));
    }
}
