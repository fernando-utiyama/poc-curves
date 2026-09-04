package com.poccurves.processor.domain.curva;

import java.util.Objects;
import java.util.UUID;

/**
 * Proveniência de uma versão de curva publicada pelo curve-processor,
 * espelhando db/migration/V4__versao_curva.sql (tabela procedencia_curva).
 * Para versões IMPORTADA: loteIngestaoId é o lote que trouxe a curva
 * pronta. Para versões CARREGADA: arquivoCarga/hashArquivo/carregadoPor/
 * justificativa são os que vêm da carga manual (seção 6). checksumModelo e
 * versaoMotor não se aplicam ao curve-processor (são do curve-engine) e
 * ficam nulos aqui.
 */
public record ProcedenciaCurva(
        UUID execucaoCurvaId,
        String referenciasInsumo,
        String hashConjuntoInsumos,
        Long loteIngestaoId,
        String arquivoCarga,
        String hashArquivo,
        String carregadoPor,
        String justificativa
) {
    public ProcedenciaCurva {
        Objects.requireNonNull(execucaoCurvaId, "execucaoCurvaId não pode ser nulo");
    }

    /** Proveniência de uma curva IMPORTADA (curva pronta recebida via Kafka). */
    public static ProcedenciaCurva importada(UUID execucaoCurvaId, long loteIngestaoId, String referenciasInsumo, String hashConjuntoInsumos) {
        return new ProcedenciaCurva(execucaoCurvaId, referenciasInsumo, hashConjuntoInsumos, loteIngestaoId, null, null, null, null);
    }

    /** Proveniência de uma curva CARREGADA (carga manual, seção 6 do backlog). */
    public static ProcedenciaCurva carregada(UUID execucaoCurvaId, String arquivoCarga, String hashArquivo, String carregadoPor, String justificativa) {
        if (arquivoCarga == null || arquivoCarga.isBlank()) {
            throw new IllegalArgumentException("arquivoCarga não pode ser nulo ou vazio");
        }
        if (hashArquivo == null || hashArquivo.isBlank()) {
            throw new IllegalArgumentException("hashArquivo não pode ser nulo ou vazio");
        }
        if (carregadoPor == null || carregadoPor.isBlank()) {
            throw new IllegalArgumentException("carregadoPor não pode ser nulo ou vazio");
        }
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("justificativa não pode ser nula ou vazia");
        }
        return new ProcedenciaCurva(execucaoCurvaId, null, null, null, arquivoCarga, hashArquivo, carregadoPor, justificativa);
    }
}
