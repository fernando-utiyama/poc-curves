package com.poccurves.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ApiDtos {

    public record CatalogoDefinicoesResponse(
            List<ItemCatalogoDefinicao> itens,
            int totalElementos,
            int pagina,
            int totalPaginas
    ) {}

    public record ItemCatalogoDefinicao(
            UUID id,
            String codigo,
            String nome,
            String moeda,
            String modoOrigem,
            String estado,
            int versaoVigenteNumero,
            String modeloApontadoNome
    ) {}

    public record LimiteValidacaoDTO(
            String teste,
            String classificacao,
            String limite
    ) {}

    public record CriarDefinicaoRequest(
            String codigo,
            String nome,
            String moeda,
            String modoOrigem,
            String estado,
            String horarioLimitePublicacao,
            String contagemDias,
            String calendario,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            UUID modeloApontadoId,
            String modeloApontadoCodigo,
            Integer orcamentoIngestaoSegundos,
            Integer orcamentoConstrucaoSegundos,
            Integer orcamentoValidacaoSegundos,
            Integer orcamentoPublicacaoSegundos,
            Integer janelaBloqueioMinutos,
            List<String> vinculosFonte,
            List<String> dependeDe,
            List<LimiteValidacaoDTO> limitesValidacao,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vigenciaInicio
    ) {}

    public record AtualizarDefinicaoRequest(
            String nome,
            String moeda,
            String modoOrigem,
            String estado,
            String horarioLimitePublicacao,
            String contagemDias,
            String calendario,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            UUID modeloApontadoId,
            String modeloApontadoCodigo,
            Integer orcamentoIngestaoSegundos,
            Integer orcamentoConstrucaoSegundos,
            Integer orcamentoValidacaoSegundos,
            Integer orcamentoPublicacaoSegundos,
            Integer janelaBloqueioMinutos,
            List<String> vinculosFonte,
            List<String> dependeDe,
            List<LimiteValidacaoDTO> limitesValidacao,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vigenciaInicio
    ) {}

    public record DefinicaoCurvaResponse(
            UUID id,
            String codigo,
            String nome,
            String moeda,
            String modoOrigem,
            String estado,
            String horarioLimitePublicacao,
            UUID versaoDefinicaoId,
            int versaoNumero,
            Integer versaoOrigemNumero,
            String contagemDias,
            String calendario,
            String interpolador,
            String politicaExtrapolacao,
            String politicaArredondamento,
            String modeloApontadoCodigo,
            Integer orcamentoIngestaoSegundos,
            Integer orcamentoConstrucaoSegundos,
            Integer orcamentoValidacaoSegundos,
            Integer orcamentoPublicacaoSegundos,
            Integer janelaBloqueioMinutos,
            List<String> vinculosFonte,
            List<String> dependeDe,
            List<LimiteValidacaoDTO> limitesValidacao,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vigenciaInicio,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vigenciaFim
    ) {}

    public record HistoricoVersoesDefinicaoResponse(
            String codigo,
            List<ItemHistoricoVersaoDefinicao> versoes
    ) {}

    public record ItemHistoricoVersaoDefinicao(
            UUID versaoDefinicaoId,
            int numeroVersao,
            String interpolador,
            String modeloApontadoCodigo,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vigenciaInicio,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vigenciaFim
    ) {}

    public record CurvaPublicadaResponse(
            UUID versaoCurvaId,
            String codigoCurva,
            String nomeCurva,
            String modoOrigem,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String momento,
            int numeroVersao,
            String estadoVersao,
            String origemVersao,
            boolean isVersaoCorrente,
            String razaoSelecaoVersao,
            Instant publicadoEm,
            ProcedenciaCurvaDTO procedencia,
            ValidacaoCurvaDTO validacao,
            List<VerticeCurvaDTO> vertices
    ) {}

    public record VerticeCurvaDTO(
            int prazoDiasUteis,
            Integer prazoDiasCorridos,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVencimento,
            BigDecimal taxa,
            BigDecimal fatorDesconto
    ) {}

    public record VerticesPaginadosResponse(
            UUID versaoCurvaId,
            String codigoCurva,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            List<VerticeCurvaDTO> vertices,
            int totalVertices,
            int pagina,
            int totalPaginas
    ) {}

    public record ProcedenciaCurvaDTO(
            UUID execucaoCurvaId,
            String correlationId,
            int numeroVersaoDefinicao,
            String modeloCodigo,
            String checksumModelo,
            String referenciasInsumo,
            String hashConjuntoInsumos,
            Long loteIngestaoId,
            String arquivoCarga,
            String hashArquivo,
            String carregadoPor,
            String justificativa,
            String versaoMotor
    ) {}

    public record ValidacaoCurvaDTO(
            String statusGeral,
            List<ItemValidacaoDTO> itens
    ) {}

    public record ItemValidacaoDTO(
            String teste,
            String classificacao,
            String resultado,
            BigDecimal medidaObservada,
            BigDecimal limiteAplicado,
            String detalhe
    ) {}

    public record HistoricoVersoesCurvaResponse(
            String codigoCurva,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String momento,
            List<ItemHistoricoVersaoCurva> versoes
    ) {}

    public record ItemHistoricoVersaoCurva(
            UUID versaoCurvaId,
            int numeroVersao,
            String estado,
            String origemVersao,
            UUID execucaoCurvaId,
            String modeloCodigo,
            Instant publicadoEm
    ) {}

    public record ProcedenciaCurvaResponse(
            UUID versaoCurvaId,
            String codigoCurva,
            ProcedenciaCurvaDTO procedencia
    ) {}

    public record InterpolacaoRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String momento,
            Integer versao,
            List<Integer> prazosDiasUteis
    ) {}

    public record InterpolacaoResponse(
            String codigoCurva,
            int versaoUtilizada,
            String interpoladorUtilizado,
            List<ItemInterpolacaoResultadoDTO> resultados
    ) {}

    public record ItemInterpolacaoResultadoDTO(
            int prazoDiasUteis,
            BigDecimal taxa,
            BigDecimal fatorDesconto,
            String status,
            String erroMensagem
    ) {}

    public record CurvaIdentificadorDTO(
            String codigo,
            Integer versao
    ) {}

    public record ComparacaoCurvasRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String momento,
            CurvaIdentificadorDTO curvaA,
            CurvaIdentificadorDTO curvaB
    ) {}

    public record ComparacaoCurvasResponse(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String rotuloCurvaA,
            String rotuloCurvaB,
            List<ItemDiferencaComparacao> diferencas
    ) {}

    public record ItemDiferencaComparacao(
            int prazoDiasUteis,
            BigDecimal taxaA,
            BigDecimal taxaB,
            BigDecimal diferencaTaxaBps,
            BigDecimal fatorDescontoA,
            BigDecimal fatorDescontoB,
            String status
    ) {}

    public record ErroResposta(
            String codigo,
            String mensagem,
            List<String> detalhes,
            Instant timestamp
    ) {
        public ErroResposta(String codigo, String mensagem) {
            this(codigo, mensagem, null, Instant.now());
        }

        public ErroResposta(String codigo, String mensagem, List<String> detalhes) {
            this(codigo, mensagem, detalhes, Instant.now());
        }
    }
}
