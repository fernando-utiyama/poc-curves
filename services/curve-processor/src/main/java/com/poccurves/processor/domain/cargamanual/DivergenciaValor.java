package com.poccurves.processor.domain.cargamanual;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Divergência detectada quando o mesmo ponto de dado de mercado (mesma
 * chave) é regravado com valor diferente do anteriormente persistido.
 * Ausência de divergência (valores numericamente iguais) não produz
 * instância nenhuma — detectar() retorna vazio.
 */
public record DivergenciaValor(String chaveInstrumento, BigDecimal valorAnterior, BigDecimal valorNovo) {

    public DivergenciaValor {
        if (chaveInstrumento == null || chaveInstrumento.isBlank()) {
            throw new IllegalArgumentException("chaveInstrumento não pode ser nula ou vazia");
        }
        Objects.requireNonNull(valorAnterior, "valorAnterior não pode ser nulo");
        Objects.requireNonNull(valorNovo, "valorNovo não pode ser nulo");
    }

    /**
     * Compara o valor anterior persistido com o novo valor recebido para a mesma chave.
     * A comparação é numérica (via compareTo), não de escala/representação — 1.10 e 1.100
     * não são divergência.
     *
     * @throws IllegalArgumentException se chaveInstrumento for nula ou vazia
     * @throws NullPointerException     se valorAnterior ou valorNovo forem nulos
     * @return vazio quando os valores são numericamente iguais; a divergência, caso contrário
     */
    public static Optional<DivergenciaValor> detectar(String chaveInstrumento, BigDecimal valorAnterior, BigDecimal valorNovo) {
        if (chaveInstrumento == null || chaveInstrumento.isBlank()) {
            throw new IllegalArgumentException("chaveInstrumento não pode ser nula ou vazia");
        }
        Objects.requireNonNull(valorAnterior, "valorAnterior não pode ser nulo");
        Objects.requireNonNull(valorNovo, "valorNovo não pode ser nulo");

        if (valorAnterior.compareTo(valorNovo) == 0) {
            return Optional.empty();
        }
        return Optional.of(new DivergenciaValor(chaveInstrumento, valorAnterior, valorNovo));
    }
}
