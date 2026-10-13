package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.application.port.in.usecase.CurvaMercadoAcoesUseCase;
import br.com.poc.domain.cadastro.RespostaEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/curvas-mercado/{nome}/{dataBase}")
@RequiredArgsConstructor
@ApiResponses({
    @ApiResponse(responseCode = "404", description = "Curva não encontrada"),
    @ApiResponse(responseCode = "422", description = "Curva sem código ou parâmetros inválidos")
})
@Tag(name = "Ações da curva", description = "Repasse ao engine para a tela Curvas")
public class CurvaMercadoAcoesController {

    private final CurvaMercadoAcoesUseCase service;

    @Operation(summary = "Construir curva da data",
        description = "Constrói os vértices e a interpolada. forcarRecalculo=true refaz data já construída")
    @PostMapping("/construcao")
    public ResponseEntity<String> construir(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @Parameter(description = "Refaz a data que já foi construída") @RequestParam(required = false) Boolean forcarRecalculo,
            @Parameter(description = "Provedor da origem secundária (junto com produto)") @RequestParam(required = false) String fonte,
            @Parameter(description = "Produto da origem secundária (junto com fonte)") @RequestParam(required = false) String produto) {
        return responder(service.construir(nome, dataBase, forcarRecalculo, fonte, produto, cid()));
    }

    @Operation(summary = "Regravar interpolada da data",
        description = "Recalcula a interpolada a partir dos vértices já construídos")
    @PostMapping("/interpolada")
    public ResponseEntity<String> regravar(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return responder(service.regravarInterpolada(nome, dataBase, cid()));
    }

    @Operation(summary = "Consultar vértices da data",
        description = "Vértices construídos da curva na data")
    @GetMapping("/vertices")
    public ResponseEntity<String> vertices(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return responder(service.consultarVertices(nome, dataBase, cid()));
    }

    @DeleteMapping("/vertices")
    @Operation(summary = "Apagar curva construída da data",
        description = "Apaga os vértices e a interpolada da data. Dado bruto e configuração não mudam")
    public ResponseEntity<Void> excluirVertices(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        service.excluirVertices(nome, dataBase);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Interpolar prazos da data",
        description = "Taxa nos prazos pedidos (du e/ou data). Só consulta")
    @GetMapping("/interpolacao")
    public ResponseEntity<String> interpolar(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            HttpServletRequest request) {
        return responder(service.interpolar(nome, dataBase, request.getQueryString(), cid()));
    }

    private ResponseEntity<String> responder(RespostaEngine r) {
        return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).body(r.corpoJson());
    }

    private static String cid() {
        String c = MDC.get("correlationId");
        return c != null ? c : UUID.randomUUID().toString();
    }
}
