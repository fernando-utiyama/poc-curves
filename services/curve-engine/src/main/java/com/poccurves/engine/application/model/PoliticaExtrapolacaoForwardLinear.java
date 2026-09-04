package com.poccurves.engine.application.model;




import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

public final class PoliticaExtrapolacaoForwardLinear implements PoliticaExtrapolacao {

    public static final String IDENTIFICADOR = "FORWARD_LINEAR";
    private static final BigDecimal BASE_DIAS = BigDecimal.valueOf(252);

    @Override
    public String identificador() {
        return IDENTIFICADOR;
    }

    @Override
    public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis) {
        Objects.requireNonNull(verticesOrdenados, "verticesOrdenados não pode ser nulo");
        if (verticesOrdenados.size() < 3) {
            throw new IllegalArgumentException("FORWARD_LINEAR exige ao menos 3 vértices para estabelecer uma tendência, recebido: " + verticesOrdenados.size());
        }

        Vertice primeiro = verticesOrdenados.get(0);
        Vertice ultimo = verticesOrdenados.get(verticesOrdenados.size() - 1);
        if (prazoDiasUteis >= primeiro.prazoDiasUteis() && prazoDiasUteis <= ultimo.prazoDiasUteis()) {
            throw new IllegalArgumentException("prazo " + prazoDiasUteis + " não requer extrapolação");
        }

        MathContext mc = MathContext.DECIMAL128;
        int n = verticesOrdenados.size() - 1;
        BigDecimal t = BigDecimal.valueOf(prazoDiasUteis);
        BigDecimal dois = BigDecimal.valueOf(2);

        if (prazoDiasUteis > ultimo.prazoDiasUteis()) {
            Vertice vA = verticesOrdenados.get(n - 2);
            Vertice vB = verticesOrdenados.get(n - 1);
            Vertice vC_ultimo = verticesOrdenados.get(n);

            BigDecimal fA = forwardImplicito(vA, vB, mc);
            BigDecimal fB = forwardImplicito(vB, vC_ultimo, mc);

            BigDecimal tA = BigDecimal.valueOf(vA.prazoDiasUteis());
            BigDecimal tB = BigDecimal.valueOf(vB.prazoDiasUteis());
            BigDecimal tC = BigDecimal.valueOf(vC_ultimo.prazoDiasUteis());

            BigDecimal tmA = tA.add(tB, mc).divide(dois, mc);
            BigDecimal tmB = tB.add(tC, mc).divide(dois, mc);

            BigDecimal inclinacao = fB.subtract(fA, mc).divide(tmB.subtract(tmA, mc), mc);

            BigDecimal tmC = tC.add(t, mc).divide(dois, mc);
            BigDecimal fC = fB.add(inclinacao.multiply(tmC.subtract(tmB, mc), mc), mc);

            BigDecimal dfUltimo = calcularFatorDesconto(vC_ultimo.taxa(), tC, mc);
            BigDecimal basePotencia = BigDecimal.ONE.add(fC, mc);
            BigDecimal expoenteT = tC.subtract(t, mc).divide(BASE_DIAS, mc);
            BigDecimal dfT = dfUltimo.multiply(RoundingPolicy.powerRaw(basePotencia, expoenteT, mc), mc);

            BigDecimal expoenteR = BASE_DIAS.negate().divide(t, mc);
            return RoundingPolicy.powerRaw(dfT, expoenteR, mc).subtract(BigDecimal.ONE, mc);
        } else {
            Vertice vA = verticesOrdenados.get(0);
            Vertice vB = verticesOrdenados.get(1);
            Vertice vC2 = verticesOrdenados.get(2);

            BigDecimal fA = forwardImplicito(vA, vB, mc);
            BigDecimal fB = forwardImplicito(vB, vC2, mc);

            BigDecimal tA = BigDecimal.valueOf(vA.prazoDiasUteis());
            BigDecimal tB = BigDecimal.valueOf(vB.prazoDiasUteis());
            BigDecimal tC = BigDecimal.valueOf(vC2.prazoDiasUteis());

            BigDecimal tmA = tA.add(tB, mc).divide(dois, mc);
            BigDecimal tmB = tB.add(tC, mc).divide(dois, mc);

            BigDecimal inclinacao = fB.subtract(fA, mc).divide(tmB.subtract(tmA, mc), mc);

            BigDecimal tmC = t.add(tA, mc).divide(dois, mc);
            BigDecimal fC = fA.add(inclinacao.multiply(tmC.subtract(tmA, mc), mc), mc);

            BigDecimal dfPrimeiro = calcularFatorDesconto(vA.taxa(), tA, mc);
            BigDecimal basePotencia = BigDecimal.ONE.add(fC, mc);
            BigDecimal expoenteT = tA.subtract(t, mc).divide(BASE_DIAS, mc);
            BigDecimal dfT = dfPrimeiro.multiply(RoundingPolicy.powerRaw(basePotencia, expoenteT, mc), mc);

            BigDecimal expoenteR = BASE_DIAS.negate().divide(t, mc);
            return RoundingPolicy.powerRaw(dfT, expoenteR, mc).subtract(BigDecimal.ONE, mc);
        }
    }

    private BigDecimal forwardImplicito(Vertice v1, Vertice v2, MathContext mc) {
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

        BigDecimal df1 = calcularFatorDesconto(r1, t1, mc);
        BigDecimal df2 = calcularFatorDesconto(r2, t2, mc);

        if (df1.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fator de desconto <= 0 no vértice " + v1.prazoDiasUteis());
        }
        if (df2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fator de desconto <= 0 no vértice " + v2.prazoDiasUteis());
        }

        BigDecimal razaoDf = df1.divide(df2, mc);
        BigDecimal expoenteF = BASE_DIAS.divide(t2.subtract(t1, mc), mc);
        return RoundingPolicy.powerRaw(razaoDf, expoenteF, mc).subtract(BigDecimal.ONE, mc);
    }

    private BigDecimal calcularFatorDesconto(BigDecimal r, BigDecimal d, MathContext mathContext) {
        BigDecimal base = BigDecimal.ONE.add(r, mathContext);
        BigDecimal expoente = d.negate().divide(BASE_DIAS, mathContext);
        return RoundingPolicy.powerRaw(base, expoente, mathContext);
    }
}
