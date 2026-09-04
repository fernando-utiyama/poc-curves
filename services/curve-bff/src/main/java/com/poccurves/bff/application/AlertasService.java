package com.poccurves.bff.application;

import com.poccurves.bff.dto.BffDtos.*;

import java.time.Duration;
import java.time.Instant;

public class AlertasService {

    private final CurveOrchestratorPort orchestratorClient;
    private final PainelDoDiaService painelDoDiaService;

    public AlertasService(CurveOrchestratorPort orchestratorClient, PainelDoDiaService painelDoDiaService) {
        this.orchestratorClient = orchestratorClient;
        this.painelDoDiaService = painelDoDiaService;
    }

    public AlertasSumarioResponse obterAlertasSumario() {
        // 1. Consulta pendências DLQ do orquestrador
        PendenciasSumarioResponse dlq = orchestratorClient.getPendenciasSumario();

        int gruposAbertos = dlq.totalGruposAbertos();
        int totalMensagens = dlq.totalMensagensPendentes();

        // Calcula idade da pendência mais antiga
        Integer idadeMaisAntigaMinutos = null;
        String severidade = "BAIXA";

        if (dlq.grupos() != null && !dlq.grupos().isEmpty()) {
            Instant maisAntiga = dlq.grupos().stream()
                    .filter(g -> g.primeiroErroEm() != null)
                    .map(GrupoPendenciaDTO::primeiroErroEm)
                    .min(Instant::compareTo)
                    .orElse(null);

            if (maisAntiga != null) {
                idadeMaisAntigaMinutos = (int) Duration.between(maisAntiga, Instant.now()).toMinutes();
                if (idadeMaisAntigaMinutos > 240) { // > 4 horas
                    severidade = "CRITICA";
                } else if (idadeMaisAntigaMinutos > 60) { // > 1 hora
                    severidade = "ALTA";
                } else if (idadeMaisAntigaMinutos > 15) {
                    severidade = "MEDIA";
                }
            }
        }

        // 2. Consulta curvas em risco no painel do dia
        PainelDoDiaResponse painel = painelDoDiaService.obterPainelDoDia(null);
        int emRisco = (int) painel.curvas().stream().filter(c -> "EM_RISCO".equalsIgnoreCase(c.status())).count();
        int atrasadas = (int) painel.curvas().stream().filter(c -> "ATRASADA".equalsIgnoreCase(c.status())).count();

        Integer menorMargem = painel.curvas().stream()
                .filter(c -> c.tempoRestanteMinutos() != null)
                .mapToInt(ItemPainelDoDiaDTO::tempoRestanteMinutos)
                .min()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);

        return new AlertasSumarioResponse(
                gruposAbertos,
                totalMensagens,
                idadeMaisAntigaMinutos,
                severidade,
                emRisco,
                atrasadas,
                menorMargem
        );
    }
}
