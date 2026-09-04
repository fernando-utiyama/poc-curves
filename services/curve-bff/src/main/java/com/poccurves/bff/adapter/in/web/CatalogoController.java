package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.*;
import com.poccurves.bff.application.CatalogoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/catalogo")
    public ResponseEntity<CatalogoResponse> getCatalogo(
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String modoOrigem,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return ResponseEntity.ok(catalogoService.getCatalogo(codigo, modoOrigem, estado, pagina, tamanho));
    }

    @GetMapping("/curvas/{codigo}/definicao")
    public ResponseEntity<DefinicaoCurvaDTO> getDefinicaoCurva(@PathVariable String codigo) {
        return ResponseEntity.ok(catalogoService.getDefinicao(codigo));
    }

    @PostMapping("/curvas/{codigo}/definicao")
    @PreAuthorize("hasRole('CURVE_ADMIN')")
    public ResponseEntity<DefinicaoCurvaDTO> criarDefinicaoCurva(
            @PathVariable String codigo,
            @RequestBody CriarOuAtualizarDefinicaoCurvaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogoService.criarDefinicao(codigo, request));
    }

    @PutMapping("/curvas/{codigo}/definicao")
    @PreAuthorize("hasRole('CURVE_ADMIN')")
    public ResponseEntity<DefinicaoCurvaDTO> atualizarDefinicaoCurva(
            @PathVariable String codigo,
            @RequestBody CriarOuAtualizarDefinicaoCurvaRequest request
    ) {
        return ResponseEntity.ok(catalogoService.atualizarDefinicao(codigo, request));
    }

    @GetMapping("/curvas/{codigo}/modelo-carga")
    public ResponseEntity<?> downloadModeloCarga(
            @PathVariable String codigo,
            @RequestParam(defaultValue = "CSV") String formato
    ) {
        byte[] bytes = catalogoService.downloadModeloCarga(codigo, formato);
        if ("XLSX".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo_carga_" + codigo + ".xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } else {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo_carga_" + codigo + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(bytes);
        }
    }
}
