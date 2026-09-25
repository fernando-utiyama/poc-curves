package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.ConstruirCurvaRequest;
import br.com.poc.adapter.in.api.rest.dto.ConstruirCurvaResponse;
import br.com.poc.application.port.in.ConstruirCurvaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/curvas")
@Tag(name = "Curvas", description = "Construção e bootstrap de estruturas a termo")
public class CurvaConstrucaoController {

    private final ConstruirCurvaUseCase construirCurvaUseCase;

    public CurvaConstrucaoController(ConstruirCurvaUseCase construirCurvaUseCase) {
        this.construirCurvaUseCase = construirCurvaUseCase;
    }

    @PostMapping("/construir")
    @Operation(summary = "Constrói a estrutura a termo a partir das configurações e insumos de mercado")
    public ResponseEntity<ConstruirCurvaResponse> construir(
        @RequestBody ConstruirCurvaRequest request,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String corrId = (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();
        boolean forcar = request.forcarRecalculo() != null && request.forcarRecalculo();

        var resultado = construirCurvaUseCase.executar(request.ticker(), request.dataBase(), forcar);

        return ResponseEntity.ok(new ConstruirCurvaResponse(
            resultado.ticker(),
            resultado.dataBase(),
            resultado.estrategiaAplicada(),
            resultado.quantidadeVerticesCalculados(),
            resultado.tempoExecucaoMs(),
            corrId
        ));
    }
}
