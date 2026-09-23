package br.com.poc.application.model.scheduler;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de domínio que representa uma tarefa agendada/executável no sistema.
 *
 * Campos principais:
 * - `id`: identificador único.
 * - `nome`, `descricao`: metadados para identificação e entendimento humano.
 * - `action`: nome da ação a ser executada (resolvida por `TaskActionExecutor`).
 * - `regraCron` / `regraIntervalo`: controle de agendamento.
 * - `status`: estado atual da tarefa (ex: AGENDADA, EXECUTANDO, EXECUTADA, ERRO).
 * - `parametros`: parâmetros específicos para execução da action.
 * - `logs`: histórico de execução e eventos relacionados à tarefa.
 */
@Data
public class Tarefa {
    private Long id;
    private String nome;
    private String descricao;
    private String action;
    private String regraCron;
    private String regraIntervalo;
    private String status;
    private List<ParametroTarefa> parametros = new ArrayList<>();
    private List<LogTarefa> logs = new ArrayList<>();
    // adicao na domain esses parametros
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;
    private Instant ultimaExecucao;
    private Instant proximaExecucao;
    private String ultimaMensagem;
}
