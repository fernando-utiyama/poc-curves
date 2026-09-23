package br.com.poc.application.port.out;

import br.com.poc.application.model.scheduler.Tarefa;

import java.util.List;

/**
 * Contrato que implementações de ações de tarefa devem cumprir.
 *
 * Cada implementação representa uma estratégia executável (ex: `http`, `console`).
 */
public interface TaskActionPort {

    String getActionName();

    default List<String> getSupportedActionNames() { return List.of(getActionName()); }

    void execute(Tarefa tarefa);
}
