package br.com.poc.application.port.out;

import br.com.poc.application.model.scheduler.Tarefa;

import java.util.List;
import java.util.Optional;

/**
 * Porta de saída responsável pelas operações de persistência de `Tarefa`.
 *
 * Implementações concretas podem utilizar JPA, repositórios em memória, ou
 * outras tecnologias de persistência. Esta interface mantém a camada de
 * aplicação desacoplada da implementação de armazenamento.
 */
public interface TaskRepositoryPort {
    Tarefa save(Tarefa tarefa);
    List<Tarefa> findAllTarefas();
    List<Tarefa> findPersistedScheduledTarefas();
    Optional<Tarefa> findTarefaById(Long id);
    Optional<Tarefa> findTarefaByNome(String nome);
    void deleteTarefaById(Long id);
    Optional<Tarefa> buscarPorId(Long id);
}
