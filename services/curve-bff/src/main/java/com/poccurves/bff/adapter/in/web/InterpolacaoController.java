package com.poccurves.bff.adapter.in.web;

import com.poccurves.bff.dto.BffDtos.InterpolacaoRequest;
import com.poccurves.bff.dto.BffDtos.InterpolacaoResponse;
import com.poccurves.bff.application.InterpolacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/curvas")
public class InterpolacaoController {

    private final InterpolacaoService interpolacaoService;

    public InterpolacaoController(InterpolacaoService interpolacaoService) {
        this.interpolacaoService = interpolacaoService;
    }

    @PostMapping("/{codigo}/interpolacao")
    public ResponseEntity<InterpolacaoResponse> interpolar(
            @PathVariable String codigo,
            @RequestBody InterpolacaoRequest request
    ) {
        return ResponseEntity.ok(interpolacaoService.interpolar(codigo, request));
    }
}
