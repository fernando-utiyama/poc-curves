package com.poccurves.processor.domain.curva;

/**
 * O {@code payloadKind} do evento (READY_CURVE) é incoerente com o
 * {@code modoOrigem} da definição de curva resolvida — só curvas com
 * {@code modoOrigem = IMPORTED} podem receber publicação de curva pronta;
 * {@code BOOTSTRAPPED} é exclusiva do curve-engine (bootstrapping via
 * curve.build.requested.v1). Nomeia os dois lados da incoerência na
 * mensagem, como pedido pela tarefa 5.3. Falha permanente — dead-letter.
 */
public final class IncoerenciaModoOrigemException extends RuntimeException {

    public IncoerenciaModoOrigemException(String payloadKind, Object modoOrigem) {
        super("payloadKind=" + payloadKind + " incoerente com modoOrigem=" + modoOrigem
                + " da definição de curva resolvida — só definições IMPORTED aceitam curva pronta");
    }
}
