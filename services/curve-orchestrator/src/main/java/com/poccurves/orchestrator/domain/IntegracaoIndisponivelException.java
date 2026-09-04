package com.poccurves.orchestrator.domain;

/** Traduz falha de transporte HTTP (feeder-marketdata, curve-processor) para um tipo de domínio. */
public class IntegracaoIndisponivelException extends RuntimeException {
    public IntegracaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
