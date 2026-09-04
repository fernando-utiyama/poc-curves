package com.poccurves.engine.adapter.in.web;
import com.poccurves.engine.application.usecase.InterpolacaoService;

import com.poccurves.engine.dto.EngineDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/curvas")
public class InterpolacaoController {

    private final InterpolacaoService interpolacaoService;

    public InterpolacaoController(InterpolacaoService interpolacaoService) {
        this.interpolacaoService = interpolacaoService;
    }

    @PostMapping("/{codigo}/interpolacao")
    public ResponseEntity<Object> interpolar(@PathVariable String codigo, @RequestBody InterpolacaoRequest request) {
        return interpolacaoService.interpolar(codigo, request)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "mensagem", "curva não encontrada para codigo=" + codigo
                                + ", dataReferencia=" + request.dataReferencia()
                                + ", momento=" + (request.momento() != null ? request.momento() : "FECHAMENTO")
                                + (request.versao() != null ? ", versao=" + request.versao() : "")
                )));
    }
}
