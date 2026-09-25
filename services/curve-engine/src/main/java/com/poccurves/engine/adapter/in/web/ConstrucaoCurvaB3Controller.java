package com.poccurves.engine.adapter.in.web;
import com.poccurves.engine.application.service.ConstrucaoCurvaB3Service;

import com.poccurves.engine.dto.EngineDtos.ConstrucaoCurvaB3Request;
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

/**
 * Despacho de construção das 5 curvas TS B3 (openspec/changes/b3-additional-curves), schema
 * legado (V22) — mesmo padrão fire-and-forget/202 de {@link ConstrucaoCurvaController}, chamado
 * pelo curve-orchestrator logo após o curve-processor gravar os vértices em tBtrsCurvaPrimr.
 */
@RestController
@RequestMapping("/api/v1/curvas-b3")
public class ConstrucaoCurvaB3Controller {

    private static final Logger log = LoggerFactory.getLogger(ConstrucaoCurvaB3Controller.class);

    private final ConstrucaoCurvaB3Service construcaoCurvaB3Service;
    private final Executor executor;

    public ConstrucaoCurvaB3Controller(
            ConstrucaoCurvaB3Service construcaoCurvaB3Service,
            @Qualifier("construcaoCurvaExecutor") Executor executor) {
        this.construcaoCurvaB3Service = construcaoCurvaB3Service;
        this.executor = executor;
    }

    @PostMapping("/construir")
    public ResponseEntity<Void> construir(@RequestBody ConstrucaoCurvaB3Request request) {
        if (request == null
                || request.tickerIndcd() == null || request.tickerIndcd().isBlank()
                || request.referenceDate() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            executor.execute(() -> {
                try {
                    construcaoCurvaB3Service.construir(request.tickerIndcd(), request.referenceDate());
                } catch (Exception e) {
                    log.error("Falha ao construir curva TS B3 tickerIndcd={} referenceDate={}",
                            request.tickerIndcd(), request.referenceDate(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Pool de execução esgotado ao submeter construção TS B3 para tickerIndcd={}",
                    request.tickerIndcd(), e);
        }

        return ResponseEntity.accepted().build();
    }
}
