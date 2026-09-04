package com.poccurves.engine.application.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Curva de juros: coleção imutável de vértices ordenados por prazo em dias
 * úteis, sem prazo duplicado. Amostra taxa e fator de desconto exatamente em
 * um vértice existente — interpolação para prazo fora dos vértices é
 * responsabilidade de um interpolador (não implementado ainda neste kernel).
 */
public final class CurvaJuros {

    private final List<Vertice> vertices;

    private CurvaJuros(List<Vertice> verticesOrdenados) {
        this.vertices = verticesOrdenados;
    }

    /**
     * Constrói a curva a partir de uma lista de vértices em qualquer ordem.
     * Ordena deterministicamente por prazoDiasUteis crescente.
     *
     * @throws IllegalArgumentException se a lista for nula, vazia, ou contiver prazo duplicado
     */
    public static CurvaJuros de(List<Vertice> vertices) {
        Objects.requireNonNull(vertices, "vertices não pode ser nulo");
        if (vertices.isEmpty()) {
            throw new IllegalArgumentException("vertices não pode ser vazio");
        }

        List<Vertice> ordenados = new ArrayList<>(vertices);
        ordenados.sort(Comparator.comparingInt(Vertice::prazoDiasUteis));

        for (int i = 1; i < ordenados.size(); i++) {
            int prazoAtual = ordenados.get(i).prazoDiasUteis();
            int prazoAnterior = ordenados.get(i - 1).prazoDiasUteis();
            if (prazoAtual == prazoAnterior) {
                throw new IllegalArgumentException("prazo duplicado: " + prazoAtual + " dias úteis");
            }
        }

        return new CurvaJuros(List.copyOf(ordenados));
    }

    /** Vértices ordenados por prazoDiasUteis crescente, lista imutável. */
    public List<Vertice> vertices() {
        return vertices;
    }

    private Vertice verticeEm(int prazoDiasUteis) {
        return vertices.stream()
                .filter(v -> v.prazoDiasUteis() == prazoDiasUteis)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "prazo " + prazoDiasUteis + " dias úteis não é vértice desta curva "
                                + "(interpolação não implementada ainda)"));
    }

    /**
     * Taxa exatamente no vértice de prazoDiasUteis informado, sem interpolação.
     *
     * @throws IllegalArgumentException se prazoDiasUteis não for um vértice desta curva
     */
    public BigDecimal taxaEm(int prazoDiasUteis) {
        return verticeEm(prazoDiasUteis).taxa();
    }

    /**
     * Fator de desconto exatamente no vértice de prazoDiasUteis informado, se já calculado.
     *
     * @throws IllegalArgumentException se prazoDiasUteis não for um vértice desta curva
     */
    public Optional<BigDecimal> fatorDescontoEm(int prazoDiasUteis) {
        return Optional.ofNullable(verticeEm(prazoDiasUteis).fatorDesconto());
    }
}
