package com.poccurves.orchestrator.application.model;

import java.time.LocalDateTime;

public record AgendamentoComUltimaExecucao(
        Agendamento agendamento,
        String ultimaExecucaoEstado,
        LocalDateTime ultimaExecucaoIniciadoEm,
        LocalDateTime ultimaExecucaoFinalizadoEm,
        String ultimaExecucaoMotivoSemDado
) {}
