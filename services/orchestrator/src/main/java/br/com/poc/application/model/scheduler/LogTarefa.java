package br.com.poc.application.model.scheduler;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Registro de log/evento associado a uma `Tarefa`.
 *
 * Usado para armazenar histórico de execução, códigos de status e mensagens
 * relacionadas à execução/alteração da tarefa.
 */
@Data
public class LogTarefa {
    private Long id;
    private LocalDateTime dataCriacao;
    private Integer codigo;
    private String texto;
}
