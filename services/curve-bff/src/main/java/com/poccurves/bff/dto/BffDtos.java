package com.poccurves.bff.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class BffDtos {

    // ==========================================
    // PAINEL DO DIA & ALERTAS
    // ==========================================

    public record PainelDoDiaResponse(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            List<ItemPainelDoDiaDTO> curvas
    ) {}

    public record ItemPainelDoDiaDTO(
            String codigo,
            String nome,
            String moeda,
            String modoOrigem,
            String status, // PUBLICADA, EM_ANDAMENTO, EM_RISCO, ATRASADA, REPROVADA, NAO_INICIADA
            String horarioLimitePublicacao,
            Integer tempoRestanteMinutos,
            Integer margemMinutos,
            String etapaAtual,
            Integer versaoNumero,
            String motivoReprovacao,
            Integer alertasAvisoContagem,
            Instant publicadoEm
    ) {}

    public record AlertasSumarioResponse(
            int gruposPendenciasAbertas,
            int totalMensagensPendentes,
            Integer idadeMaisAntigaMinutos,
            String severidadePendencias, // BAIXA, MEDIA, ALTA, CRITICA
            int curvasEmRiscoContagem,
            int curvasAtrasadasContagem,
            Integer menorMargemMinutos
    ) {}

    // ==========================================
    // CATÁLOGO & DEFINIÇÃO DE CURVA
    // ==========================================

    public record CatalogoResponse(
            List<ItemCatalogoDTO> itens,
            int totalElementos,
            int pagina,
            int totalPaginas
    ) {}

    public record ItemCatalogoDTO(
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

    public record DefinicaoCurvaDTO(
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

    public record CriarOuAtualizarDefinicaoCurvaRequest(
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

    // ==========================================
    // CURVA VIEWER (TELA AGREGADA)
    // ==========================================

    public record SecaoDegradadaDTO(
            boolean disponivel,
            String motivo,
            Long duracaoMs
    ) {
        public static SecaoDegradadaDTO ok(Long duracaoMs) {
            return new SecaoDegradadaDTO(true, null, duracaoMs);
        }

        public static SecaoDegradadaDTO erro(String motivo) {
            return new SecaoDegradadaDTO(false, motivo, null);
        }
    }

    public record VerticeCurvaDTO(
            int prazoDiasUteis,
            Integer prazoDiasCorridos,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVencimento,
            BigDecimal taxa,
            BigDecimal fatorDesconto
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

    public record ItemValidacaoDTO(
            String teste,
            String classificacao,
            String resultado,
            BigDecimal medidaObservada,
            BigDecimal limiteAplicado,
            String detalhe
    ) {}

    public record ValidacaoCurvaDTO(
            String statusGeral,
            List<ItemValidacaoDTO> itens
    ) {}

    public record ExecucaoResumoDTO(
            UUID id,
            String correlationId,
            String estado,
            String etapaAtual,
            Integer duracaoSegundos,
            String erroMensagem
    ) {}

    public record CurvaViewerResponse(
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
            List<VerticeCurvaDTO> vertices,
            ProcedenciaCurvaDTO procedencia,
            ValidacaoCurvaDTO validacao,
            ExecucaoResumoDTO ultimaExecucao,
            SecaoDegradadaDTO secaoVertices,
            SecaoDegradadaDTO secaoProcedencia,
            SecaoDegradadaDTO secaoValidacao,
            SecaoDegradadaDTO secaoExecucao
    ) {}

    // ==========================================
    // INTERPOLAÇÃO & COMPARAÇÃO
    // ==========================================

    public record InterpolacaoRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String momento,
            Integer versao,
            List<Integer> prazosDiasUteis
    ) {}

    public record ItemInterpolacaoDTO(
            int prazoDiasUteis,
            BigDecimal taxa,
            BigDecimal fatorDesconto,
            String status, // VERTICE_EXATO, INTERPOLADO, ERRO_FORA_INTERVALO
            String erroMensagem
    ) {}

    public record InterpolacaoResponse(
            String codigoCurva,
            int versaoUtilizada,
            String interpoladorUtilizado,
            List<ItemInterpolacaoDTO> resultados,
            SecaoDegradadaDTO statusMotor
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

    public record ItemComparacaoDTO(
            int prazoDiasUteis,
            BigDecimal taxaA,
            BigDecimal taxaB,
            BigDecimal diferencaTaxaBps,
            BigDecimal fatorDescontoA,
            BigDecimal fatorDescontoB,
            String status // COINCIDENTE, PRESENTE_APENAS_EM_A, PRESENTE_APENAS_EM_B
    ) {}

    public record ComparacaoResponse(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String rotuloCurvaA,
            String rotuloCurvaB,
            List<ItemComparacaoDTO> diferencas
    ) {}

    public record ComparacaoModelosRequest(
            String codigoCurva,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String momento,
            String modeloA,
            String modeloB
    ) {}

    // ==========================================
    // AÇÕES DE DISPARO & MONITORAMENTO
    // ==========================================

    public record DisparoManualRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            List<String> conjuntosInsumo, // [BVBG_086, BVBG_028, PRECOS, TAXAS_REFERENCIA]
            String justificativa
    ) {}

    public record ProgressoConjuntoDTO(
            String conjunto,
            // INICIADO, EM_PROCESSAMENTO, CONCLUIDO, FALHA, SEM_DADO (curve-orchestrator
            // distingue "fonte não tem dado ainda" de falha real — não é a mesma coisa)
            String status,
            String mensagem
    ) {}

    public record DisparoManualResponse(
            String correlationId,
            String status, // DISPARADO, JA_EM_ANDAMENTO, RECUSADO_NAO_PREGAO
            String mensagem,
            List<ProgressoConjuntoDTO> progressoPorConjunto
    ) {}

    public record BackfillRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataInicio,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataFim,
            List<String> curvas,
            String justificativa
    ) {}

    public record BackfillResponse(
            String correlationId,
            int totalDiasUteis,
            String status,
            String mensagem
    ) {}

    public record ItemExecucaoDTO(
            UUID id,
            String correlationId,
            String tipoExecucao,
            String codigoCurva,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            String momento,
            String estado,
            String etapaAtual,
            Integer duracaoSegundos,
            Instant iniciadoEm,
            Instant concluidoEm,
            String disparadoPor,
            String erroMensagem
    ) {}

    public record ExecucoesResponse(
            List<ItemExecucaoDTO> itens,
            int totalElementos,
            int pagina,
            int totalPaginas
    ) {}

    // ==========================================
    // PENDÊNCIAS DLQ
    // ==========================================

    public record GrupoPendenciaDTO(
            UUID grupoId,
            String motivo,
            String fonte,
            String conjuntoDados,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            int quantidadeMensagens,
            Instant primeiroErroEm,
            Instant ultimoErroEm,
            String detalheRepresentativo,
            String estado // ABERTO, EM_REPROCESSAMENTO, RESOLVIDO, DESCARTADO, OBSOLETO
    ) {}

    public record PendenciasSumarioResponse(
            List<GrupoPendenciaDTO> grupos,
            int totalGruposAbertos,
            int totalMensagensPendentes
    ) {}

    public record ItemPendenciaDTO(
            UUID id,
            String idEvento,
            String correlationId,
            String payload,
            String erroDetalhe,
            Instant ocorridoEm
    ) {}

    public record PendenciasDetalheResponse(
            UUID grupoId,
            List<ItemPendenciaDTO> itens,
            int totalElementos,
            int pagina,
            int totalPaginas
    ) {}

    public record DescartarPendenciaRequest(
            String justificativa
    ) {}

    public record AcaoPendenciaResponse(
            String acao,
            String status, // REPROCESSAMENTO_INICIADO, DESCARTADO, RECUSADO_OBSOLETO
            String mensagem,
            int mensagensAfetadas
    ) {}

    // ==========================================
    // CARGA MANUAL DE CONTINGÊNCIA
    // ==========================================

    public record ErroCargaDTO(
            int linha,
            String coluna,
            String motivo
    ) {}

    public record CargaManualResponse(
            String correlationId,
            String status, // ACEITA, REPROVADA_VALIDACAO, ERRO_LEITURA
            String mensagem,
            List<ErroCargaDTO> errosLeitura,
            List<ItemValidacaoDTO> validacoesReprovadas
    ) {}

    // ==========================================
    // GESTÃO DE MODELOS
    // ==========================================

    public record ModeloDTO(
            UUID id,
            String codigo,
            String nome,
            String tipo, // BUILTIN, GROOVY
            String estado, // ATIVO, DESABILITADO
            String checksum,
            Instant criadoEm
    ) {}

    public record ModelosResponse(
            List<ModeloDTO> modelos
    ) {}

    public record ImportarModeloGroovyRequest(
            String codigo,
            String nome,
            String scriptGroovy
    ) {}

    public record ImportarModeloResponse(
            UUID id,
            String codigo,
            String status, // VALIDO, ERRO_COMPILACAO, ERRO_EXECUCAO_TESTE
            String checksum,
            String mensagem
    ) {}

    // ==========================================
    // RESPOSTA PADRÃO DE ERRO
    // ==========================================

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
