package br.com.poc.domain.cadastro;

/** Quantas linhas de uma tabela dependem de uma curva (e impedem a exclusão dela). */
public record LinhasPorTabela(String tabela, int linhas) {
}
