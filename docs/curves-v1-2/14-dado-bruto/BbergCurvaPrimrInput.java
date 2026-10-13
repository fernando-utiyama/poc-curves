package br.com.poc.domain.cadastro;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Campos editáveis de um vértice da Bloomberg. */
public record BbergCurvaPrimrInput(
    String tickerBloomberg,
    BigDecimal precoUltimo,
    BigDecimal precoLiquidacao,
    BigDecimal precoMedio,
    Integer diaVencimento,
    LocalDate dataLiquidacaoFinanceira,
    String formaLiquidacao,
    LocalDate dataVencimentoContrato,
    LocalDate dataUltimoNegocio
) {
}
