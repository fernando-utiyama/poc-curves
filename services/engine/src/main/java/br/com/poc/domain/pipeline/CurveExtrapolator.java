package br.com.poc.domain.pipeline;

import java.math.BigDecimal;

public interface CurveExtrapolator {

    enum Direcao {
        INFERIOR, // Prazo < min(prazos)
        SUPERIOR  // Prazo > max(prazos)
    }

    String getPolicyName();

    /**
     * Extrapola o valor (taxa ou fator) para um prazo além dos nós observados.
     *
     * @param prazoAlvo Prazo fora do domínio
     * @param prazos Array ordenado com os prazos dos vértices observados
     * @param valores Array ordenado com os valores correspondentes
     * @param direcao INFERIOR (esquerda) ou SUPERIOR (direita)
     * @param baseAnual Base anual (ex: 252 ou 360)
     * @return Valor extrapolado
     */
    BigDecimal extrapolar(
        double prazoAlvo,
        double[] prazos,
        double[] valores,
        Direcao direcao,
        int baseAnual
    );
}
