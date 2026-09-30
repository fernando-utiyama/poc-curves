package br.com.poc.adapter.in.api.rest.controller.v1;

import br.com.poc.adapter.in.api.rest.dto.v1.AtualizarProvedorRequest;
import br.com.poc.adapter.in.api.rest.dto.v1.CriarProvedorRequest;
import br.com.poc.adapter.in.api.rest.dto.v1.ProvedorResponse;
import br.com.poc.shared.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "Provedores", description = "Operações de CRUD de provedores")
public interface ProvedorSwaggerController {

    @Operation(
            summary = "Criar provedor",
            description = "Cria um novo provedor de dados de mercado"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Provedor criado com sucesso",
                content = @Content(schema = @Schema(implementation = ProvedorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Regra de negócio violada",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<ProvedorResponse> criar(@Valid @RequestBody CriarProvedorRequest request);

    @Operation(
            summary = "Buscar provedor por id",
            description = "Busca um provedor pelo nomeProvedor"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Provedor encontrado",
                content = @Content(schema = @Schema(implementation = ProvedorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Provedor não encontrado",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<ProvedorResponse> buscarPorId(
            @Parameter(description = "Nome do provedor", required = true)
            @PathVariable String nomeProvedor
    );

    @Operation(
            summary = "Listar provedores",
            description = "Lista todos os provedores cadastrados"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProvedorResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<List<ProvedorResponse>> listarTodos();

    @Operation(
            summary = "Atualizar provedor",
            description = "Atualiza os dados de um provedor existente"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Provedor atualizado com sucesso",
                content = @Content(schema = @Schema(implementation = ProvedorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Provedor não encontrado",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Regra de negócio violada",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<ProvedorResponse> atualizar(
            @Parameter(description = "Nome do provedor", required = true)
            @PathVariable String nomeProvedor,
            @Valid @RequestBody AtualizarProvedorRequest request
    );

    @Operation(
            summary = "Excluir provedor",
            description = "Exclui um provedor existente"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Provedor excluído com sucesso"),
        @ApiResponse(responseCode = "404", description = "Provedor não encontrado",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    ResponseEntity<Void> excluir(
            @Parameter(description = "Nome do provedor", required = true)
            @PathVariable String nomeProvedor
    );

    @Operation(
            summary = "Filtrar provedores por texto",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Filtro realizado com sucesso"),
                    @ApiResponse(responseCode = "400", description = "Texto inválido",
                            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
            }
    )
    ResponseEntity<List<ProvedorResponse>> filtrarPorTexto(String texto);
}
