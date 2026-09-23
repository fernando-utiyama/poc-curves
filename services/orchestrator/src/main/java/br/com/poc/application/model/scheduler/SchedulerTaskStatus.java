package br.com.poc.application.model.scheduler;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SchedulerTaskStatus {

    private Long tarefaId;
    private String status;
    private Boolean teveErro;
    private String ultimaMensagem;
    private LocalDateTime ultimaExecucao;
    private LocalDateTime proximaExecucao;
}
