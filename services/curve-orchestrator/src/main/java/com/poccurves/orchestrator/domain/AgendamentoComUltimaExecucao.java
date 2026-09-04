package com.poccurves.orchestrator.domain;

import java.time.LocalDateTime;

public record AgendamentoComUltimaExecucao(
        Agendamento agendamento,
        String ultimaExecucaoEstado,
        LocalDateTime ultimaExecucaoIniciadoEm,
        LocalDateTime ultimaExecucaoFinalizadoEm,
        String ultimaExecucaoMotivoSemDado
) {}
