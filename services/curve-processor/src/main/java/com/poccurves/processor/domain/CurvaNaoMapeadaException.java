package com.poccurves.processor.domain;

/**
 * Nenhuma definicao_curva encontrada para o identificador de curva
 * declarado na origem (payload.curveId). Falha permanente — vai direto
 * para a dead-letter como UNMAPPED_CURVE.
 */
public final class CurvaNaoMapeadaException extends RuntimeException {

    public CurvaNaoMapeadaException(String curveIdOrigem) {
        super("UNMAPPED_CURVE: nenhuma definição de curva encontrada para o identificador de origem \"" + curveIdOrigem + "\"");
    }
}
