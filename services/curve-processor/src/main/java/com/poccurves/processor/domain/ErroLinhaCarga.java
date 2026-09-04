package com.poccurves.processor.domain;

/**
 * Um erro de leitura numa linha/coluna específica do arquivo de carga
 * manual de curva — docs/leiaute-carga-manual-curva.md.
 */
public record ErroLinhaCarga(int numeroLinha, String coluna, String mensagem) {

    public ErroLinhaCarga {
        if (numeroLinha < 1) {
            throw new IllegalArgumentException("numeroLinha deve ser >= 1: " + numeroLinha);
        }
        if (coluna == null || coluna.isBlank()) {
            throw new IllegalArgumentException("coluna não pode ser nula ou vazia");
        }
        if (mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("mensagem não pode ser nula ou vazia");
        }
    }
}
