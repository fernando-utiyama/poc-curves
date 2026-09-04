package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Um vértice de curva de juros, espelhando os campos relevantes de
 * vertice_curva (db/migration/V4__versao_curva.sql): prazo em dias úteis é a
 * chave de amostragem; dias corridos, data de vencimento e fator de desconto
 * são opcionais (o fator de desconto pode não estar calculado ainda).
 */
public record Vertice(
        int prazoDiasUteis,
        Integer prazoDiasCorridos,
        LocalDate dataVencimento,
        BigDecimal taxa,
        BigDecimal fatorDesconto
) {
    public Vertice {
        if (prazoDiasUteis <= 0) {
            throw new IllegalArgumentException("prazoDiasUteis deve ser positivo: " + prazoDiasUteis);
        }
        Objects.requireNonNull(taxa, "taxa não pode ser nula");
    }
}
