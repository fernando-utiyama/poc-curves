package com.poccurves.processor.adapter.in.web;

import com.poccurves.processor.application.CurvaPublicadaEventPort;
import com.poccurves.processor.application.PublicacaoCurvaService;
import com.poccurves.processor.domain.CargaManualPoliticaAcesso;
import com.poccurves.processor.domain.CurvaNaoMapeadaException;
import com.poccurves.processor.domain.CurvaVaziaException;
import com.poccurves.processor.domain.EstadoVersaoCurva;
import com.poccurves.processor.domain.LeitorCsvCurva;
import com.poccurves.processor.domain.LeitorXlsxCurva;
import com.poccurves.processor.domain.MomentoCurva;
import com.poccurves.processor.domain.ResultadoLeituraCarga;
import com.poccurves.processor.domain.ResultadoPublicacaoCarga;
import com.poccurves.processor.domain.VerticeCurva;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/interno/carga-manual")
public class CargaManualInternoController {

    private final PublicacaoCurvaService publicacaoCurvaService;
    private final CurvaPublicadaEventPort curvaPublicadaEventPort;

    public CargaManualInternoController(PublicacaoCurvaService publicacaoCurvaService, CurvaPublicadaEventPort curvaPublicadaEventPort) {
        this.publicacaoCurvaService = publicacaoCurvaService;
        this.curvaPublicadaEventPort = curvaPublicadaEventPort;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaManualInternaResponse> carregar(
            @RequestParam String codigo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @RequestParam String momento,
            @RequestParam String justificativa,
            @RequestParam String carregadoPor,
            @RequestParam UUID execucaoCurvaId,
            @RequestParam MultipartFile arquivo
    ) throws IOException {
        // Endpoint interno: chamado só por curve-orchestrator, autenticado hoje só pela rede
        // interna do compose (sem JWT/RBAC real ainda — CargaManualPoliticaAcesso.validar usa
        // um perfil fixo "OPERADOR" enquanto a cadeia de autenticação real não chega até aqui).
        byte[] conteudo = arquivo.getBytes();

        CargaManualPoliticaAcesso.validar(conteudo.length, "OPERADOR");

        String nomeArquivo = arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo";
        String nomeLower = nomeArquivo.toLowerCase();

        ResultadoLeituraCarga resultadoLeitura;
        if (nomeLower.endsWith(".csv")) {
            resultadoLeitura = LeitorCsvCurva.ler(conteudo, "UTF-8", ';', ',');
        } else if (nomeLower.endsWith(".xlsx")) {
            resultadoLeitura = LeitorXlsxCurva.ler(conteudo, ',');
        } else {
            return ResponseEntity.badRequest().body(new CargaManualInternaResponse(
                    "ERRO_LEITURA", "Formato de arquivo não suportado: " + nomeArquivo, List.of(), List.of(), null, null));
        }

        if (resultadoLeitura instanceof ResultadoLeituraCarga.Falha falha) {
            List<ErroCargaInternoDTO> erros = falha.erros().stream()
                    .map(e -> new ErroCargaInternoDTO(e.numeroLinha(), e.coluna(), e.mensagem()))
                    .toList();
            return ResponseEntity.ok(new CargaManualInternaResponse(
                    "ERRO_LEITURA", "Falha na leitura do arquivo: " + erros.size() + " erro(s) encontrado(s).",
                    erros, List.of(), null, null));
        }

        List<VerticeCurva> vertices = ((ResultadoLeituraCarga.Sucesso) resultadoLeitura).vertices();
        String hashArquivo = calcularHashSha256(conteudo);
        MomentoCurva momentoCurva = MomentoCurva.valueOf(momento);

        ResultadoPublicacaoCarga resultado;
        try {
            resultado = publicacaoCurvaService.publicarCurvaCarregada(
                    codigo, dataReferencia, momentoCurva, execucaoCurvaId, vertices,
                    nomeArquivo, hashArquivo, carregadoPor, justificativa);
        } catch (CurvaNaoMapeadaException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new CargaManualInternaResponse(
                    "ERRO_LEITURA", "Código de curva não encontrado: " + codigo, List.of(), List.of(), null, null));
        } catch (CurvaVaziaException e) {
            return ResponseEntity.badRequest().body(new CargaManualInternaResponse(
                    "ERRO_LEITURA", "Arquivo não contém nenhum vértice válido.", List.of(), List.of(), null, null));
        }

        if (!resultado.recarga() && resultado.versao().estado() == EstadoVersaoCurva.PUBLICADA) {
            curvaPublicadaEventPort.publicar(codigo, resultado.versao(), vertices.size());
        }

        List<ItemValidacaoInternoDTO> validacoes = resultado.resultadosValidacao().stream()
                .map(r -> new ItemValidacaoInternoDTO(
                        r.identificador(), r.classificacao().name(), r.resultado().name(),
                        r.medidaObservada(), r.limiteAplicado(), r.detalhe()))
                .toList();

        String status = resultado.versao().estado() == EstadoVersaoCurva.PUBLICADA ? "ACEITA" : "REPROVADA_VALIDACAO";
        String mensagem = resultado.recarga()
                ? "Recarga do mesmo arquivo já publicado anteriormente (mesma hash) — nenhuma alteração aplicada."
                : (status.equals("ACEITA") ? "Curva publicada com sucesso." : "Carga reprovada por falha em teste de validação bloqueante.");

        return ResponseEntity.ok(new CargaManualInternaResponse(
                status, mensagem, List.of(), validacoes,
                resultado.versao().id(), resultado.versao().numeroVersao()));
    }

    private String calcularHashSha256(byte[] conteudo) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(conteudo);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM", e);
        }
    }

    public record ErroCargaInternoDTO(int numeroLinha, String coluna, String mensagem) {}

    public record ItemValidacaoInternoDTO(
            String identificador,
            String classificacao,
            String resultado,
            BigDecimal medidaObservada,
            BigDecimal limiteAplicado,
            String detalhe
    ) {}

    public record CargaManualInternaResponse(
            String status, // ACEITA, REPROVADA_VALIDACAO, ERRO_LEITURA
            String mensagem,
            List<ErroCargaInternoDTO> errosLeitura,
            List<ItemValidacaoInternoDTO> validacoes,
            UUID versionId,
            Integer versionNumber
    ) {}
}
