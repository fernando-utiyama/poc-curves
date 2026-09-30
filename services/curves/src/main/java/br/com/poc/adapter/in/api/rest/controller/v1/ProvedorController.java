package br.com.poc.adapter.in.api.rest.controller.v1;

import br.com.poc.adapter.in.api.rest.dto.v1.AtualizarProvedorRequest;
import br.com.poc.adapter.in.api.rest.dto.v1.CriarProvedorRequest;
import br.com.poc.adapter.in.api.rest.dto.v1.ProvedorResponse;
import br.com.poc.adapter.in.api.rest.mapper.ProvedorApiMapper;
import br.com.poc.application.dto.v1.AtualizarProvedorCommand;
import br.com.poc.application.dto.v1.CriarProvedorCommand;
import br.com.poc.application.port.in.usecase.ProvedorUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/provedores")
@RequiredArgsConstructor
@Tag(name = "Provedores", description = "Operações de CRUD de provedores")
public class ProvedorController implements ProvedorSwaggerController {

    private final ProvedorUseCase provedorUseCase;
    private final ProvedorApiMapper mapper;

    @Override
    @PostMapping
    @Operation(
        summary = "Criar provedor",
        description = "Cria um novo provedor de dados de mercado"
    )
    public ResponseEntity<ProvedorResponse> criar(@Valid @RequestBody CriarProvedorRequest request) {
        var result = provedorUseCase.criar(
            new CriarProvedorCommand(
                request.nomeProvedor(),
                request.descricao(),
                request.produto(),
                request.nomeCompletoAtivoOuInstrumento()
            )
        );

        return ResponseEntity
            .created(URI.create("/api/v1/provedores/" + result.nomeProvedor()))
            .body(mapper.toResponse(result));
    }

    @Override
    @GetMapping("/{nomeProvedor}")
    @Operation(
        summary = "Buscar provedor por id",
        description = "Busca um provedor pelo nomeProvedor"
    )
    public ResponseEntity<ProvedorResponse> buscarPorId(@PathVariable String nomeProvedor) {
        return ResponseEntity.ok(mapper.toResponse(provedorUseCase.buscarPorId(nomeProvedor)));
    }

    @Override
    @GetMapping
    @Operation(
        summary = "Listar provedores",
        description = "Lista todos os provedores cadastrados"
    )
    public ResponseEntity<List<ProvedorResponse>> listarTodos() {
        return ResponseEntity.ok(
            provedorUseCase.listarTodos().stream()
                .map(mapper::toResponse)
                .toList()
        );
    }

    @Override
    @PutMapping("/{nomeProvedor}")
    @Operation(
        summary = "Atualizar provedor",
        description = "Atualiza os dados de um provedor existente"
    )
    public ResponseEntity<ProvedorResponse> atualizar(@PathVariable String nomeProvedor,
                                                      @Valid @RequestBody AtualizarProvedorRequest request) {
        var result = provedorUseCase.atualizar(
            nomeProvedor,
            new AtualizarProvedorCommand(
                request.nomeProvedor(),
                request.descricao(),
                request.produto(),
                request.nomeCompletoAtivoOuInstrumento()
            )
        );

        return ResponseEntity.ok(mapper.toResponse(result));
    }

    @Override
    @DeleteMapping("/{nomeProvedor}")
    @Operation(
        summary = "Excluir provedor",
        description = "Exclui um provedor existente"
    )
    public ResponseEntity<Void> excluir(@PathVariable String nomeProvedor) {
        provedorUseCase.excluir(nomeProvedor);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/filtro")
    public ResponseEntity<List<ProvedorResponse>> filtrarPorTexto(@RequestParam String texto) {
        var resultado = provedorUseCase.filtrarPorTexto(texto).stream()
            .map(mapper::toResponse)
            .toList();

        return ResponseEntity.ok(resultado);
    }
}
