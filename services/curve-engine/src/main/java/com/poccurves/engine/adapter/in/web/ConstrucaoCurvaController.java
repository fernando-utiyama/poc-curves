package com.poccurves.engine.adapter.in.web;

import com.poccurves.engine.application.PublicacaoCurvaService;
import com.poccurves.engine.dto.EngineDtos.ConstrucaoCurvaRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@RestController
@RequestMapping("/api/v1/construcoes")
public class ConstrucaoCurvaController {

    private static final Logger log = LoggerFactory.getLogger(ConstrucaoCurvaController.class);

    private final PublicacaoCurvaService publicacaoCurvaService;
    private final Executor executor;

    public ConstrucaoCurvaController(
            PublicacaoCurvaService publicacaoCurvaService,
            @Qualifier("construcaoCurvaExecutor") Executor executor) {
        this.publicacaoCurvaService = publicacaoCurvaService;
        this.executor = executor;
    }

    @PostMapping
    public ResponseEntity<Void> construir(@RequestBody ConstrucaoCurvaRequest request) {
        if (request == null
                || request.curveCode() == null || request.curveCode().isBlank()
                || request.referenceDate() == null
                || request.curveMoment() == null || request.curveMoment().isBlank()
                || request.runId() == null
                || request.executionId() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            executor.execute(() -> publicacaoCurvaService.processarPedidoConstrucao(
                    request.curveCode(),
                    request.referenceDate(),
                    request.curveMoment(),
                    request.runId(),
                    request.executionId()
            ));
        } catch (RejectedExecutionException e) {
            log.error("Pool de execução esgotado ao submeter pedido de construção para executionId={}, curveCode={}",
                    request.executionId(), request.curveCode(), e);
        }

        return ResponseEntity.accepted().build();
    }
}
