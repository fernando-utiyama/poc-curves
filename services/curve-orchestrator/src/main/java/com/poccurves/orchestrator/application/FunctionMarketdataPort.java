package com.poccurves.orchestrator.application;

import java.time.LocalDate;
import java.util.UUID;

public interface FunctionMarketdataPort {

    record ResultadoAquisicao(
            String kind,
            String loteId,
            Integer totalBlocos,
            String motivo,
            String diagnostico
    ) {}

    /** @throws com.poccurves.orchestrator.domain.IntegracaoIndisponivelException em falha de transporte */
    ResultadoAquisicao acionar(String dataset, LocalDate referenceDate, String faixa, UUID correlationId);
}
