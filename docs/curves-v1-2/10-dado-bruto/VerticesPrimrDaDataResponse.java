package br.com.poc.adapter.in.api.rest.dto;

import java.util.List;

/** Resposta da consulta do dado bruto de uma data: os vértices e se a data já foi construída. */
public record VerticesPrimrDaDataResponse<T>(boolean curvaConstruida, List<T> vertices) {
}
