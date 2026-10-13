package br.com.poc.domain.cadastro;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vértice bruto da ANBIMA (tAnbmaCurvaPrimr). */
public record AnbmaCurvaPrimr(
    Long id,
    String nomeCurva,
    LocalDate dataBase,
    BigDecimal taxa,
    BigDecimal vertice,
    String tipoInstrumento,
    LocalDate vencimento
) {
}
