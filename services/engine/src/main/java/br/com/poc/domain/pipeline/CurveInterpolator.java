package br.com.poc.domain.pipeline;

import java.math.BigDecimal;

public interface CurveInterpolator {

    String getMethodName();

    /**
     * Interpola o valor (taxa ou fator) para um prazo no intervalo [min(prazos), max(prazos)].
     *
     * @param prazoAlvo Prazo a ser interpolado
     * @param prazos Array ordenado com os prazos dos vértices de referência
     * @param valores Array ordenado com os valores correspondentes aos prazos
     * @return Valor interpolado
     */
    BigDecimal interpolar(double prazoAlvo, double[] prazos, double[] valores);
}
