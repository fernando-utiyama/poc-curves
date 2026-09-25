package br.com.poc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VerticeInsumo(
    String ticker,
    LocalDate dataBase,
    LocalDate dataVertice,
    Integer diasUteis,
    Integer diasCorridos,
    BigDecimal taxa,
    BigDecimal valor,
    String flagMercado
) {
}
