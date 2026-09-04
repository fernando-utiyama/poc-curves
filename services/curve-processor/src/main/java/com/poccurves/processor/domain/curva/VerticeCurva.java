package com.poccurves.processor.domain.curva;
import com.poccurves.processor.domain.ingestao.PontoDadoMercado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Um vértice (prazo, taxa) de uma versão de curva, espelhando
 * db/migration/V4__versao_curva.sql (tabela vertice_curva). O id e a
 * associação a uma versao_curva são atribuídos na persistência, não aqui —
 * mesma convenção de PontoDadoMercado.
 */
public record VerticeCurva(
        int prazoDiasUteis,
        Integer prazoDiasCorridos,
        LocalDate dataVencimento,
        BigDecimal taxa,
        BigDecimal fatorDesconto
) {
    public VerticeCurva {
        if (prazoDiasUteis < 0) {
            throw new IllegalArgumentException("prazoDiasUteis não pode ser negativo: " + prazoDiasUteis);
        }
        Objects.requireNonNull(taxa, "taxa não pode ser nula");
    }
}
