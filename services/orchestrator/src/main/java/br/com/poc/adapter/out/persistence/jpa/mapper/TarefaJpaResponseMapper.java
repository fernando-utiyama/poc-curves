package br.com.poc.adapter.out.persistence.jpa.mapper;

import br.com.poc.adapter.in.api.rest.dto.scheduler.LogRecuperarStatusResponseDto;
import br.com.poc.adapter.in.api.rest.dto.scheduler.RecuperarStatusResponseDto;
import br.com.poc.application.model.scheduler.LogTarefa;
import br.com.poc.application.model.scheduler.Tarefa;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class TarefaJpaResponseMapper {
    public RecuperarStatusResponseDto toStatusResponse(Tarefa tarefa, List<LogTarefa> logs) {
        if (tarefa == null) {
            return null;
        }

        List<LogRecuperarStatusResponseDto> logResponses = logs == null
            ? List.of()
            : logs.stream()
            .map(this::toLogResponse)
            .toList();

        List<LogTarefa> logsOrdenados = logs == null
            ? List.of()
            : logs.stream()
            .filter(Objects::nonNull)
            .filter(log -> log.getDataCriacao() != null)
            .sorted(Comparator.comparing(LogTarefa::getDataCriacao))
            .toList();

        LogTarefa primeiroLog = logsOrdenados.isEmpty() ? null : logsOrdenados.getFirst();
        LogTarefa ultimoLog = logsOrdenados.isEmpty() ? null : logsOrdenados.getLast();
        Instant ultimaExecucao = tarefa.getUltimaExecucao() != null
            ? tarefa.getUltimaExecucao()
            : obterUltimaExecucao(logsOrdenados);

        return new RecuperarStatusResponseDto(
            tarefa.getId(),
            tarefa.getNome(),
            tarefa.getStatus(),
            tarefa.getDataCriacao() != null ? tarefa.getDataCriacao() : dataDo(primeiroLog),
            tarefa.getDataAtualizacao() != null ? tarefa.getDataAtualizacao() : dataDo(ultimoLog),
            ultimaExecucao,
            tarefa.getProximaExecucao() != null
                ? tarefa.getProximaExecucao()
                : calcularProximaExecucao(tarefa, ultimaExecucao),
            tarefa.getUltimaMensagem() != null ? tarefa.getUltimaMensagem() : textoDo(ultimoLog),
            logResponses
        );
    }

    private Instant obterUltimaExecucao(List<LogTarefa> logs) {
        return logs.stream()
            .filter(this::concluiuExecucao)
            .map(LogTarefa::getDataCriacao)
            .max(Comparator.naturalOrder())
            .map(data -> data.atZone(ZoneId.systemDefault()).toInstant())
            .orElse(null);
    }

    private boolean concluiuExecucao(LogTarefa log) {
        return Integer.valueOf(500).equals(log.getCodigo())
            || (Integer.valueOf(200).equals(log.getCodigo())
            && "Tarefa executada com sucesso".equals(log.getTexto()));
    }

    private Instant calcularProximaExecucao(Tarefa tarefa, Instant ultimaExecucao) {
        if (ultimaExecucao == null) {
            return null;
        }

        LocalDateTime referencia = LocalDateTime.ofInstant(ultimaExecucao, ZoneId.systemDefault());
        if (tarefa.getRegraCron() != null && !tarefa.getRegraCron().isBlank()) {
            try {
                LocalDateTime proxima = CronExpression.parse(tarefa.getRegraCron()).next(referencia);
                return proxima == null ? null : proxima.atZone(ZoneId.systemDefault()).toInstant();
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        if (tarefa.getRegraIntervalo() != null && !tarefa.getRegraIntervalo().isBlank()) {
            try {
                return ultimaExecucao.plus(Duration.parse(tarefa.getRegraIntervalo()));
            } catch (DateTimeParseException ex) {
                return null;
            }
        }

        return null;
    }

    private LocalDateTime dataDo(LogTarefa log) { return log == null ? null : log.getDataCriacao(); }

    private String textoDo(LogTarefa log) { return log == null ? null : log.getTexto(); }

    public LogRecuperarStatusResponseDto toLogResponse(LogTarefa log) {
        if (log == null) {
            return null;
        }

        return new LogRecuperarStatusResponseDto(
            log.getDataCriacao(),
            log.getTexto()
        );
    }
}
