package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.CalcularCurvaRequest;
import br.com.poc.adapter.in.api.rest.dto.CalcularCurvaResponse;
import br.com.poc.application.port.in.CalcularCurvaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calculo")
@Tag(name = "Cálculo", description = "Interpolação de funções e extrapolação analítica sob demanda")
public class CurvaCalculoController {

    private final CalcularCurvaUseCase calcularCurvaUseCase;

    public CurvaCalculoController(CalcularCurvaUseCase calcularCurvaUseCase) {
        this.calcularCurvaUseCase = calcularCurvaUseCase;
    }

    @PostMapping
    @Operation(summary = "Interpola taxas e fatores para prazos contínuos arbitrários em tempo real")
    public ResponseEntity<CalcularCurvaResponse> calcular(
        @RequestBody CalcularCurvaRequest request,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        String corrId = (correlationId != null && !correlationId.isBlank()) ? correlationId : UUID.randomUUID().toString();

        var resultado = calcularCurvaUseCase.executar(
            request.ticker(),
            request.dataBase(),
            request.base(),
            request.metodoInterpolacao(),
            request.politicaExtrapolacao(),
            request.prazos()
        );

        return ResponseEntity.ok(new CalcularCurvaResponse(
            resultado.ticker(),
            resultado.dataBase(),
            resultado.base(),
            resultado.metodo().name(),
            resultado.politica().name(),
            resultado.pontos(),
            corrId
        ));
    }
}
