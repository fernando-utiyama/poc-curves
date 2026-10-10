package br.com.poc.domain.cadastro;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Campos de um vértice da Bloomberg digitados pelo gestor. */
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
