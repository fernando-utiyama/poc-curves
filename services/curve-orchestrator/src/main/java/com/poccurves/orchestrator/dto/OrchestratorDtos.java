package com.poccurves.orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTOs de comunicação da API de orquestração.
 * <p>
 * Espelha o contrato já consumido por {@code curve-bff}
 * ({@code CurveOrchestratorClient.disparoManual}, {@code BffDtos.DisparoManualRequest}/{@code DisparoManualResponse}).
 */
public class OrchestratorDtos {

    public record DisparoManualRequest(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataReferencia,
            List<String> conjuntosInsumo, // [BVBG_086, BVBG_028, PRECOS, TAXAS_REFERENCIA]
            String justificativa
    ) {}

    public record ProgressoConjuntoDTO(
            String conjunto,
            String status, // INICIADO, EM_PROCESSAMENTO, CONCLUIDO, FALHA, SEM_DADO
            String mensagem
    ) {}

    public record DisparoManualResponse(
            String correlationId,
            String status, // DISPARADO, JA_EM_ANDAMENTO, RECUSADO_NAO_PREGAO
            String mensagem,
            List<ProgressoConjuntoDTO> progressoPorConjunto
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

    public record ErroCargaDTO(int linha, String coluna, String motivo) {}

    public record ItemValidacaoDTO(
            String teste,
            String classificacao,
            String resultado,
            BigDecimal medidaObservada,
            BigDecimal limiteAplicado,
            String detalhe
    ) {}

    public record CargaManualResponse(
            String correlationId,
            String status, // ACEITA, REPROVADA_VALIDACAO, ERRO_LEITURA
            String mensagem,
            List<ErroCargaDTO> errosLeitura,
            List<ItemValidacaoDTO> validacoesReprovadas
    ) {}

    public record AgendamentoRequest(
            UUID definicaoCurvaId,
            String conjuntoDados,
            String momentoCurva,
            String faixa,
            String expressaoHorario,
            String fusoHorario,
            Integer janelaTentativaMinutos,
            Integer intervaloTentativaSegundos
    ) {}

    public record AgendamentoResponse(
            UUID id,
            UUID definicaoCurvaId,
            String conjuntoDados,
            String momentoCurva,
            String faixa,
            String expressaoHorario,
            String fusoHorario,
            int janelaTentativaMinutos,
            int intervaloTentativaSegundos,
            boolean ativo,
            String criadoPor,
            java.time.LocalDateTime criadoEm,
            java.time.LocalDateTime atualizadoEm
    ) {}

    public record AgendamentoComUltimaExecucaoDTO(
            AgendamentoResponse agendamento,
            String ultimaExecucaoEstado,
            java.time.LocalDateTime ultimaExecucaoIniciadoEm,
            java.time.LocalDateTime ultimaExecucaoFinalizadoEm,
            String ultimaExecucaoMotivoSemDado
    ) {}

    public record IniciarBackfillRequest(
            String conjuntoDados,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataInicial,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataFinal,
            Integer concorrenciaMaxima
    ) {}

    public record IniciarBackfillResponse(
            UUID execucaoMaeId,
            UUID correlacaoId
    ) {}

    public record ProgressoBackfillDTO(
            int total,
            int concluidas,
            int semDado,
            int falhas,
            int pendentes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConclusaoConstrucaoRequest(
            String status,   // "PUBLICADA" ou "ERRO"
            String motivo     // detalhe da causa quando status="ERRO"; pode ser null quando status="PUBLICADA"
    ) {}
}

