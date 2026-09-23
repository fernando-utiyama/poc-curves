package br.com.poc.application.port.in;

import br.com.poc.application.model.scheduler.Tarefa;

import java.util.List;

public interface TarefaCrudUseCase {

    Tarefa criarTarefa(Tarefa tarefa);

    List<Tarefa> listarTarefas();

    Tarefa buscarTarefa(Long id);

    Tarefa atualizarParcialmente(Long id, Tarefa tarefaPatch);

    void desabilitarTarefa(Long id);

    void habilitarTarefa(Long id);

    void removerTarefa(Long id);
}
