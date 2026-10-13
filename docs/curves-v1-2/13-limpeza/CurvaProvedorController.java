package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.*;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.port.in.usecase.CurvaProvedorUseCase;
import br.com.poc.domain.cadastro.AtualizarCurvaProvedorInput;
import br.com.poc.domain.cadastro.CriarCurvaProvedorInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "Curvas de Mercado Provedor", description = "Gestão de provedores da curva de mercado (tCurvaPrvdr)")
public class CurvaProvedorController {

    private final CurvaProvedorUseCase useCase;

    @GetMapping("/api/v1/curvas-mercado/{nome}/provedores")
    @Operation(summary = "Listar provedores da curva", description = "Lista os provedores da curva de mercado ordenados por prioridade")
    public ResponseEntity<List<CurvaProvedorResponse>> listarPorCurva(@PathVariable @NotBlank @Size(max = 100) String nome) {
        return ResponseEntity.ok(useCase.listarPorCurva(nome).stream()
            .map(CurvaProvedorResponse::fromDomain)
            .toList());
    }

    @PostMapping("/api/v1/curvas-mercado/{nome}/provedores")
    @Operation(summary = "Incluir provedor na curva", description = "Inclui um novo provedor na curva de mercado (tCurvaPrvdr)")
    public ResponseEntity<CurvaProvedorResponse> criar(
            @PathVariable @NotBlank @Size(max = 100) String nome,
            @Valid @RequestBody CriarCurvaProvedorRequest request) {

        CriarCurvaProvedorInput input = request.toDomain();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CurvaProvedorResponse.fromDomain(useCase.criar(nome, input)));
    }

    @PutMapping("/api/v1/curvas-mercado/{nome}/provedores/{idCurvaProvedor}")
    @Operation(summary = "Alterar provedor da curva", description = "Altera produto, tickerProvedor ou prioridade de um provedor existente")
    public ResponseEntity<CurvaProvedorResponse> alterar(
            @PathVariable @NotBlank @Size(max = 100) String nome,
            @PathVariable @NotNull @Positive Long idCurvaProvedor,
            @Valid @RequestBody AtualizarCurvaProvedorRequest request) {

        validarParametros(nome, idCurvaProvedor);
        AtualizarCurvaProvedorInput input = request.toDomain();
        return ResponseEntity.ok(CurvaProvedorResponse.fromDomain(useCase.alterar(nome, idCurvaProvedor, input)));
    }

    @DeleteMapping("/api/v1/curvas-mercado/{nome}/provedores/{idCurvaProvedor}")
    @Operation(summary = "Excluir provedor da curva", description = "Exclui um provedor existente (tCurvaPrvdr)")
    public ResponseEntity<Void> excluir(
            @PathVariable @NotBlank @Size(max = 100) String nome,
            @PathVariable @NotNull @Positive Long idCurvaProvedor) {

        validarParametros(nome, idCurvaProvedor);
        useCase.excluir(nome, idCurvaProvedor);
        return ResponseEntity.ok().build();
    }

    private void validarParametros(String nome, Long idCurvaProvedor) {
        if (nome == null || nome.isBlank()) {
            throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO);
        }
        if (idCurvaProvedor == null || idCurvaProvedor <= 0) {
            throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO);
        }
    }

    @GetMapping("/api/v1/curvas-mercado/provedores")
    @Operation(summary = "Listar curvas por origem", description = "Lista quais curvas recebem um determinado ticker de um provedor/produto")
    public ResponseEntity<List<CurvaProvedorCurvaResponse>> listarPorOrigem(
            @RequestParam(required = false) String provedor,
            @RequestParam(required = false) String produto,
            @RequestParam(required = false) String tickerProvedor) {

        return ResponseEntity.ok(useCase.listarPorOrigem(provedor, produto, tickerProvedor).stream()
            .map(CurvaProvedorCurvaResponse::fromDomain)
            .toList());
    }
}
