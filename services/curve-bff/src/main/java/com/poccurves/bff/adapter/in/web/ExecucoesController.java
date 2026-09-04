package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.ExecucoesResponse;
import com.poccurves.bff.application.ExecucoesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class ExecucoesController {

    private final ExecucoesService execucoesService;

    public ExecucoesController(ExecucoesService execucoesService) {
        this.execucoesService = execucoesService;
    }

    @GetMapping("/execucoes")
    public ResponseEntity<ExecucoesResponse> getExecucoes(
            @RequestParam(required = false) String codigoCurva,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho
    ) {
        return ResponseEntity.ok(execucoesService.getExecucoes(codigoCurva, dataReferencia, estado, pagina, tamanho));
    }
}
