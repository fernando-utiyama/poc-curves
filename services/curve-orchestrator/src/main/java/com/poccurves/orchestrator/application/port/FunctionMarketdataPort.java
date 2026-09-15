package com.poccurves.orchestrator.application.port;

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

    /** @throws com.poccurves.orchestrator.application.exception.IntegracaoIndisponivelException em falha de transporte */
    ResultadoAquisicao acionar(String dataset, LocalDate referenceDate, String faixa, UUID correlationId);
}
