package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.application.port.out.EnginePort.RespostaEngine;
import br.com.poc.application.service.ApagarConstrucaoService;
import br.com.poc.application.service.CurvaMercadoAcoesService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Ações da curva", description = "Repasse ao engine para a tela Curvas")
public class CurvaMercadoAcoesController {

    private final CurvaMercadoAcoesService service;
    private final ApagarConstrucaoService apagarConstrucaoService;

    @PostMapping("/construcao")
    public ResponseEntity<String> construir(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @RequestParam(required = false) Boolean forcarRecalculo,
            @RequestParam(required = false) String fonte,
            @RequestParam(required = false) String produto) {
        return repassar(service.construir(nome, dataBase, forcarRecalculo, fonte, produto, cid()));
    }

    @PostMapping("/interpolada")
    public ResponseEntity<String> regravar(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return repassar(service.regravarInterpolada(nome, dataBase, cid()));
    }

    @GetMapping("/vertices")
    public ResponseEntity<String> vertices(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        return repassar(service.consultarVertices(nome, dataBase, cid()));
    }

    @DeleteMapping("/vertices")
    @Operation(summary = "Apagar curva construída da data",
        description = "Apaga os vértices construídos da data e, em cascata, a interpolada dela. Não toca o dado bruto, "
            + "a configuração nem o cadastro. A data volta a ficar não construída e pode ser construída de novo")
    public ResponseEntity<Void> apagarConstrucao(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        apagarConstrucaoService.apagar(nome, dataBase);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/interpolacao")
    public ResponseEntity<String> interpolar(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            HttpServletRequest request) {
        return repassar(service.interpolar(nome, dataBase, request.getQueryString(), cid()));
    }

    private ResponseEntity<String> repassar(RespostaEngine r) {
        return ResponseEntity.status(r.status()).contentType(MediaType.APPLICATION_JSON).body(r.corpoJson());
    }

    private static String cid() {
        String c = MDC.get("correlationId");
        return c != null ? c : UUID.randomUUID().toString();
    }
}
