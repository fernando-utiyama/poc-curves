package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;



import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Interpolação de taxa em função do prazo utilizando Spline Cúbico Natural.
 * Assegura que a curva de taxas seja suave (contínua até a segunda derivada).
 */
public final class InterpoladorSplineCubicoNatural implements Interpolador {

    public static final String IDENTIFICADOR = "SPLINE_CUBICO_NATURAL";

    @Override
    public String identificador() {
        return IDENTIFICADOR;
    }

    @Override
    public BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis, MathContext mathContext) {
        Objects.requireNonNull(verticesOrdenados, "verticesOrdenados não pode ser nulo");
        Objects.requireNonNull(mathContext, "mathContext não pode ser nulo");
        if (verticesOrdenados.size() < 2) {
            throw new IllegalArgumentException("interpolação spline cúbico natural exige ao menos 2 vértices");
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

        List<BigDecimal> xs = new ArrayList<>(verticesOrdenados.size());
        List<BigDecimal> ys = new ArrayList<>(verticesOrdenados.size());

        for (Vertice v : verticesOrdenados) {
            xs.add(BigDecimal.valueOf(v.prazoDiasUteis()));
            ys.add(v.taxa());
        }

        return SplineCubicaNatural.avaliar(xs, ys, BigDecimal.valueOf(prazoDiasUteis), mathContext);
    }
}
