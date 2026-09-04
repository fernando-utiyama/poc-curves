package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Contagem de dias corridos sobre base fixa (Atual/360, Atual/365) — não
 * depende de calendário de dias úteis. Atual/252 (base em dias úteis) exige
 * o calendário de feriados B3/ANBIMA, e utiliza {@link CalendarioPregao#diasUteisEntre}
 * como a fonte da contagem de dias úteis para calcular a fração.
 */
public enum ConvencaoContagemDias {
    ATUAL_360(360),
    ATUAL_365(365);

    private final int base;

    ConvencaoContagemDias(int base) {
        this.base = base;
    }

    /** A base fixa da convenção (360 ou 365). */
    public int base() {
        return base;
    }

    /**
     * Fração de ano entre dataInicio (inclusive) e dataFim (exclusive), pela base desta convenção:
     * dias corridos entre as datas, dividido pela base.
     *
     * @throws NullPointerException     se dataInicio, dataFim ou mathContext forem nulos
     * @throws IllegalArgumentException se dataFim for anterior a dataInicio
     */
    public BigDecimal fracaoAno(LocalDate dataInicio, LocalDate dataFim, MathContext mathContext) {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser nula");
        Objects.requireNonNull(dataFim, "dataFim não pode ser nula");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");
        if (dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException(
                    "dataFim (" + dataFim + ") não pode ser anterior a dataInicio (" + dataInicio + ")");
        }

        long dias = diasCorridos(dataInicio, dataFim);
        return BigDecimal.valueOf(dias).divide(BigDecimal.valueOf(base), mathContext);
    }

    /**
     * Dias corridos entre dataInicio (inclusive) e dataFim (exclusive) — útil sozinho, sem
     * dividir pela base de nenhuma convenção.
     *
     * @throws NullPointerException se dataInicio ou dataFim forem nulos
     */
    public static long diasCorridos(LocalDate dataInicio, LocalDate dataFim) {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser nula");
        Objects.requireNonNull(dataFim, "dataFim não pode ser nula");
        return ChronoUnit.DAYS.between(dataInicio, dataFim);
    }

    /**
     * Fração de ano pela base 252 (dias úteis).
     *
     * @param diasUteis o número de dias úteis (ver CalendarioPregao.diasUteisEntre)
     * @param mathContext o contexto matemático para a divisão
     * @return a fração de ano
     * @throws NullPointerException se mathContext for nulo
     * @throws IllegalArgumentException se diasUteis for negativo
     */
    public static BigDecimal fracaoAnoBase252(long diasUteis, MathContext mathContext) {
        if (diasUteis < 0) {
            throw new IllegalArgumentException("diasUteis não pode ser negativo");
        }
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");
        return BigDecimal.valueOf(diasUteis).divide(BigDecimal.valueOf(252), mathContext);
    }
}
