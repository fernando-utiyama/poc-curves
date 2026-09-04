package com.poccurves.engine.domain.versao;

import java.util.UUID;

/**
 * Registro de auditoria imutável que registra a procedência de uma versão de curva.
 * Espelha a tabela procedencia_curva (db/migration/V4__versao_curva.sql e V5__modelo_curva.sql).
 */
public record ProcedenciaCurva(
        UUID versaoCurvaId,
        UUID execucaoCurvaId,
        int numeroVersaoDefinicao,
        String checksumModelo,
        String referenciasInsumo,
        String hashConjuntoInsumos,
        Long loteIngestaoId,
        String arquivoCarga,
        String hashArquivo,
        String carregadoPor,
        String justificativa,
        String versaoMotor,
        UUID modeloCurvaId
) {
    public ProcedenciaCurva {
        if (versaoCurvaId == null) {
            throw new IllegalArgumentException("versaoCurvaId não pode ser nulo");
        }
        if (execucaoCurvaId == null) {
            throw new IllegalArgumentException("execucaoCurvaId não pode ser nulo");
        }
        if (numeroVersaoDefinicao <= 0) {
            throw new IllegalArgumentException("numeroVersaoDefinicao deve ser positivo");
        }
    }
}
