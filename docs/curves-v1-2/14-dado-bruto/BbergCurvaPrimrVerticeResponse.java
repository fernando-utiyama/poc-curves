package br.com.poc.adapter.in.api.rest.dto;

import br.com.poc.domain.cadastro.BbergCurvaPrimr;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Vértice de dado bruto da Bloomberg")
public record BbergCurvaPrimrVerticeResponse(
    @Schema(description = "Id do vértice", example = "1") Integer id,
    @Schema(description = "Ticker Bloomberg", example = "PRE-BRL-15") String tickerBloomberg,
    @Schema(description = "Último preço, como gravado (texto, sem arredondar)", example = "97.280000000000") String precoUltimo,
    @Schema(description = "Preço de liquidação (texto)", example = "97.250000000000") String precoLiquidacao,
    @Schema(description = "Preço médio (texto)", example = "97.300000000000") String precoMedio,
    @Schema(description = "Dia de vencimento", example = "15") Integer diaVencimento,
    @Schema(description = "Data de liquidação financeira", example = "2026-09-03")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataLiquidacaoFinanceira,
    @Schema(description = "Forma de liquidação", example = "FINANCEIRA") String formaLiquidacao,
    @Schema(description = "Data de vencimento do contrato", example = "2027-01-15")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataVencimentoContrato,
    @Schema(description = "Data do último negócio", example = "2026-09-02")
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dataUltimoNegocio
) {

    public static BbergCurvaPrimrVerticeResponse fromDomain(BbergCurvaPrimr v) {
        return new BbergCurvaPrimrVerticeResponse(
            v.id(),
            v.tickerBloomberg(),
            v.precoUltimo() != null ? v.precoUltimo().toPlainString() : null,
            v.precoLiquidacao() != null ? v.precoLiquidacao().toPlainString() : null,
            v.precoMedio() != null ? v.precoMedio().toPlainString() : null,
            v.diaVencimento(),
            v.dataLiquidacaoFinanceira(),
            v.formaLiquidacao(),
            v.dataVencimentoContrato(),
            v.dataUltimoNegocio());
    }
}
