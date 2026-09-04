package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.RoundingPolicy;
import com.poccurves.engine.domain.curva.Vertice;




import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Objects;

/**
 * Interpolação log-linear sobre o fator de desconto em função do prazo (FLAT_FORWARD).
 * <p>
 * Decisão de design:
 * Mantém constante a taxa forward implícita entre dois vértices adjacentes (t1, DF1) e (t2, DF2).
 * O fator de desconto no prazo t é calculado por:
 * DF(t) = DF1 * (DF2/DF1)^((t-t1)/(t2-t1))
 * Depois, converte de volta para a taxa através da fórmula inversa:
 * r(t) = DF(t)^(-252/t) - 1
 */
public final class InterpoladorFlatForward implements Interpolador {

    public static final String IDENTIFICADOR = "FLAT_FORWARD";
    private static final BigDecimal BASE_DIAS = BigDecimal.valueOf(252);

    @Override
    public String identificador() {
        return IDENTIFICADOR;
    }

    @Override
    public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis, MathContext mathContext) {
        Objects.requireNonNull(verticesOrdenados, "verticesOrdenados não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");
        if (verticesOrdenados.size() < 2) {
            throw new IllegalArgumentException("interpolação exige ao menos 2 vértices");
        }

        Vertice primeiro = verticesOrdenados.get(0);
        Vertice ultimo = verticesOrdenados.get(verticesOrdenados.size() - 1);
        if (prazoDiasUteis < primeiro.prazoDiasUteis() || prazoDiasUteis > ultimo.prazoDiasUteis()) {
            throw new IllegalArgumentException(
                    "prazo " + prazoDiasUteis + " fora do intervalo dos vértices ["
                            + primeiro.prazoDiasUteis() + ", " + ultimo.prazoDiasUteis() + "]");
        }

        for (Vertice v : verticesOrdenados) {
            if (v.prazoDiasUteis() == prazoDiasUteis) {
                return v.taxa();
            }
        }

        Vertice anterior = null;
        Vertice posterior = null;
        for (int i = 0; i < verticesOrdenados.size() - 1; i++) {
            Vertice atual = verticesOrdenados.get(i);
            Vertice proximo = verticesOrdenados.get(i + 1);
            if (atual.prazoDiasUteis() < prazoDiasUteis && prazoDiasUteis < proximo.prazoDiasUteis()) {
                anterior = atual;
                posterior = proximo;
                break;
            }
        }
        if (anterior == null) {
            throw new IllegalArgumentException(
                    "não foi possível localizar par de vértices adjacentes para o prazo " + prazoDiasUteis);
        }

        BigDecimal t1 = BigDecimal.valueOf(anterior.prazoDiasUteis());
        BigDecimal t2 = BigDecimal.valueOf(posterior.prazoDiasUteis());
        BigDecimal t = BigDecimal.valueOf(prazoDiasUteis);
        BigDecimal r1 = anterior.taxa();
        BigDecimal r2 = posterior.taxa();

        if (r1.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("taxa <= 0 no vértice " + anterior.prazoDiasUteis());
        }
        if (r2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("taxa <= 0 no vértice " + posterior.prazoDiasUteis());
        }

        BigDecimal df1 = calcularFatorDesconto(r1, t1, mathContext);
        BigDecimal df2 = calcularFatorDesconto(r2, t2, mathContext);

        if (df1.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fator de desconto <= 0 no vértice " + anterior.prazoDiasUteis());
        }
        if (df2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("fator de desconto <= 0 no vértice " + posterior.prazoDiasUteis());
        }

        BigDecimal razaoDf = df2.divide(df1, mathContext);
        BigDecimal expoenteDf = t.subtract(t1).divide(t2.subtract(t1), mathContext);
        BigDecimal dfT = df1.multiply(RoundingPolicy.powerRaw(razaoDf, expoenteDf, mathContext), mathContext);

        BigDecimal expoenteR = BASE_DIAS.negate().divide(t, mathContext);
        return RoundingPolicy.powerRaw(dfT, expoenteR, mathContext).subtract(BigDecimal.ONE);
    }

    private BigDecimal calcularFatorDesconto(BigDecimal r, BigDecimal d, MathContext mathContext) {
        BigDecimal base = BigDecimal.ONE.add(r);
        BigDecimal expoente = d.negate().divide(BASE_DIAS, mathContext);
        return RoundingPolicy.powerRaw(base, expoente, mathContext);
    }
}
