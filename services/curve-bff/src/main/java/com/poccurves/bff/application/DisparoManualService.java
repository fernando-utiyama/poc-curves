package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

public class DisparoManualService {

    private final CurveOrchestratorPort orchestratorClient;

    public DisparoManualService(CurveOrchestratorPort orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    public DisparoManualResponse disparoManual(DisparoManualRequest request) {
        // Valida se a data informada é dia útil de pregão (fim de semana = não é pregão)
        LocalDate data = request.dataReferencia() != null ? request.dataReferencia() : LocalDate.now();
        if (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return new DisparoManualResponse(
                    UUID.randomUUID().toString(),
                    "RECUSADO_NAO_PREGAO",
                    "A data " + data + " não é dia útil de pregão na B3.",
                    Collections.emptyList()
            );
        }

        // Propaga a falha de verdade se o orquestrador não responder — corrigido
        // na auditoria desta sessão: a versão anterior engolia qualquer exceção
        // (rede, 404, timeout) e fabricava uma resposta "DISPARADO" falsa, com
        // UUID e progresso inventados. Uma plataforma de curvas financeiras não
        // pode informar sucesso para um disparo que nunca aconteceu.
        // GlobalExceptionHandler já mapeia HttpClientErrorException/
        // HttpServerErrorException/Exception genérica para uma resposta de erro
        // honesta (4xx/5xx real), então não precisa de catch aqui.
        return orchestratorClient.disparoManual(request);
    }

    public BackfillResponse backfill(BackfillRequest request) {
        return orchestratorClient.backfill(request);
    }
}
