package com.poccurves.api.application;

import com.poccurves.api.domain.ComparadorCurvas;
import com.poccurves.api.domain.PontoComparacao;
import com.poccurves.api.dto.ApiDtos.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Achado real desta migração: o construtor original recebia também um `RestClient
 * engineRestClient` (Spring) nunca usado no corpo da classe — código morto. Removido
 * aqui porque manter significaria manter um tipo de framework na assinatura do
 * caso de uso sem nenhum uso real; não é mudança de comportamento (nada chamava esse
 * campo).
 */
public class CurvaConsultaService {

    private final CurvaConsultaRepositoryPort repo;

    public CurvaConsultaService(CurvaConsultaRepositoryPort repo) {
        this.repo = repo;
    }

    public CurvaPublicadaResponse consultarCurvaPublicada(
            String codigo,
            LocalDate dataReferencia,
            String momento,
            Integer versao,
            Instant asOf
    ) {
        String cod = codigo.trim().toUpperCase();
        String mom = momento != null ? momento.toUpperCase() : "FECHAMENTO";

        Optional<CurvaConsultaRepositoryPort.VersaoCurvaRegistro> versaoRegOpt;
        String razaoSelecao;

        if (versao != null) {
            versaoRegOpt = repo.buscarVersaoPorNumero(cod, dataReferencia, mom, versao);
            razaoSelecao = "VERSAO_EXPLICITA";
        } else if (asOf != null) {
            versaoRegOpt = repo.buscarVersaoAsOf(cod, dataReferencia, mom, asOf);
            razaoSelecao = "SELECAO_AS_OF";
        } else {
            versaoRegOpt = repo.buscarVersaoPublicada(cod, dataReferencia, mom);
            razaoSelecao = "VERSAO_CORRENTE";
        }

        var versaoReg = versaoRegOpt.orElseThrow(() -> new NoSuchElementException(
                "Nenhuma versão de curva encontrada para '" + cod + "' em " + dataReferencia + " (" + mom + ")."
        ));

        // Busca se é a versão corrente publicada
        var correnteOpt = repo.buscarVersaoPublicada(cod, dataReferencia, mom);
        boolean isCorrente = correnteOpt.isPresent() && correnteOpt.get().versaoCurvaId().equals(versaoReg.versaoCurvaId());

        // Busca vértices
        List<CurvaConsultaRepositoryPort.VerticeRegistro> verticesReg = repo.buscarTodosVertices(versaoReg.versaoCurvaId());
        List<VerticeCurvaDTO> verticesDto = verticesReg.stream()
                .map(v -> new VerticeCurvaDTO(
                        v.prazoDiasUteis(),
                        v.prazoDiasCorridos(),
                        v.dataVencimento(),
                        v.taxa(),
                        v.fatorDesconto()
                ))
                .toList();

        // Busca procedência
        ProcedenciaCurvaDTO procedenciaDto = repo.buscarProcedencia(versaoReg.versaoCurvaId())
                .map(p -> new ProcedenciaCurvaDTO(
                        p.execucaoCurvaId(),
                        p.correlationId(),
                        p.numeroVersaoDefinicao(),
                        p.modeloCodigo(),
                        p.checksumModelo(),
                        p.referenciasInsumo(),
                        p.hashConjuntoInsumos(),
                        p.loteIngestaoId(),
                        p.arquivoCarga(),
                        p.hashArquivo(),
                        p.carregadoPor(),
                        p.justificativa(),
                        p.versaoMotor()
                ))
                .orElse(null);

        // Busca validações
        List<CurvaConsultaRepositoryPort.ValidacaoItemRegistro> validacoesReg = repo.buscarValidacoes(versaoReg.versaoCurvaId());
        ValidacaoCurvaDTO validacaoDto = null;
        if (!validacoesReg.isEmpty()) {
            boolean temReprovadoBloqueante = validacoesReg.stream()
                    .anyMatch(v -> "REPROVADO".equalsIgnoreCase(v.resultado()) && "BLOQUEANTE".equalsIgnoreCase(v.classificacao()));
            boolean temReprovadoAviso = validacoesReg.stream()
                    .anyMatch(v -> "REPROVADO".equalsIgnoreCase(v.resultado()) && "AVISO".equalsIgnoreCase(v.classificacao()));

            String statusGeral = temReprovadoBloqueante ? "REPROVADA" : (temReprovadoAviso ? "APROVADA_COM_AVISOS" : "APROVADA");
            List<ItemValidacaoDTO> itensDto = validacoesReg.stream()
                    .map(v -> new ItemValidacaoDTO(
                            v.teste(),
                            v.classificacao(),
                            v.resultado(),
                            v.medidaObservada(),
                            v.limiteAplicado(),
                            v.detalhe()
                    ))
                    .toList();
            validacaoDto = new ValidacaoCurvaDTO(statusGeral, itensDto);
        }

        return new CurvaPublicadaResponse(
                versaoReg.versaoCurvaId(),
                versaoReg.codigoCurva(),
                versaoReg.nomeCurva(),
                versaoReg.modoOrigem(),
                versaoReg.dataReferencia(),
                versaoReg.momentoCurva(),
                versaoReg.numeroVersao(),
                versaoReg.estado(),
                versaoReg.origemVersao(),
                isCorrente,
                razaoSelecao,
                versaoReg.publicadoEm(),
                procedenciaDto,
                validacaoDto,
                verticesDto
        );
    }

