package com.poccurves.orchestrator.application.port;

import java.time.LocalDate;

/**
 * Despacho de construção das 5 curvas TS B3 (PRE/DCL/PTX/INP/DPL, openspec/changes/
 * b3-additional-curves) no curve-engine — schema legado (V22), fora do fluxo genérico de {@link
 * BuildRequestPort} (que resolve definições BOOTSTRAPPED via definicao_curva; essas 5 curvas não
 * usam mais essa tabela).
 */
public interface ConstrucaoCurvaB3Port {
    void construir(String tickerIndcd, LocalDate referenceDate);
}
