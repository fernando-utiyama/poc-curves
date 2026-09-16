package com.poccurves.processor.application.exception;

import java.util.List;

/**
 * Os vértices de PRE extraídos do TaxaSwap.txt divergem dos vértices de PRE já publicados via
 * curva pronta de referência (B3_CURVA_PRE) para a mesma data — spec "Validação do layout por
 * oráculo cruzado" (openspec/changes/b3-additional-curves). Falha permanente: bloqueia a
 * publicação de toda curva derivada da mesma aquisição do arquivo (DCL/PTX/INP/DPL), nunca só a
 * curva em processamento no momento — a divergência é do arquivo inteiro, não de um vértice
 * isolado.
 */
public final class OraculoTaxaSwapDivergenteException extends RuntimeException {

    public OraculoTaxaSwapDivergenteException(List<Integer> prazosDivergentesDiasUteis) {
        super("ORACLE_DIVERGENCE: vértices de PRE do TaxaSwap.txt divergem do oráculo B3_CURVA_PRE nos prazos (dias úteis): "
                + prazosDivergentesDiasUteis);
    }
}