    public VerticesPaginadosResponse listarVerticesPaginados(
            String codigo,
            UUID versaoCurvaId,
            int pagina,
            int tamanhoPagina
    ) {
        int total = repo.contarVertices(versaoCurvaId);
        int offset = pagina * tamanhoPagina;
        List<CurvaConsultaRepositoryPort.VerticeRegistro> regs = repo.listarVertices(versaoCurvaId, offset, tamanhoPagina);

        List<VerticeCurvaDTO> verticesDto = regs.stream()
                .map(v -> new VerticeCurvaDTO(
                        v.prazoDiasUteis(),
                        v.prazoDiasCorridos(),
                        v.dataVencimento(),
                        v.taxa(),
                        v.fatorDesconto()
                ))
                .toList();

        int totalPaginas = (int) Math.ceil((double) total / tamanhoPagina);
        return new VerticesPaginadosResponse(
                versaoCurvaId,
                codigo.toUpperCase(),
                LocalDate.now(),
                verticesDto,
                total,
                pagina,
                totalPaginas
        );
    }

    public HistoricoVersoesCurvaResponse listarHistoricoVersoes(
            String codigo,
            LocalDate dataReferencia,
            String momento
    ) {
        String cod = codigo.trim().toUpperCase();
        String mom = momento != null ? momento.toUpperCase() : "FECHAMENTO";

        List<CurvaConsultaRepositoryPort.VersaoCurvaRegistro> regs = repo.listarHistoricoVersoes(cod, dataReferencia, mom);
        List<ItemHistoricoVersaoCurva> itens = regs.stream()
                .map(r -> new ItemHistoricoVersaoCurva(
                        r.versaoCurvaId(),
                        r.numeroVersao(),
                        r.estado(),
                        r.origemVersao(),
                        r.execucaoCurvaId(),
                        null,
                        r.publicadoEm()
                ))
                .toList();

        return new HistoricoVersoesCurvaResponse(cod, dataReferencia, mom, itens);
    }

    public ProcedenciaCurvaResponse obterProcedencia(String codigo, UUID versaoId) {
        String cod = codigo.trim().toUpperCase();
        ProcedenciaCurvaDTO dto = repo.buscarProcedencia(versaoId)
                .map(p -> new ProcedenciaCurvaDTO(
                        p.execucaoCurvaId(),
                        p.correlationId(),
                        p.numeroVersaoDefinicao(),
                        p.modeloCodigo(),
                        p.checksumModelo(),
                        p.referenciasInsumo(),
                        p.hashConjuntoInsumos(),
                        p.loteIngestaoId(),
                        p.arquivoCarga(),
                        p.hashArquivo(),
                        p.carregadoPor(),
                        p.justificativa(),
                        p.versaoMotor()
                ))
                .orElseThrow(() -> new NoSuchElementException("Procedência não encontrada para a versão: " + versaoId));

        return new ProcedenciaCurvaResponse(versaoId, cod, dto);
    }

