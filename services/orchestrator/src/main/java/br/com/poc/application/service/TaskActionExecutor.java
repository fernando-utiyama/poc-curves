package br.com.poc.application.service;

import br.com.poc.application.exception.InvalidInputException;
import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.port.out.TaskActionPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
/**
 * Executor de ações de tarefa. Centraliza a resolução da `action` declarada na tarefa
 * para a implementação apropriada de `TaskActionPort`.
 *
 * Como funciona:
 * - No construtor, recebe todas as implementações de `TaskActionPort` e cria um mapa
 *   normalizado de nomes de ação para a estratégia correspondente.
 * - `supports` verifica se uma action é suportada.
 * - `execute` valida a tarefa, resolve a estratégia e delega a execução.
 *
 * Observações:
 * - A normalização de nomes remove espaços e transforma em lowercase para comparações.
 */
public class TaskActionExecutor {

    private static final String ORIGIN = "TASK_ACTION";

    private final Map<String, TaskActionPort> actionsByName;

    public TaskActionExecutor(List<TaskActionPort> actions) {
        this.actionsByName = actions.stream()
            .flatMap(action -> action.getSupportedActionNames().stream().map(name -> Map.entry(normalize(name), action)))
            .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(), entry -> entry.getValue(), (current, ignored) -> current));
    }

    public boolean supports(String actionName) {
        return actionName != null && actionsByName.containsKey(normalize(actionName));
    }

    public void execute(Tarefa tarefa) {
        if (tarefa == null || tarefa.getAction() == null || tarefa.getAction().isBlank()) {
            throw new InvalidInputException(ORIGIN, "execute", "Action da tarefa nao informada");
        }

        TaskActionPort action = actionsByName.get(normalize(tarefa.getAction()));
        if (action == null) {
            throw new InvalidInputException(ORIGIN, "execute", "Action nao suportada: " + tarefa.getAction());
        }

        action.execute(tarefa);
    }

    private String normalize(String actionName) { return actionName.trim().toLowerCase(Locale.ROOT); }
}
