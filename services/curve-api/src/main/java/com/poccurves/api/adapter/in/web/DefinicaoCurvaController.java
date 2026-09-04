package com.poccurves.api.adapter.in.web;

import com.poccurves.api.application.DefinicaoCurvaService;
import com.poccurves.api.dto.ApiDtos.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/curvas/definicoes")
public class DefinicaoCurvaController {

    private final DefinicaoCurvaService service;

    public DefinicaoCurvaController(DefinicaoCurvaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<CatalogoDefinicoesResponse> listarDefinicoes(
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String moeda,
            @RequestParam(required = false) String modoOrigem,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanhoPagina
    ) {
        return ResponseEntity.ok(service.listarDefinicoes(codigo, nome, moeda, modoOrigem, estado, pagina, tamanhoPagina));
    }

    @PostMapping
    public ResponseEntity<DefinicaoCurvaResponse> criarDefinicao(
            @RequestBody CriarDefinicaoRequest req,
            @RequestHeader(value = "X-User", required = false) String usuario
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.criarDefinicao(req, usuario != null ? usuario : "operador"));
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<DefinicaoCurvaResponse> obterDefinicao(
            @PathVariable String codigo,
            @RequestParam(required = false) Integer numeroVersao
    ) {
        return ResponseEntity.ok(service.obterDefinicao(codigo, numeroVersao));
    }

    @PutMapping("/{codigo}")
    public ResponseEntity<DefinicaoCurvaResponse> atualizarDefinicao(
            @PathVariable String codigo,
            @RequestBody AtualizarDefinicaoRequest req
    ) {
        return ResponseEntity.ok(service.atualizarDefinicao(codigo, req));
    }

    @GetMapping("/{codigo}/versoes")
    public ResponseEntity<HistoricoVersoesDefinicaoResponse> listarHistoricoVersoes(
            @PathVariable String codigo
    ) {
        return ResponseEntity.ok(service.listarHistoricoVersoes(codigo));
    }

    @GetMapping("/{codigo}/modelo-carga")
    public ResponseEntity<?> baixarModeloCarga(
            @PathVariable String codigo,
            @RequestParam(defaultValue = "CSV") String formato
    ) {
        if ("XLSX".equalsIgnoreCase(formato)) {
            byte[] bytes = service.gerarModeloCargaXlsx(codigo);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo_carga_" + codigo + ".xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } else {
            String csv = service.gerarModeloCargaCsv(codigo);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo_carga_" + codigo + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv.getBytes(StandardCharsets.UTF_8));
        }
    }
}
