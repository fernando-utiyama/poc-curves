package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.BbergCurvaPrimrVerticeRequest;
import br.com.poc.adapter.in.api.rest.dto.BbergCurvaPrimrVerticeResponse;
import br.com.poc.adapter.in.api.rest.dto.CurvaPrimrDataBaseResponse;
import br.com.poc.adapter.in.api.rest.dto.VerticesPrimrDaDataResponse;
import br.com.poc.application.port.in.usecase.BbergCurvaPrimrUseCase;
import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/curvas-mercado", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Curva Primária Bloomberg", description = "Manutenção de dado bruto da Bloomberg (tBbergCurvaPrimr)")
public class BbergCurvaPrimrController {

    private final BbergCurvaPrimrUseCase useCase;

    @GetMapping("/primaria-bloomberg")
    @Operation(summary = "Listagem geral para seleção",
        description = "Datas com dado bruto por curva. Sem 'de' e 'ate', a última data de cada curva")
    public ResponseEntity<List<CurvaPrimrDataBaseResponse>> listarDatasBase(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String nome) {
        return ResponseEntity.ok(useCase.listarDatasBase(de, ate, codigo, nome).stream()
            .map(CurvaPrimrDataBaseResponse::fromDomain)
            .toList());
    }

    @GetMapping("/{nome}/primaria-bloomberg/{dataBase}")
    @Operation(summary = "Consulta dos vértices de uma data", description = "Vértices do dado bruto e se a data já foi construída")
    public ResponseEntity<VerticesPrimrDaDataResponse<BbergCurvaPrimrVerticeResponse>> consultar(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        VerticesPrimrDaData<BbergCurvaPrimr> resultado = useCase.consultar(nome, dataBase);
        return ResponseEntity.ok(new VerticesPrimrDaDataResponse<>(resultado.curvaConstruida(),
            resultado.vertices().stream().map(BbergCurvaPrimrVerticeResponse::fromDomain).toList()));
    }

    @PostMapping("/{nome}/primaria-bloomberg/{dataBase}/vertices")
    @Operation(summary = "Incluir vértice", description = "Não reconstrói a curva; recalcule na tela Curvas se a data já foi construída")
    public ResponseEntity<BbergCurvaPrimrVerticeResponse> incluir(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @RequestBody BbergCurvaPrimrVerticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BbergCurvaPrimrVerticeResponse.fromDomain(useCase.incluir(nome, dataBase, request.toDomain())));
    }

    @PutMapping("/{nome}/primaria-bloomberg/{dataBase}/vertices/{id}")
    @Operation(summary = "Alterar vértice", description = "Não reconstrói a curva; recalcule na tela Curvas se a data já foi construída")
    public ResponseEntity<BbergCurvaPrimrVerticeResponse> alterar(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @PathVariable Integer id,
            @RequestBody BbergCurvaPrimrVerticeRequest request) {
        return ResponseEntity.ok(BbergCurvaPrimrVerticeResponse.fromDomain(useCase.alterar(nome, dataBase, id, request.toDomain())));
    }

    @DeleteMapping("/{nome}/primaria-bloomberg/{dataBase}/vertices/{id}")
    @Operation(summary = "Apagar vértice")
    public ResponseEntity<Void> excluir(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @PathVariable Integer id) {
        useCase.excluir(nome, dataBase, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{nome}/primaria-bloomberg/{dataBase}")
    @Operation(summary = "Apagar todos os vértices da data", description = "Recusado se a data já foi construída (apague antes a curva construída)")
    public ResponseEntity<Void> excluirData(
            @PathVariable String nome,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        useCase.excluirData(nome, dataBase);
        return ResponseEntity.ok().build();
    }
}
