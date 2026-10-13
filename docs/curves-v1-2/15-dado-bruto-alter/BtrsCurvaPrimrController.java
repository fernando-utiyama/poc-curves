package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.BtrsCurvaPrimrVerticeRequest;
import br.com.poc.adapter.in.api.rest.dto.BtrsCurvaPrimrVerticeResponse;
import br.com.poc.adapter.in.api.rest.dto.CurvaPrimrDataBaseResponse;
import br.com.poc.adapter.in.api.rest.dto.VerticesPrimrDaDataResponse;
import br.com.poc.application.port.in.usecase.BtrsCurvaPrimrUseCase;
import br.com.poc.domain.cadastro.BtrsCurvaPrimr;
import br.com.poc.domain.cadastro.VerticesPrimrDaData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@ApiResponses({
    @ApiResponse(responseCode = "404", description = "Curva ou vértice não encontrado"),
    @ApiResponse(responseCode = "422", description = "Dados inválidos (a mensagem diz qual campo)")
})
@Tag(name = "Curva Primária B3", description = "Manutenção de dado bruto da B3 (tBtrsCurvaPrimr)")
public class BtrsCurvaPrimrController {

    private final BtrsCurvaPrimrUseCase useCase;

    @GetMapping("/primaria-b3")
    @Operation(summary = "Listagem geral para seleção",
        description = "Datas com dado bruto por curva. Sem 'de' e 'ate', a última data de cada curva")
    public ResponseEntity<List<CurvaPrimrDataBaseResponse>> listarDatasBase(
            @Parameter(description = "Início do período (padrão: 30 dias antes do fim)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Fim do período (padrão: hoje); passando de 366 dias responde 400") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @Parameter(description = "Código da curva") @RequestParam(required = false) String codigo,
            @Parameter(description = "Trecho do nome da curva") @RequestParam(required = false) String nome) {
        return ResponseEntity.ok(useCase.listarDatasBase(de, ate, codigo, nome).stream()
            .map(CurvaPrimrDataBaseResponse::fromDomain)
            .toList());
    }

    @GetMapping("/{nome}/primaria-b3/{dataBase}")
    @Operation(summary = "Consulta dos vértices de uma data", description = "Vértices do dado bruto e se a data já foi construída")
    public ResponseEntity<VerticesPrimrDaDataResponse<BtrsCurvaPrimrVerticeResponse>> consultar(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        VerticesPrimrDaData<BtrsCurvaPrimr> resultado = useCase.consultar(nome, dataBase);
        return ResponseEntity.ok(new VerticesPrimrDaDataResponse<>(resultado.curvaConstruida(),
            resultado.vertices().stream().map(BtrsCurvaPrimrVerticeResponse::fromDomain).toList()));
    }

    @PostMapping("/{nome}/primaria-b3/{dataBase}/vertices")
    @Operation(summary = "Incluir vértice", description = "Não reconstrói a curva; recalcule na tela Curvas se a data já foi construída")
    public ResponseEntity<BtrsCurvaPrimrVerticeResponse> incluir(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @RequestBody BtrsCurvaPrimrVerticeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(BtrsCurvaPrimrVerticeResponse.fromDomain(useCase.incluir(nome, dataBase, request.toDomain())));
    }

    @PutMapping("/{nome}/primaria-b3/{dataBase}/vertices/{id}")
    @Operation(summary = "Alterar vértice", description = "Não reconstrói a curva; recalcule na tela Curvas se a data já foi construída")
    public ResponseEntity<BtrsCurvaPrimrVerticeResponse> alterar(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @Parameter(description = "Id do vértice") @PathVariable  id,
            @RequestBody BtrsCurvaPrimrVerticeRequest request) {
        return ResponseEntity.ok(BtrsCurvaPrimrVerticeResponse.fromDomain(useCase.alterar(nome, dataBase, id, request.toDomain())));
    }

    @DeleteMapping("/{nome}/primaria-b3/{dataBase}/vertices/{id}")
    @Operation(summary = "Apagar vértice", description = "Apaga só este vértice do dado bruto; a curva construída não muda")
    public ResponseEntity<Void> excluir(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase,
            @Parameter(description = "Id do vértice") @PathVariable  id) {
        useCase.excluir(nome, dataBase, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{nome}/primaria-b3/{dataBase}")
    @Operation(summary = "Apagar todos os vértices da data", description = "Apaga só o bruto; a curva construída da data não muda")
    public ResponseEntity<Void> excluirData(
            @Parameter(description = "Nome da curva", example = "DIxPRE") @PathVariable String nome,
            @Parameter(description = "Data-base (AAAA-MM-DD)", example = "2026-09-14")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataBase) {
        useCase.excluirData(nome, dataBase);
        return ResponseEntity.ok().build();
    }
}
