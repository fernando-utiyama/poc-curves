package com.poccurves.processor.domain.curva;

/**
 * A curva pronta recebida não tem nenhum vértice. Falha permanente — vai
 * direto para a dead-letter como EMPTY_CURVE.
 */
public final class CurvaVaziaException extends RuntimeException {

    public CurvaVaziaException(String curveIdOrigem) {
        super("EMPTY_CURVE: curva \"" + curveIdOrigem + "\" recebida sem nenhum vértice");
    }
}
