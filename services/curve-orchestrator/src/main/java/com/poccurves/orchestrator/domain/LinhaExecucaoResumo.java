package com.poccurves.orchestrator.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Projeção de leitura para a tela de consulta de execuções (mais simples que {@link ExecucaoCurva}
 * completa) — {@code codigoCurva} já resolvido (código da definição de curva quando existir, senão
 * o próprio conjunto de dados).
 */
public record LinhaExecucaoResumo(
        UUID id,
        UUID correlacaoId,
        String tipoDisparo,
        String codigoCurva,
        LocalDate dataReferencia,
        String momentoCurva,
        String estado,
        String disparadoPor,
        Instant iniciadoEm,
        Instant finalizadoEm,
        String mensagemErro
) {}
