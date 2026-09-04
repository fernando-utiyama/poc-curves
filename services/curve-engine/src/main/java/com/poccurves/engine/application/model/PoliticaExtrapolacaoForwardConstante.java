package com.poccurves.engine.application.model;




import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Política de extrapolação mantendo a taxa forward implícita constante (FORWARD_CONSTANTE).
 * <p>
 * Decisão de design:
 * Quando o prazo pedido está acima do último vértice, calcula a taxa forward implícita
 * entre o penúltimo e o último vértice (usando a mesma fórmula de FLAT_FORWARD),
 * e projeta o fator de desconto além do último vértice mantendo essa mesma taxa forward.
 * Quando o prazo pedido está abaixo do primeiro vértice, usa a taxa forward implícita
 * entre o primeiro e o segundo vértice.
 * Caso degenerado: se a curva tiver apenas 1 vértice, repete a taxa desse vértice (igual a TAXA_CONSTANTE).
 */
public final class PoliticaExtrapolacaoForwardConstante implements PoliticaExtrapolacao {

    public static final String IDENTIFICADOR = "FORWARD_CONSTANTE";
    private static final BigDecimal BASE_DIAS = BigDecimal.valueOf(252);

    @Override
    public String identificador() {
        return IDENTIFICADOR;
    }

    @Override
    public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis) {
        Objects.requireNonNull(verticesOrdenados, "verticesOrdenados não pode ser nulo");
        if (verticesOrdenados.isEmpty()) {
            throw new IllegalArgumentException("verticesOrdenados não pode ser vazio");
        }

        Vertice primeiro = verticesOrdenados.get(0);
        if (verticesOrdenados.size() == 1) {
            return primeiro.taxa();
        }

        Vertice ultimo = verticesOrdenados.get(verticesOrdenados.size() - 1);
        if (prazoDiasUteis >= primeiro.prazoDiasUteis() && prazoDiasUteis <= ultimo.prazoDiasUteis()) {
            throw new IllegalArgumentException("prazo " + prazoDiasUteis + " não requer extrapolação");
        }

        MathContext mathContext = MathContext.DECIMAL128;
        BigDecimal t = BigDecimal.valueOf(prazoDiasUteis);

        if (prazoDiasUteis > ultimo.prazoDiasUteis()) {
            Vertice penultimo = verticesOrdenados.get(verticesOrdenados.size() - 2);
            return extrapolar(penultimo, ultimo, t, mathContext);
        } else {
            Vertice segundo = verticesOrdenados.get(1);
            return extrapolar(primeiro, segundo, t, mathContext);
        }
    }

    private BigDecimal extrapolar(Vertice v1, Vertice v2, BigDecimal t, MathContext mathContext) {
        BigDecimal t1 = BigDecimal.valueOf(v1.prazoDiasUteis());
        BigDecimal t2 = BigDecimal.valueOf(v2.prazoDiasUteis());
        BigDecimal r1 = v1.taxa();
        BigDecimal r2 = v2.taxa();

        if (r1.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("taxa <= 0 no vértice " + v1.prazoDiasUteis());
        }
        if (r2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("taxa <= 0 no vértice " + v2.prazoDiasUteis());
        }

        BigDecimal df1 = calcularFatorDesconto(r1, t1, mathContext);
        BigDecimal df2 = calcularFatorDesconto(r2, t2, mathContext);

        if (df1.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fator de desconto <= 0 no vértice " + v1.prazoDiasUteis());
        }
        if (df2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fator de desconto <= 0 no vértice " + v2.prazoDiasUteis());
        }

        BigDecimal razaoDf = df1.divide(df2, mathContext);
        BigDecimal expoenteF = BASE_DIAS.divide(t2.subtract(t1), mathContext);
        BigDecimal f = RoundingPolicy.powerRaw(razaoDf, expoenteF, mathContext).subtract(BigDecimal.ONE);

        BigDecimal tBase;
        BigDecimal dfBase;
        if (t.compareTo(t2) > 0) {
            tBase = t2;
            dfBase = df2;
        } else {
            tBase = t1;
            dfBase = df1;
        }

        BigDecimal basePotencia = BigDecimal.ONE.add(f);
        BigDecimal expoenteT = tBase.subtract(t).divide(BASE_DIAS, mathContext);
        BigDecimal dfT = dfBase.multiply(RoundingPolicy.powerRaw(basePotencia, expoenteT, mathContext), mathContext);

        BigDecimal expoenteR = BASE_DIAS.negate().divide(t, mathContext);
        return RoundingPolicy.powerRaw(dfT, expoenteR, mathContext).subtract(BigDecimal.ONE);
    }

    private BigDecimal calcularFatorDesconto(BigDecimal r, BigDecimal d, MathContext mathContext) {
        BigDecimal base = BigDecimal.ONE.add(r);
        BigDecimal expoente = d.negate().divide(BASE_DIAS, mathContext);
        return RoundingPolicy.powerRaw(base, expoente, mathContext);
    }
}
