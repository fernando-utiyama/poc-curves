package com.poccurves.engine.domain.interpolacao;
import com.poccurves.engine.domain.curva.CurvaJuros;
import com.poccurves.engine.domain.curva.Vertice;



import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * Contrato de um interpolador de taxa sobre uma lista de vértices. A lista
 * recebida DEVE estar ordenada por prazoDiasUteis crescente (é a garantia
 * que CurvaJuros.vertices() já entrega) — o interpolador não reordena.
 * Implementações são selecionáveis por identificador via InterpoladorRegistry.
 */
public interface Interpolador {

    /** Identificador único do interpolador no registro (ex.: "LINEAR"). */
    String identificador();

    /**
     * Taxa no prazoDiasUteis informado. Se o prazo coincidir exatamente com
     * um vértice, retorna a taxa do vértice sem interpolar. Fora do
     * intervalo [primeiro vértice, último vértice], é responsabilidade de
     * uma política de extrapolação (não implementada aqui) — este método
     * lança exceção nesse caso.
     *
     * @throws IllegalArgumentException se houver menos de 2 vértices, ou se
     *                                   prazoDiasUteis estiver fora do intervalo dos vértices
     */
    BigDecimal taxaEm(List<Vertice> verticesOrdenados, int prazoDiasUteis, MathContext mathContext);
}
