package br.com.poc.adapter.in.api.rest.dto;

import java.util.List;

/** Vértices brutos de uma data e se ela já foi construída. */
public record VerticesPrimrDaDataResponse<T>(boolean curvaConstruida, List<T> vertices) {
}
