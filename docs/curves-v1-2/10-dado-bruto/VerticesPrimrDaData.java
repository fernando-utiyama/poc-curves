package br.com.poc.domain.cadastro;

import java.util.List;

/** Vértices do dado bruto de uma curva numa data-base (B3, ANBIMA ou Bloomberg) e se a data já foi construída. */
public record VerticesPrimrDaData<T>(boolean curvaConstruida, List<T> vertices) {
}
