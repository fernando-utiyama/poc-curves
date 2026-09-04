package com.poccurves.processor.domain;

/** O arquivo de carga manual excede o limite de tamanho permitido (tarefa 6.11). */
public final class ArquivoCargaExcedeTamanhoException extends RuntimeException {

    public ArquivoCargaExcedeTamanhoException(long tamanhoBytes, long limiteBytes) {
        super("CARGA_EXCEDE_TAMANHO: arquivo com " + tamanhoBytes + " bytes excede o limite de " + limiteBytes + " bytes");
    }
}
