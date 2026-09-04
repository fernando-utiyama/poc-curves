package com.poccurves.processor.domain.ingestao;
import com.poccurves.processor.domain.parsing.DatasetParser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Ponto de dado de mercado individual produzido por um DatasetParser,
 * espelhando os campos de ponto_dado_mercado (db/migration/V2__dado_mercado.sql)
 * que o próprio parser conhece — id, lote_ingestao_id e atualizado_em são
 * atribuídos na persistência, não aqui.
 */
public record PontoDadoMercado(
        String fonte,
        String conjuntoDados,
        LocalDate dataReferencia,
        String chaveInstrumento,
        BigDecimal valor,
        String tipoCotacao,
        LocalDate dataVencimento
) {
    public PontoDadoMercado {
        if (fonte == null || fonte.isBlank()) {
            throw new IllegalArgumentException("fonte não pode ser nula ou vazia");
        }
        if (conjuntoDados == null || conjuntoDados.isBlank()) {
            throw new IllegalArgumentException("conjuntoDados não pode ser nulo ou vazio");
        }
        Objects.requireNonNull(dataReferencia, "dataReferencia não pode ser nula");
        if (chaveInstrumento == null || chaveInstrumento.isBlank()) {
            throw new IllegalArgumentException("chaveInstrumento não pode ser nula ou vazia");
        }
        Objects.requireNonNull(valor, "valor não pode ser nulo");
    }
}
