package br.com.poc.adapter.out.action;

import br.com.poc.application.model.scheduler.Tarefa;
import br.com.poc.application.port.out.TaskActionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter simples que escreve uma mensagem no console/log como ação de tarefa.
 *
 * Convenção de parâmetros:
 * - `message`: texto a ser logado/imprimido. Se ausente, será usado um valor padrão.
 */
@Component
public class ConsoleTaskActionAdapter implements TaskActionPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleTaskActionAdapter.class);

    @Override
    public String getActionName() { return "console"; }

    @Override
    public List<String> getSupportedActionNames() { return List.of("console", "consoleaction"); }

    @Override
    public void execute(Tarefa tarefa) {
        String message = tarefa.getParametros().stream()
            .filter(parametro -> "message".equalsIgnoreCase(parametro.getNome()))
            .map(parametro -> parametro.getValor())
            .findFirst()
            .orElse("Executando tarefa " + tarefa.getNome());

        System.out.println("Executing ConsoleAction with parameters: message = " + message);
        LOGGER.info("ConsoleAction [{}]: {}", tarefa.getNome(), message);
    }
}
