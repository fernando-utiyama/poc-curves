package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.ConfiguracaoCurvaResponse;
import br.com.poc.adapter.in.api.rest.dto.CriarConfiguracaoCurvaRequest;
import br.com.poc.application.port.in.usecase.ConfiguracaoCurvaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/curvas-mercado/{nome}/configuracoes")
@RequiredArgsConstructor
@Tag(name = "Configuração de Cálculo", description = "Versões de configuração de cálculo da curva de mercado (tConfgCurva)")
public class ConfiguracaoCurvaController {

    private final ConfiguracaoCurvaUseCase useCase;

    @GetMapping
    @Operation(summary = "Listar versões", description = "Lista as versões de configuração da curva da mais nova para a mais antiga")
    public ResponseEntity<List<ConfiguracaoCurvaResponse>> listar(@PathVariable String nome) {
        return ResponseEntity.ok(useCase.listarPorCurva(nome).stream()
            .map(ConfiguracaoCurvaResponse::fromDomain)
            .toList());
    }

    @GetMapping("/vigente")
    @Operation(summary = "Consultar versão vigente", description = "Consulta a versão de configuração vigente na data especificada (padrão: hoje)")
    public ResponseEntity<ConfiguracaoCurvaResponse> consultarVigente(
            @PathVariable String nome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        return ResponseEntity.ok(ConfiguracaoCurvaResponse.fromDomain(useCase.consultarVigente(nome, data)));
    }

    @PostMapping("/validacao")
    @Operation(summary = "Validar versão", description = "Valida os parâmetros de uma versão sem gravar nada: 200 se estiver correta, ou o erro explicado")
    public ResponseEntity<Void> validar(
            @PathVariable String nome,
            @RequestBody CriarConfiguracaoCurvaRequest request) {

        useCase.validar(nome, request.toDomain());
        return ResponseEntity.ok().build();
    }

    @PostMapping
    @Operation(summary = "Criar nova versão", description = "Cria uma nova versão de configuração, fechando a anterior se houver")
    public ResponseEntity<ConfiguracaoCurvaResponse> criar(
            @PathVariable String nome,
            @RequestBody CriarConfiguracaoCurvaRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ConfiguracaoCurvaResponse.fromDomain(useCase.criar(nome, request.toDomain())));
    }

    @DeleteMapping
    @Operation(summary = "Excluir versão",
        description = "Exclui a versão informada em 'versao' (padrão: a vigente hoje). Recusa se há curva construída na vigência dela. "
            + "A versão vizinha cobre a vigência da excluída, sem deixar buraco")
    public ResponseEntity<Void> excluir(
            @PathVariable String nome,
            @RequestParam(required = false) Integer versao) {

        useCase.excluir(nome, versao);
        return ResponseEntity.ok().build();
    }
}
