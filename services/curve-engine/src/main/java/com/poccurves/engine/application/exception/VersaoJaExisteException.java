package com.poccurves.engine.application.exception;

public class VersaoJaExisteException extends RuntimeException {
    public VersaoJaExisteException(String mensagem) {
        super(mensagem);
    }
}
