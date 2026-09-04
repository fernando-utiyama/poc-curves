package com.poccurves.engine.domain.versao;

public class VersaoJaExisteException extends RuntimeException {
    public VersaoJaExisteException(String mensagem) {
        super(mensagem);
    }
}
