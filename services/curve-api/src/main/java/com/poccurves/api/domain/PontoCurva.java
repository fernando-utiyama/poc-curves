package com.poccurves.api.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um vértice de curva (de {@code tDadoCurva} ou {@code tCurvaData}) na data de referência consultada. */
public record PontoCurva(LocalDate dataVertice, BigDecimal valor) {
}
