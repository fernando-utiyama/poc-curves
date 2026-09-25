package br.com.poc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VerticeCalculado(
    String ticker,
    LocalDate dataBase,
    LocalDate dataVertice,
    Integer diasUteis,
    Integer diasCorridos,
    Integer diasPeriodo,
    BigDecimal taxaAnualizada,
    BigDecimal fatorDiario,
    BigDecimal fatorAcumulado
) {
}