    public InterpolacaoResponse interpolarCurva(String codigo, InterpolacaoRequest request) {
        String cod = codigo.trim().toUpperCase();
        String mom = request.momento() != null ? request.momento().toUpperCase() : "FECHAMENTO";

        // Localiza a curva no banco para garantir que ela existe e obter vértices
        CurvaPublicadaResponse curva = consultarCurvaPublicada(
                cod,
                request.dataReferencia(),
                mom,
                request.versao(),
                null
        );

        Map<Integer, VerticeCurvaDTO> mapaVertices = curva.vertices().stream()
                .collect(Collectors.toMap(VerticeCurvaDTO::prazoDiasUteis, v -> v, (a, b) -> a));

        List<ItemInterpolacaoResultadoDTO> resultados = new ArrayList<>();

        int minPrazo = curva.vertices().stream().mapToInt(VerticeCurvaDTO::prazoDiasUteis).min().orElse(0);
        int maxPrazo = curva.vertices().stream().mapToInt(VerticeCurvaDTO::prazoDiasUteis).max().orElse(0);

        for (int prazo : request.prazosDiasUteis()) {
            if (mapaVertices.containsKey(prazo)) {
                var v = mapaVertices.get(prazo);
                resultados.add(new ItemInterpolacaoResultadoDTO(
                        prazo,
                        v.taxa(),
                        v.fatorDesconto(),
                        "VERTICE_EXATO",
                        null
                ));
            } else if (prazo < minPrazo || prazo > maxPrazo) {
                // Fora do intervalo (sob política estrita)
                resultados.add(new ItemInterpolacaoResultadoDTO(
                        prazo,
                        null,
                        null,
                        "ERRO_FORA_INTERVALO",
                        "Prazo " + prazo + " DU fora do intervalo disponível [" + minPrazo + " - " + maxPrazo + "] sob política STRICT"
                ));
            } else {
                // Vértice interpolado intermediário
                // Interpola linearmente sobre taxas para resposta padrão
                VerticeCurvaDTO anterior = null;
                VerticeCurvaDTO posterior = null;
                for (var v : curva.vertices()) {
                    if (v.prazoDiasUteis() < prazo) {
                        anterior = v;
                    } else if (v.prazoDiasUteis() > prazo && posterior == null) {
                        posterior = v;
                    }
                }

                if (anterior != null && posterior != null) {
                    BigDecimal peso = BigDecimal.valueOf((double) (prazo - anterior.prazoDiasUteis()) / (posterior.prazoDiasUteis() - anterior.prazoDiasUteis()));
                    BigDecimal taxaInterpolada = anterior.taxa().add(posterior.taxa().subtract(anterior.taxa()).multiply(peso, MathContext.DECIMAL128))
                            .setScale(12, RoundingMode.HALF_UP);
                    BigDecimal fatorInterpolado = anterior.fatorDesconto() != null && posterior.fatorDesconto() != null
                            ? anterior.fatorDesconto().add(posterior.fatorDesconto().subtract(anterior.fatorDesconto()).multiply(peso, MathContext.DECIMAL128)).setScale(12, RoundingMode.HALF_UP)
                            : null;

                    resultados.add(new ItemInterpolacaoResultadoDTO(
                            prazo,
                            taxaInterpolada,
                            fatorInterpolado,
                            "INTERPOLADO",
                            null
                    ));
                } else {
                    resultados.add(new ItemInterpolacaoResultadoDTO(
                            prazo,
                            null,
                            null,
                            "ERRO_FORA_INTERVALO",
                            "Não foi possível interpolar o prazo " + prazo
                    ));
                }
            }
        }

        return new InterpolacaoResponse(cod, curva.numeroVersao(), "FLAT_FORWARD", resultados);
    }

    public ComparacaoCurvasResponse compararCurvas(ComparacaoCurvasRequest request) {
        String mom = request.momento() != null ? request.momento().toUpperCase() : "FECHAMENTO";

        CurvaPublicadaResponse curvaA = consultarCurvaPublicada(
                request.curvaA().codigo(),
                request.dataReferencia(),
                mom,
                request.curvaA().versao(),
                null
        );

        CurvaPublicadaResponse curvaB = consultarCurvaPublicada(
                request.curvaB().codigo(),
                request.dataReferencia(),
                mom,
                request.curvaB().versao(),
                null
        );

        Map<Integer, BigDecimal> taxasA = curvaA.vertices().stream()
                .collect(Collectors.toMap(VerticeCurvaDTO::prazoDiasUteis, VerticeCurvaDTO::taxa, (x, y) -> x));
        Map<Integer, BigDecimal> taxasB = curvaB.vertices().stream()
                .collect(Collectors.toMap(VerticeCurvaDTO::prazoDiasUteis, VerticeCurvaDTO::taxa, (x, y) -> x));

        Map<Integer, BigDecimal> fatoresA = curvaA.vertices().stream()
                .filter(v -> v.fatorDesconto() != null)
                .collect(Collectors.toMap(VerticeCurvaDTO::prazoDiasUteis, VerticeCurvaDTO::fatorDesconto, (x, y) -> x));
        Map<Integer, BigDecimal> fatoresB = curvaB.vertices().stream()
                .filter(v -> v.fatorDesconto() != null)
                .collect(Collectors.toMap(VerticeCurvaDTO::prazoDiasUteis, VerticeCurvaDTO::fatorDesconto, (x, y) -> x));

        List<PontoComparacao> pontos = ComparadorCurvas.comparar(taxasA, taxasB);
        List<ItemDiferencaComparacao> diferencas = new ArrayList<>();

        for (PontoComparacao p : pontos) {
            String status;
            BigDecimal diffBps = null;

            if (p.presenteEmAmbas()) {
                status = "COINCIDENTE";
                // diffBps = (taxaA - taxaB) * 100
                diffBps = p.taxaA().subtract(p.taxaB()).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
            } else if (p.taxaA() != null) {
                status = "PRESENTE_APENAS_EM_A";
            } else {
                status = "PRESENTE_APENAS_EM_B";
            }

            diferencas.add(new ItemDiferencaComparacao(
                    p.prazoDiasUteis(),
                    p.taxaA(),
                    p.taxaB(),
                    diffBps,
                    fatoresA.get(p.prazoDiasUteis()),
                    fatoresB.get(p.prazoDiasUteis()),
                    status
            ));
        }

        return new ComparacaoCurvasResponse(
                request.dataReferencia(),
                curvaA.codigoCurva() + " (v" + curvaA.numeroVersao() + ")",
                curvaB.codigoCurva() + " (v" + curvaB.numeroVersao() + ")",
                diferencas
        );
    }
}
