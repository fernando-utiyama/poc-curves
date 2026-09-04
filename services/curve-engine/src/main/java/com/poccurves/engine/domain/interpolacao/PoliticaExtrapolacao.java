package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.Vertice;



import java.math.BigDecimal;
import java.util.List;

/**
 * Política de extrapolação: decide o que fazer quando o prazo pedido está
 * fora do intervalo [primeiro vértice, último vértice] da curva. Só é
 * consultada nesse caso — dentro do intervalo, a amostragem usa um
 * Interpolador normalmente (incluindo o caso de vértice exato).
 */
public interface PoliticaExtrapolacao {

    /** Identificador único da política no registro (ex.: "ESTRITA", "TAXA_CONSTANTE"). */
    String identificador();

    /**
     * Taxa para um prazo fora do intervalo dos vértices, segundo esta política.
     *
     * @throws NullPointerException     se verticesOrdenados for nulo
     * @throws IllegalArgumentException se verticesOrdenados estiver vazio
     */
    BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis);
}
