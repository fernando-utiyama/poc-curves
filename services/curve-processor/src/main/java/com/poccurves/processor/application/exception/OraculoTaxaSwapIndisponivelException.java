package com.poccurves.processor.application.exception;

import java.time.LocalDate;

/**
 * A curva PRE oficial da B3 (B3_CURVA_PRE, `referenceRatesProxy`) ainda não foi publicada para a
 * mesma data de pregão do TaxaSwap.txt, ou o arquivo não trouxe nenhum vértice de PRE — sem o
 * oráculo, a divergência não pode ser descartada, então a publicação de DCL/PTX/INP/DPL fica
 * bloqueada até o oráculo existir (fail-safe: nunca publicar sem prova, mesmo princípio de
 * design.md "nunca fabricar/estimar valor de mercado"). Falha transitória, ao contrário de
 * {@link OraculoTaxaSwapDivergenteException} — uma retentativa depois que B3_CURVA_PRE for
 * publicada pode ter sucesso.
 */
public final class OraculoTaxaSwapIndisponivelException extends RuntimeException {

    public OraculoTaxaSwapIndisponivelException(LocalDate dataReferencia) {
        super("ORACLE_UNAVAILABLE: oráculo B3_CURVA_PRE ainda não publicado para " + dataReferencia
                + " (ou TaxaSwap.txt sem vértices de PRE) — publicação de curvas TaxaSwap bloqueada até então");
    }
}
