package br.com.poc.application.model.scheduler;

import java.util.Locale;

/**
 * Enum que representa os estados possíveis de uma `Tarefa`.
 *
 * Valores típicos:
 * - `AGENDADA`: tarefa agendada para execução futura
 * - `EXECUTANDO`: tarefa em execução no momento
 * - `FINALIZADA`: execução concluída com sucesso
 * - `CANCELADA`: tarefa cancelada
 * - `ERRO`: tarefa finalizada com erro
 */
public enum TarefaStatus {
    PRONTA,
    AGENDADA,
    EXECUTANDO,
    FINALIZADA,
    ERRO,
    CANCELADA,
    DESABILITADA,
    REMOVIDA

    ;

    public static TarefaStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Status da tarefa nao informado");
        }

        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedValue) {
            case "ATIVO" -> PRONTA;
            case "INATIVO", "DESATIVADA" -> DESABILITADA;
            case "EXECUTADA" -> FINALIZADA;
            default -> valueOf(normalizedValue);
        };
    }
}
