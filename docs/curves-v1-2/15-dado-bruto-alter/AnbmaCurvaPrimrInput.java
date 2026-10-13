package br.com.poc.domain.cadastro;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Campos editáveis de um vértice da ANBIMA (tipo do título e vencimento são opcionais). */
public record AnbmaCurvaPrimrInput(
    Integer prazoDiasCorridos,
    BigDecimal taxa,
    String tipoInstrumento,
    LocalDate vencimento
) {
}
