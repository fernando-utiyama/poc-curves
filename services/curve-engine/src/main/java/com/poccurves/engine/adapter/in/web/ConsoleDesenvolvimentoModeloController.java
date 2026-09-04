package com.poccurves.engine.adapter.in.web;

import com.poccurves.engine.application.ConsoleDesenvolvimentoModeloService;
import com.poccurves.engine.dto.EngineDtos.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console local de desenvolvimento de modelo (tarefa 7.11) — testa um script Groovy contra
 * insumos informados na hora, sem persistir. {@code @Profile("local")}: nunca registrado fora
 * do perfil local, então não existe em nenhum ambiente implantado (compose/produção).
 */
@RestController
@RequestMapping("/api/v1/dev/console-modelo")
@Profile("local")
public class ConsoleDesenvolvimentoModeloController {

    private final ConsoleDesenvolvimentoModeloService service;

    public ConsoleDesenvolvimentoModeloController(ConsoleDesenvolvimentoModeloService service) {
        this.service = service;
    }

    @PostMapping("/testar")
    public ResponseEntity<TestarScriptGroovyResponse> testar(@RequestBody TestarScriptGroovyRequest request) {
        return ResponseEntity.ok(service.testar(request));
    }
}
