package com.poccurves.api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Compara duas curvas prazo a prazo, sem interpolar. Prazos presentes em
 * apenas uma das curvas aparecem no resultado com o lado ausente como nulo.
 */
public final class ComparadorCurvas {

    private ComparadorCurvas() {
    }

    /**
     * Compara curvaA contra curvaB, prazo a prazo, ordenado por prazoDiasUteis crescente.
     *
     * @throws NullPointerException se curvaA ou curvaB forem nulas
     */
    public static List<PontoComparacao> comparar(Map<Integer, BigDecimal> curvaA, Map<Integer, BigDecimal> curvaB) {
        Objects.requireNonNull(curvaA, "curvaA não pode ser nula");
        Objects.requireNonNull(curvaB, "curvaB não pode ser nula");

        TreeSet<Integer> todosOsPrazos = new TreeSet<>();
        todosOsPrazos.addAll(curvaA.keySet());
        todosOsPrazos.addAll(curvaB.keySet());

        List<PontoComparacao> resultado = new ArrayList<>();
        for (Integer prazo : todosOsPrazos) {
            resultado.add(new PontoComparacao(prazo, curvaA.get(prazo), curvaB.get(prazo)));
        }
        return resultado;
    }
}
