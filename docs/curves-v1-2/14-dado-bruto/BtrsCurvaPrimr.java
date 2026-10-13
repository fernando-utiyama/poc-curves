package br.com.poc.domain.cadastro;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vértice do dado bruto da B3 (tBtrsCurvaPrimr). */
public record BtrsCurvaPrimr(
    Integer id,
    String nomeCurva,
    LocalDate dataBase,
    Integer diasCorridos,
    Integer diasUteis,
    BigDecimal valor,
    BigDecimal fatorAcumulado,
    BigDecimal fatorDia
) {

    /** Data do vértice = data-base + dias corridos. */
    public LocalDate dataVertice() {
        return dataBase != null && diasCorridos != null ? dataBase.plusDays(diasCorridos) : null;
    }
}
