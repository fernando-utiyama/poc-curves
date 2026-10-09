package br.com.poc.domain.cadastro;

/** Curva que usa uma origem (provedor, produto e ticker do provedor), com a prioridade dessa origem na curva. */
public record CurvaPorOrigem(String codigo, String nome, Integer prioridade) {
}
