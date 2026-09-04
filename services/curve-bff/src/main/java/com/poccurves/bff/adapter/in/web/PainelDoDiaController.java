package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.AlertasSumarioResponse;
import com.poccurves.bff.dto.BffDtos.PainelDoDiaResponse;
import com.poccurves.bff.application.AlertasService;
import com.poccurves.bff.application.PainelDoDiaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class PainelDoDiaController {

    private final PainelDoDiaService painelService;

    public PainelDoDiaController(PainelDoDiaService painelService) {
        this.painelService = painelService;
    }

    @GetMapping("/painel-do-dia")
    public ResponseEntity<PainelDoDiaResponse> getPainelDoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia
    ) {
        return ResponseEntity.ok(painelService.obterPainelDoDia(dataReferencia));
    }
}
