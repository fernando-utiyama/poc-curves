package br.com.poc.adapter.in.api.rest.controller;

import br.com.poc.adapter.in.api.rest.dto.CurvaMercadoDetalhadaResponse;
import br.com.poc.adapter.in.api.rest.dto.CurvaMercadoRequest;
import br.com.poc.adapter.in.api.rest.dto.CurvaMercadoResponse;
import br.com.poc.adapter.in.api.rest.dto.CurvasMercadoPaginadaResponse;
import br.com.poc.adapter.in.api.rest.excel.CurvaAuditoriaExcelGenerator;
import br.com.poc.application.exception.CadastroErrorCode;
import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.port.in.usecase.CurvaMercadoUseCase;
import br.com.poc.domain.cadastro.CurvaAuditoria;
import br.com.poc.domain.cadastro.CurvaMercado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/curvas-mercado")
@RequiredArgsConstructor
@Tag(name = "Curvas de Mercado", description = "CRUD e consulta de curvas de mercado")
public class CurvaMercadoController {

    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final CurvaMercadoUseCase useCase;

    @GetMapping
    @Operation(summary = "Listar curvas de mercado", description = "Lista curvas com filtros e paginação ordenada por código")
    public ResponseEntity<CurvasMercadoPaginadaResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String codigo,
            @RequestParam(required = false) String unidade,
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) String provedor,
            @RequestParam(required = false) String dono,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {

        Page<CurvaMercado> page = useCase.listar(nome, codigo, unidade, situacao, provedor, dono, pagina, tamanho);
        List<String> nomesDaPagina = page.getContent().stream().map(CurvaMercado::nome).toList();
        Map<String, List<String>> provedoresPorCurva = useCase.buscarProvedoresPorNomes(nomesDaPagina);

        List<CurvaMercadoResponse> itens = page.getContent().stream()
            .map(c -> CurvaMercadoResponse.fromDomain(c, List.of(), provedoresPorCurva.getOrDefault(c.nome(), List.of())))
            .toList();

        CurvasMercadoPaginadaResponse response = new CurvasMercadoPaginadaResponse(
            itens,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{nome}")
    @Operation(summary = "Consultar curva de mercado", description = "Consulta curva pelo nome, incluindo provedores e configuração vigente")
    public ResponseEntity<CurvaMercadoDetalhadaResponse> consultar(@PathVariable String nome) {
        return ResponseEntity.ok(CurvaMercadoDetalhadaResponse.fromDomain(useCase.consultar(nome)));
    }

    @PostMapping
    @Operation(summary = "Criar curva de mercado", description = "Cria uma nova curva de mercado com situação ATIVO")
    public ResponseEntity<CurvaMercadoDetalhadaResponse> criar(@RequestBody CurvaMercadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CurvaMercadoDetalhadaResponse.fromDomain(useCase.criar(request.toInput())));
    }

    @PutMapping("/{nome}")
    @Operation(summary = "Alterar curva de mercado", description = "Altera campos da curva")
    public ResponseEntity<CurvaMercadoDetalhadaResponse> alterar(
            @PathVariable String nome,
            @RequestBody CurvaMercadoRequest request) {

        return ResponseEntity.ok(CurvaMercadoDetalhadaResponse.fromDomain(useCase.alterar(nome, request.toInput())));
    }

    @DeleteMapping("/{nome}")
    @Operation(summary = "Excluir curva de mercado",
        description = "Exclui a curva com provedores e configurações. Recusa curva com construído ou dado bruto "
            + "(use a inativação). Não apaga o dado bruto dos provedores")
    public ResponseEntity<Void> excluir(@PathVariable String nome) {
        useCase.excluir(nome);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{nome}/inativacao")
    @Operation(summary = "Inativar curva de mercado", description = "Altera situação da curva para INATIVO")
    public ResponseEntity<CurvaMercadoDetalhadaResponse> inativar(@PathVariable String nome) {
        return ResponseEntity.ok(CurvaMercadoDetalhadaResponse.fromDomain(useCase.inativar(nome)));
    }

    @PostMapping("/{nome}/reativacao")
    @Operation(summary = "Reativar curva de mercado", description = "Altera situação da curva para ATIVO")
    public ResponseEntity<CurvaMercadoDetalhadaResponse> reativar(@PathVariable String nome) {
        return ResponseEntity.ok(CurvaMercadoDetalhadaResponse.fromDomain(useCase.reativar(nome)));
    }

    /** Sem `formato`, ou `formato=json`: o JSON tipado. Outro valor que não seja `xlsx` = 400. */
    @GetMapping(value = "/{nome}/auditoria", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Auditoria do cadastro da curva", description = "Monta na hora a auditoria da curva em JSON; com formato=xlsx, em planilha")
    public ResponseEntity<CurvaAuditoria> auditoria(
            @PathVariable String nome,
            @RequestParam(required = false) String formato) {

        if (formato != null && !"json".equalsIgnoreCase(formato)) {
            throw new InvalidInputException(CadastroErrorCode.PARAMETRO_INVALIDO);
        }
        return ResponseEntity.ok(useCase.consultarAuditoria(nome));
    }

    /** `formato=xlsx`: a planilha. É escolhida no lugar do método acima por causa da condição `params`. */
    @GetMapping(value = "/{nome}/auditoria", params = "formato=xlsx", produces = XLSX)
    @Operation(summary = "Auditoria do cadastro da curva em planilha", description = "Mesma auditoria, em XLSX")
    public ResponseEntity<byte[]> auditoriaXlsx(@PathVariable String nome) {
        CurvaAuditoria auditoria = useCase.consultarAuditoria(nome);
        byte[] bytes = CurvaAuditoriaExcelGenerator.gerar(auditoria);

        String timestamp = LocalDateTime.now().format(FILENAME_DATE_FORMAT);
        String safeNome = nome != null ? nome.replaceAll("[^a-zA-Z0-9_]", "") : "CURVA";
        String filename = String.format("%s_CADASTRO_AUDITORIA_%s.xlsx", safeNome, timestamp);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
            .contentType(MediaType.parseMediaType(XLSX))
            .body(bytes);
    }
}
