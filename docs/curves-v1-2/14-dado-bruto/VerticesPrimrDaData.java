package br.com.poc.domain.cadastro;

import java.util.List;

/** Vértices brutos de uma data e se ela já foi construída. */
public record VerticesPrimrDaData<T>(boolean curvaConstruida, List<T> vertices) {
}
