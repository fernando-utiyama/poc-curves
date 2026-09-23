package br.com.poc.application.port.in;

import br.com.poc.application.model.scheduler.Tarefa;

import java.util.List;

/**
 * <summary>
 * Porta de entrada responsável pelas operações de tarefa.
 * </summary>
 */
public interface TarefaUseCase {

    /**
     * <summary>
     * Cria e agenda uma nova tarefa.
     * </summary>
     *
     * @param tarefa modelo de domínio
     * @return tarefa criada
     */
    Tarefa criarTarefa(Tarefa tarefa);

    List<Tarefa> listarTarefas();

    Tarefa buscarTarefa(Long id);

    Tarefa atualizarTarefa(Long id, Tarefa tarefa);

    Tarefa executarTarefa(Long id);

    Tarefa executarTarefa(String nome);

    void deletarTarefa(Long id);
}
